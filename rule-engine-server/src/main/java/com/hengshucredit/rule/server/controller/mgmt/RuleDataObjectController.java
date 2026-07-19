package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObjectFieldOption;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleDataObjectService;
import com.hengshucredit.rule.server.service.SchemaSyncService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
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

    @PostMapping("/import/java")
    public R<Map<String, Object>> importJava(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String javaSource = body.get("javaSource");
        Map<String, Object> result = dataObjectService.importFromJava(projectId, scope, javaSource, objectType);
        trySyncSchema();
        return R.ok(result);
    }

    @PostMapping("/import/java-file")
    public R<Map<String, Object>> importJavaFile(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "PROJECT") String scope,
            @RequestParam(defaultValue = "INPUT") String objectType,
            @RequestParam("file") MultipartFile file) throws Exception {
        String javaSource = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> result = dataObjectService.importFromJava(projectId, scope, javaSource, objectType);
        trySyncSchema();
        return R.ok(result);
    }

    @PostMapping("/import/json")
    public R<Map<String, Object>> importJson(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String objectCode = body.get("objectCode");
        String jsonContent = body.get("jsonContent");
        Map<String, Object> result = dataObjectService.importFromJson(projectId, scope, jsonContent, objectCode, objectType);
        trySyncSchema();
        return R.ok(result);
    }

    /** 从建表 DDL（CREATE TABLE）导入数据对象与字段，COMMENT 作为变量名称 */
    @PostMapping("/import/ddl")
    public R<Map<String, Object>> importDdl(@RequestBody Map<String, String> body) {
        String pidStr = body.get("projectId");
        Long projectId = pidStr != null && !pidStr.isEmpty() ? Long.valueOf(pidStr) : null;
        String scope = body.getOrDefault("scope", "PROJECT");
        String objectType = body.getOrDefault("objectType", "INPUT");
        String ddlSource = body.get("ddlSource");
        Map<String, Object> result = dataObjectService.importFromDdl(projectId, scope, ddlSource, objectType);
        trySyncSchema();
        return R.ok(result);
    }

    @PostMapping
    public R<Long> createOrUpdate(@RequestBody RuleDataObject obj) {
        if (obj.getId() != null) {
            dataObjectService.updateById(obj);
            trySyncSchema();
            return R.ok(obj.getId());
        } else {
            dataObjectService.save(obj);
            trySyncSchema();
            return R.ok(obj.getId());
        }
    }

    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @RequestBody RuleDataObject obj) {
        obj.setId(id);
        dataObjectService.updateById(obj);
        trySyncSchema();
        return R.ok();
    }

    /** 将项目级数据对象及其字段转为全局。 */
    @PostMapping("/toGlobal/{id:\\d+}")
    public R<Void> toGlobal(@PathVariable Long id) {
        try {
            dataObjectService.toGlobal(id);
            trySyncSchema();
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/project/{projectId:\\d+}")
    public R<List<RuleDataObject>> listByProject(@PathVariable Long projectId) {
        return R.ok(dataObjectService.listByProject(projectId));
    }

    @GetMapping("/page")
    public R<IPage<RuleDataObject>> page(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String objectCode) {
        return R.ok(dataObjectService.pageList(pageNum, pageSize, scope, projectId, projectCode, projectName, sourceType, objectCode));
    }

    /**
     * 获取所有项目的数据对象树（未选项目时使用）
     */
    @GetMapping("/tree")
    public R<Map<String, Object>> treeAll(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String objectCode) {
        return R.ok(dataObjectService.getVariableTreeAll(scope, projectId, projectCode, projectName, sourceType, objectCode));
    }

    @GetMapping("/{id:\\d+}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> data = dataObjectService.getObjectWithVariables(id);
        return data != null ? R.ok(data) : R.fail("数据对象不存在");
    }

    @GetMapping("/tree/{projectId:\\d+}")
    public R<Map<String, Object>> tree(@PathVariable Long projectId) {
        return R.ok(dataObjectService.getVariableTree(projectId));
    }

    @PutMapping("/{id:\\d+}/type")
    public R<Void> updateType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dataObjectService.updateObjectType(id, body.get("objectType"));
        return R.ok();
    }

    /** 更新数据对象的脚本引用名 */
    @PutMapping("/{id:\\d+}/script-name")
    public R<Void> updateScriptName(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dataObjectService.updateScriptName(id, body.get("scriptName"));
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        dataObjectService.deleteWithVariables(id);
        trySyncSchema();
        return R.ok();
    }

    /** 在数据对象下新增字段（写入 rule_data_object_field） */
    @PostMapping("/{objectId:\\d+}/field")
    public R<RuleDataObjectField> createField(@PathVariable Long objectId, @RequestBody RuleDataObjectField field) {
        return R.ok(dataObjectService.createObjectField(objectId, field));
    }

    /** 更新对象字段 */
    @PutMapping("/field")
    public R<Void> updateField(@RequestBody RuleDataObjectField field) {
        dataObjectService.updateObjectField(field);
        trySyncSchema();
        return R.ok();
    }

    /** 删除对象字段及其枚举选项 */
    @DeleteMapping("/field/{fieldId:\\d+}")
    public R<Void> deleteField(@PathVariable Long fieldId) {
        dataObjectService.deleteObjectField(fieldId);
        trySyncSchema();
        return R.ok();
    }

    @GetMapping("/field/{fieldId:\\d+}/options")
    public R<List<RuleDataObjectFieldOption>> getFieldOptions(@PathVariable Long fieldId) {
        return R.ok(dataObjectService.getFieldOptions(fieldId));
    }

    @PostMapping("/field/{fieldId:\\d+}/options")
    public R<Void> saveFieldOptions(@PathVariable Long fieldId, @RequestBody List<RuleDataObjectFieldOption> options) {
        dataObjectService.saveFieldOptions(fieldId, options);
        trySyncSchema();
        return R.ok();
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {}
    }
}
