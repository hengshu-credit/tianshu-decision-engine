package com.bjjw.rule.server.controller;

import com.bjjw.rule.model.entity.RuleModel;
import com.bjjw.rule.model.entity.RuleModelInputField;
import com.bjjw.rule.model.entity.RuleModelOutputField;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleModelService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/model")
public class RuleModelController {

    @Resource
    private RuleModelService modelService;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /**
     * 检查模型编码是否与现有模型冲突
     * @param modelCode 模型编码
     * @param scope GLOBAL / PROJECT
     * @param projectId 项目ID（仅 PROJECT 时需要）
     * @param excludeId 排除的模型ID（编辑时传自己的ID，跳过自身比对）
     */
    @GetMapping("/checkCode")
    public R<Boolean> checkCode(
            @RequestParam String modelCode,
            @RequestParam String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long excludeId) {
        boolean exists = modelService.existsModelCodeConflict(modelCode, scope, projectId, excludeId);
        return R.ok(exists);
    }

    /**
     * 上传并解析模型文件
     */
    @PostMapping("/upload")
    public R<RuleModel> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String scope,
            @RequestParam String modelCode,
            @RequestParam String modelName,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String changeLog,
            @RequestParam(required = false) String testParams) {
        try {
            RuleModel model = modelService.uploadAndParse(
                    file, projectId, scope, modelCode, modelName, modelType, description, changeLog, testParams);
            return R.ok(model);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 分页查询模型列表
     */
    @GetMapping("/list")
    public R<IPage<RuleModel>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String modelFormat,
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName) {
        return R.ok(modelService.pageList(pageNum, pageSize, projectId, scope,
                modelType, modelFormat, modelCode, modelName, projectCode, projectName));
    }

    /**
     * 获取模型详情（含字段信息）
     */
    @GetMapping("/{id}")
    public R<RuleModel> get(@PathVariable Long id) {
        RuleModel model = modelService.getDetail(id);
        return model != null ? R.ok(model) : R.fail("模型不存在");
    }

    /**
     * 更新模型元信息（不包括文件内容）
     */
    @PutMapping
    public R<Void> update(@RequestBody RuleModel model) {
        try {
            modelService.update(model);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return R.ok();
    }

    /**
     * 发布模型
     */
    @PostMapping("/publish/{id}")
    public R<Void> publish(@PathVariable Long id,
            @RequestParam(required = false) String changeLog) {
        try {
            modelService.publish(id, changeLog, null);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 下线模型
     */
    @PostMapping("/unpublish/{id}")
    public R<Void> unpublish(@PathVariable Long id) {
        try {
            modelService.unpublish(id);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 查询项目下所有模型（非分页，设计器使用）
     */
    @GetMapping("/project/{projectId}/all")
    public R<List<RuleModel>> listAllByProject(@PathVariable Long projectId) {
        return R.ok(modelService.listByProject(projectId));
    }

    /**
     * 执行模型测试
     * @param id 模型ID
     * @param params 输入参数（Map<String, Object>）
     */
    @PostMapping("/execute/{id}")
    public R<Map<String, Object>> execute(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        try {
            Map<String, Object> result = modelService.execute(id, params);
            return R.ok(result);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.toString();
            return R.fail("模型执行失败: " + msg);
        }
    }

    /**
     * 保存模型的测试参数（JSON）
     * @param id 模型ID
     * @param testParams JSON字符串的测试参数
     */
    @PostMapping("/testParams/{id}")
    public R<Void> saveTestParams(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            modelService.saveTestParams(id, body.get("testParams"));
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取模型的测试参数（JSON）
     * @param id 模型ID
     */
    @GetMapping("/testParams/{id}")
    public R<String> getTestParams(@PathVariable Long id) {
        String params = modelService.getTestParams(id);
        return R.ok(params);
    }

    /**
     * 更新模型输入字段（关联变量映射）
     */
    @PutMapping("/inputField/{id}")
    public R<Void> updateInputField(@PathVariable Long id, @RequestBody RuleModelInputField field) {
        try {
            modelService.updateInputField(id, field);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 更新模型输出字段（关联变量映射）
     */
    @PutMapping("/outputField/{id}")
    public R<Void> updateOutputField(@PathVariable Long id, @RequestBody RuleModelOutputField field) {
        try {
            modelService.updateOutputField(id, field);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 将项目级模型转为全局模型
     * @param id 模型ID
     * @param newModelCode 新的全局编码（用户重新填写）
     */
    @PostMapping("/toGlobal/{id:\\d+}")
    public R<Void> toGlobal(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String newModelCode = body.get("modelCode");
            modelService.toGlobal(id, newModelCode);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}