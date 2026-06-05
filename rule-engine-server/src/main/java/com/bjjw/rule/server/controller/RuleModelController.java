package com.bjjw.rule.server.controller;

import com.bjjw.rule.model.entity.RuleModel;
import com.bjjw.rule.model.entity.RuleModelRef;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleModelService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

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
            @RequestParam(required = false) String changeLog) {
        try {
            RuleModel model = modelService.uploadAndParse(
                    file, projectId, scope, modelCode, modelName, modelType, description, changeLog);
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
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
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
     * 添加全局模型到项目
     */
    @PostMapping("/ref")
    public R<Void> addRef(@RequestBody RuleModelRef ref) {
        try {
            modelService.addModelRef(ref.getModelId(), ref.getProjectId());
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 从项目移除全局模型
     */
    @DeleteMapping("/ref")
    public R<Void> removeRef(@RequestBody RuleModelRef ref) {
        modelService.removeModelRef(ref.getModelId(), ref.getProjectId());
        return R.ok();
    }
}