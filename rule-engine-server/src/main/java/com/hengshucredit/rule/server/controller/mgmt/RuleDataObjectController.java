package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.server.service.DataObjectFieldReferenceValidator;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObjectFieldOption;
import com.hengshucredit.rule.server.governance.GovernanceBatchImportService;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleDataObjectService;
import com.hengshucredit.rule.server.service.SchemaSyncService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/dataobject")
public class RuleDataObjectController {

    @Resource
    private RuleDataObjectService dataObjectService;

    @Resource
    private SchemaSyncService schemaSyncService;

    @Resource
    private GovernanceBatchImportService governanceBatchImportService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @Resource
    private DataObjectFieldReferenceValidator fieldReferenceValidator;

    @PostMapping("/{objectId:\\d+}/field/validate-reference")
    @RequirePermission("field:edit")
    public ApiResponse<List<RuleValidationIssue>> validateFieldReference(
            @PathVariable Long objectId, @RequestBody RuleDataObjectField field) {
        return ApiResponse.ok(fieldReferenceValidator.validateCandidate(objectId, field));
    }

    @PostMapping("/import/java")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importJava(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String javaSource = body.get("javaSource");
        Map<String, Object> result =
                governanceBatchImportService.importDataObjectsFromJava(
                        projectId, scope, javaSource, objectType,
                        operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    @PostMapping("/import/java-file")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importJavaFile(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "PROJECT") String scope,
            @RequestParam(defaultValue = "INPUT") String objectType,
            @RequestParam("file") MultipartFile file) throws Exception {
        String javaSource = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> result =
                governanceBatchImportService.importDataObjectsFromJava(
                        projectId, scope, javaSource, objectType,
                        operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    @PostMapping("/import/json")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importJson(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String objectCode = body.get("objectCode");
        String jsonContent = body.get("jsonContent");
        Map<String, Object> result =
                governanceBatchImportService.importDataObjectsFromJson(
                        projectId, scope, jsonContent, objectCode,
                        objectType, operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    /** 从建表 DDL 解析数据对象与字段并生成生命周期审批草稿。 */
    @PostMapping("/import/ddl")
    @RequirePermission("approval:submit")
    public ApiResponse<Map<String, Object>> importDdl(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String ddlSource = body.get("ddlSource");
        Map<String, Object> result =
                governanceBatchImportService.importDataObjectsFromDdl(
                        projectId, scope, ddlSource, objectType,
                        operatorResolver.resolve());
        return ApiResponse.ok(result);
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<Long> createOrUpdate(@RequestBody RuleDataObject obj) {
        if (obj.getId() != null) {
            dataObjectService.updateById(obj);
            trySyncSchema();
            return ApiResponse.ok(obj.getId());
        } else {
            dataObjectService.save(obj);
            trySyncSchema();
            return ApiResponse.ok(obj.getId());
        }
    }

    @PutMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody RuleDataObject obj) {
        obj.setId(id);
        dataObjectService.updateById(obj);
        trySyncSchema();
        return ApiResponse.ok();
    }

    /** 将项目级数据对象及其字段转为全局。 */
    @PostMapping("/toGlobal/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> toGlobal(@PathVariable Long id) {
        try {
            dataObjectService.toGlobal(id);
            trySyncSchema();
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @GetMapping("/project/{projectId:\\d+}")
    public ApiResponse<List<RuleDataObject>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(dataObjectService.listByProject(projectId));
    }

    @GetMapping("/page")
    public ApiResponse<IPage<RuleDataObject>> page(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String objectCode) {
        return ApiResponse.ok(dataObjectService.pageList(pageNum, pageSize, scope, projectId, projectCode, projectName, sourceType, objectCode));
    }

    /**
     * 获取所有项目的数据对象树（未选项目时使用）
     */
    @GetMapping("/tree")
    public ApiResponse<Map<String, Object>> treeAll(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String objectCode) {
        return ApiResponse.ok(dataObjectService.getVariableTreeAll(scope, projectId, projectCode, projectName, sourceType, objectCode));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> data = dataObjectService.getObjectWithVariables(id);
        return data != null ? ApiResponse.ok(data) : ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "数据对象不存在");
    }

    @GetMapping("/tree/{projectId:\\d+}")
    public ApiResponse<Map<String, Object>> tree(@PathVariable Long projectId) {
        return ApiResponse.ok(dataObjectService.getVariableTree(projectId));
    }

    @PutMapping("/{id:\\d+}/type")
    @GovernedProjectionMutation
    public ApiResponse<Void> updateType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dataObjectService.updateObjectType(id, body.get("objectType"));
        return ApiResponse.ok();
    }

    /** 更新数据对象的脚本引用名 */
    @PutMapping("/{id:\\d+}/script-name")
    @GovernedProjectionMutation
    public ApiResponse<Void> updateScriptName(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dataObjectService.updateScriptName(id, body.get("scriptName"));
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataObjectService.deleteWithVariables(id);
        trySyncSchema();
        return ApiResponse.ok();
    }

    /** 在数据对象下新增字段（写入 rule_data_object_field） */
    @PostMapping("/{objectId:\\d+}/field")
    @GovernedProjectionMutation
    public ApiResponse<RuleDataObjectField> createField(@PathVariable Long objectId, @RequestBody RuleDataObjectField field) {
        return ApiResponse.ok(dataObjectService.createObjectField(objectId, field));
    }

    /** 更新对象字段 */
    @PutMapping("/field")
    @GovernedProjectionMutation
    public ApiResponse<Void> updateField(@RequestBody RuleDataObjectField field) {
        dataObjectService.updateObjectField(field);
        trySyncSchema();
        return ApiResponse.ok();
    }

    /** 删除对象字段及其枚举选项 */
    @DeleteMapping("/field/{fieldId:\\d+}")
    @GovernedProjectionMutation
    public ApiResponse<Void> deleteField(@PathVariable Long fieldId) {
        dataObjectService.deleteObjectField(fieldId);
        trySyncSchema();
        return ApiResponse.ok();
    }

    @GetMapping("/field/{fieldId:\\d+}/options")
    public ApiResponse<List<RuleDataObjectFieldOption>> getFieldOptions(@PathVariable Long fieldId) {
        return ApiResponse.ok(dataObjectService.getFieldOptions(fieldId));
    }

    @PostMapping("/field/{fieldId:\\d+}/options")
    @GovernedProjectionMutation
    public ApiResponse<Void> saveFieldOptions(@PathVariable Long fieldId, @RequestBody List<RuleDataObjectFieldOption> options) {
        dataObjectService.saveFieldOptions(fieldId, options);
        trySyncSchema();
        return ApiResponse.ok();
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {}
    }
}
