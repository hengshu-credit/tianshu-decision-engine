package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjjw.rule.model.entity.*;
import com.bjjw.rule.server.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleModelService {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";

    @Resource
    private RuleModelMapper modelMapper;
    @Resource
    private RuleModelInputFieldMapper inputFieldMapper;
    @Resource
    private RuleModelOutputFieldMapper outputFieldMapper;
    @Resource
    private RuleModelVersionMapper versionMapper;
    @Resource
    private RuleModelRefMapper refMapper;
    @Resource
    private RuleProjectMapper projectMapper;

    /**
     * 填充模型列表的项目名称
     */
    private void fillProjectName(List<RuleModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> projectIds = list.stream()
                .filter(m -> m.getProjectId() != null && m.getProjectId() > 0)
                .map(RuleModel::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        if (projectIds.isEmpty()) return;
        Map<Long, String> nameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (a, b) -> a));
        list.forEach(m -> {
            if (m.getProjectId() != null && m.getProjectId() > 0) {
                m.setProjectName(nameMap.get(m.getProjectId()));
            }
        });
    }

    /**
     * 填充模型的项目编码
     */
    private void fillProjectCode(RuleModel model) {
        if (model == null || model.getProjectId() == null || model.getProjectId() <= 0) return;
        RuleProject project = projectMapper.selectById(model.getProjectId());
        if (project != null) {
            model.setProjectCode(project.getProjectCode());
            model.setProjectName(project.getProjectName());
        }
    }

    /**
     * 上传并解析模型文件
     */
    public RuleModel uploadAndParse(MultipartFile file, Long projectId, String scope,
            String modelCode, String modelName, String modelType, String description,
            String changeLog) {
        try {
            // 1. 读取文件内容（Base64）
            String modelContent = java.util.Base64.getEncoder().encodeToString(file.getBytes());

            // 2. 根据文件扩展名检测格式
            String modelFormat = detectFormat(file.getOriginalFilename());

            // 3. 解析模型（目前支持 PMML 自动解析，其他格式需手动配置字段）
            Map<String, Object> modelConfig = null;
            List<RuleModelInputField> inputFields = null;
            List<RuleModelOutputField> outputFields = null;

            if ("PMML".equals(modelFormat)) {
                String rawContent = new String(file.getBytes(), "UTF-8");
                PmmlParseResult result = parsePmml(rawContent);
                modelConfig = result.getModelConfig();
                inputFields = result.getInputFields();
                outputFields = result.getOutputFields();
                if (modelType == null || modelType.isEmpty()) {
                    modelType = result.getModelType();
                }
            }

            // 4. 填充项目信息
            String projectCode = null;
            String projectName = null;
            if (projectId != null && projectId > 0) {
                RuleProject project = projectMapper.selectById(projectId);
                if (project != null) {
                    projectCode = project.getProjectCode();
                    projectName = project.getProjectName();
                }
            }

            // 5. 保存模型主表
            RuleModel model = new RuleModel();
            model.setProjectId(projectId);
            model.setProjectCode(projectCode);
            model.setProjectName(projectName);
            model.setScope(scope != null ? scope : SCOPE_PROJECT);
            model.setModelCode(modelCode);
            model.setModelName(modelName);
            model.setModelType(modelType != null ? modelType : "ML");
            model.setModelFormat(modelFormat);
            model.setDescription(description);
            model.setModelContent(modelContent);
            model.setModelFileName(file.getOriginalFilename());
            model.setModelFileSize(file.getSize());
            if (modelConfig != null) {
                model.setModelConfig(com.alibaba.fastjson.JSON.toJSONString(modelConfig));
            }
            model.setInputFieldCount(inputFields != null ? inputFields.size() : 0);
            model.setOutputFieldCount(outputFields != null ? outputFields.size() : 0);
            model.setCurrentVersion(1);
            model.setStatus(1);
            modelMapper.insert(model);

            // 6. 保存输入字段
            if (inputFields != null) {
                for (int i = 0; i < inputFields.size(); i++) {
                    RuleModelInputField inputField = inputFields.get(i);
                    inputField.setModelId(model.getId());
                    inputField.setSortOrder(i);
                    inputField.setStatus(1);
                    inputField.setCreateTime(LocalDateTime.now());
                    inputFieldMapper.insert(inputField);
                }
            }

            // 7. 保存输出字段
            if (outputFields != null) {
                for (int i = 0; i < outputFields.size(); i++) {
                    RuleModelOutputField outputField = outputFields.get(i);
                    outputField.setModelId(model.getId());
                    outputField.setSortOrder(i);
                    outputField.setCreateTime(LocalDateTime.now());
                    outputFieldMapper.insert(outputField);
                }
            }

            // 8. 保存版本快照
            saveVersionSnapshot(model.getId(), 1, modelContent, model.getModelConfig(), changeLog, null);

            return model;
        } catch (IOException e) {
            throw new RuntimeException("读取模型文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 手动创建模型（不依赖文件上传）
     */
    public RuleModel create(RuleModel model) {
        if (model.getScope() == null || model.getScope().isEmpty()) {
            model.setScope(SCOPE_PROJECT);
        }
        if (model.getCurrentVersion() == null) {
            model.setCurrentVersion(0);
        }
        if (model.getStatus() == null) {
            model.setStatus(1);
        }
        fillProjectCode(model);
        modelMapper.insert(model);
        return model;
    }

    /**
     * 更新模型元信息（不包括文件内容）
     */
    public void update(RuleModel model) {
        RuleModel existing = modelMapper.selectById(model.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        fillProjectCode(model);
        modelMapper.updateById(model);
    }

    /**
     * 删除模型
     */
    public void delete(Long modelId) {
        modelMapper.deleteById(modelId);
        inputFieldMapper.delete(new LambdaQueryWrapper<RuleModelInputField>()
                .eq(RuleModelInputField::getModelId, modelId));
        outputFieldMapper.delete(new LambdaQueryWrapper<RuleModelOutputField>()
                .eq(RuleModelOutputField::getModelId, modelId));
        versionMapper.delete(new LambdaQueryWrapper<RuleModelVersion>()
                .eq(RuleModelVersion::getModelId, modelId));
        refMapper.delete(new LambdaQueryWrapper<RuleModelRef>()
                .eq(RuleModelRef::getModelId, modelId));
    }

    /**
     * 发布模型（创建版本快照）
     */
    public void publish(Long modelId, String changeLog, String publishBy) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");

        int newVersion = (model.getCurrentVersion() != null ? model.getCurrentVersion() : 0) + 1;
        model.setPublishedVersion(newVersion);
        model.setCurrentVersion(newVersion);
        modelMapper.updateById(model);

        saveVersionSnapshot(modelId, newVersion, model.getModelContent(),
                model.getModelConfig(), changeLog, publishBy);
    }

    /**
     * 下线模型（清除发布版本）
     */
    public void unpublish(Long modelId) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");

        model.setPublishedVersion(null);
        modelMapper.updateById(model);
    }

    /**
     * 分页查询模型列表
     */
    public IPage<RuleModel> pageList(int pageNum, int pageSize, Long projectId,
            String scope, String modelType, String modelFormat, String modelCode,
            String modelName, String projectCode, String projectName) {
        LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<>();

        if (scope != null && !scope.isEmpty()) {
            wrapper.eq(RuleModel::getScope, scope);
        }

        if (projectId != null && projectId > 0) {
            if (scope == null || scope.isEmpty()) {
                wrapper.and(w -> w
                        .eq(RuleModel::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleModel::getScope, SCOPE_PROJECT)
                        .eq(RuleModel::getProjectId, projectId));
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleModel::getProjectId, projectId);
            }
        }

        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(RuleModel::getModelType, modelType);
        }

        if (modelFormat != null && !modelFormat.isEmpty()) {
            wrapper.eq(RuleModel::getModelFormat, modelFormat);
        }

        if (modelCode != null && !modelCode.isEmpty()) {
            wrapper.like(RuleModel::getModelCode, modelCode);
        }

        if (modelName != null && !modelName.isEmpty()) {
            wrapper.like(RuleModel::getModelName, modelName);
        }

        // 通过 projectCode 或 projectName 进行筛选
        if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectCode, projectCode))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleModel::getProjectId, projectIds)
                        .or()
                        .eq(RuleModel::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleModel::getScope, SCOPE_GLOBAL);
            }
        } else if (projectName != null && !projectName.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().eq(RuleProject::getProjectName, projectName))
                    .stream().map(RuleProject::getId).collect(Collectors.toList());
            if (!projectIds.isEmpty()) {
                wrapper.and(w -> w.in(RuleModel::getProjectId, projectIds)
                        .or()
                        .eq(RuleModel::getScope, SCOPE_GLOBAL));
            } else {
                wrapper.eq(RuleModel::getScope, SCOPE_GLOBAL);
            }
        }

        wrapper.orderByDesc(RuleModel::getId);
        IPage<RuleModel> result = modelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(result.getRecords());
        return result;
    }

    /**
     * 获取模型详情（含字段信息）
     */
    public RuleModel getDetail(Long modelId) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) return null;

        model.setInputFields(inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .eq(RuleModelInputField::getModelId, modelId)
                        .orderByAsc(RuleModelInputField::getSortOrder)));

        model.setOutputFields(outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .eq(RuleModelOutputField::getModelId, modelId)
                        .orderByAsc(RuleModelOutputField::getSortOrder)));

        return model;
    }

    /**
     * 查询项目下所有模型（非分页，设计器使用）
     */
    public List<RuleModel> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w
                    .eq(RuleModel::getScope, SCOPE_GLOBAL)
                    .or()
                    .eq(RuleModel::getScope, SCOPE_PROJECT)
                    .eq(RuleModel::getProjectId, projectId));
        } else {
            wrapper.eq(RuleModel::getScope, SCOPE_GLOBAL);
        }
        wrapper.eq(RuleModel::getStatus, 1)
               .orderByAsc(RuleModel::getModelCode);
        return modelMapper.selectList(wrapper);
    }

    /**
     * 添加全局模型到项目
     */
    public void addModelRef(Long modelId, Long projectId) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");
        if (!SCOPE_GLOBAL.equals(model.getScope())) {
            throw new IllegalArgumentException("只有全局模型才能关联到项目");
        }
        RuleModelRef ref = new RuleModelRef();
        ref.setModelId(modelId);
        ref.setProjectId(projectId);
        refMapper.insert(ref);
    }

    /**
     * 从项目移除全局模型
     */
    public void removeModelRef(Long modelId, Long projectId) {
        refMapper.delete(new LambdaQueryWrapper<RuleModelRef>()
                .eq(RuleModelRef::getModelId, modelId)
                .eq(RuleModelRef::getProjectId, projectId));
    }

    /**
     * 保存版本快照
     */
    private void saveVersionSnapshot(Long modelId, int version, String modelContent,
            String modelConfig, String changeLog, String publishBy) {
        RuleModelVersion modelVersion = new RuleModelVersion();
        modelVersion.setModelId(modelId);
        modelVersion.setVersion(version);
        modelVersion.setModelContent(modelContent);
        modelVersion.setModelConfig(modelConfig);
        modelVersion.setChangeLog(changeLog);
        modelVersion.setPublishBy(publishBy);
        modelVersion.setPublishTime(LocalDateTime.now());
        versionMapper.insert(modelVersion);
    }

    /**
     * 检测模型格式
     */
    private String detectFormat(String fileName) {
        if (fileName == null) return "PMML";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pmml") || lower.endsWith(".xml")) return "PMML";
        if (lower.endsWith(".onnx")) return "ONNX";
        if (lower.endsWith(".pb")) return "TENSORFLOW";
        if (lower.endsWith(".pkl") || lower.endsWith(".pickle")) return "PICKLE";
        if (lower.endsWith(".txt") || lower.endsWith(".txt")) return "LIGHTGBM";
        return "PMML";
    }

    /**
     * 解析 PMML 文件
     */
    private PmmlParseResult parsePmml(String content) {
        PmmlParseResult result = new PmmlParseResult();
        result.setModelFormat("PMML");
        result.setModelType("ML");

        try {
            // 使用简单的 XML 解析提取字段信息
            result.setInputFields(extractPmmlInputFields(content));
            result.setOutputFields(extractPmmlOutputFields(content));
            result.setModelConfig(java.util.Collections.emptyMap());
        } catch (Exception e) {
            throw new RuntimeException("PMML解析失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 从 PMML 内容提取输入字段
     */
    private java.util.List<RuleModelInputField> extractPmmlInputFields(String content) throws Exception {
        java.util.List<RuleModelInputField> fields = new java.util.ArrayList<>();

        // 简单的 XML 解析：提取 DataField
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<DataField\\s+name=\"([^\"]+)\"[^>]*dataType=\"([^\"]+)\"[^>]*optype=\"([^\"]+)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(content);

        int sortOrder = 0;
        while (matcher.find()) {
            RuleModelInputField field = new RuleModelInputField();
            field.setFieldName(matcher.group(1));
            field.setFieldLabel(matcher.group(1));
            field.setFieldType(mapPmmlDataType(matcher.group(2)));
            field.setDataType(matcher.group(3).toUpperCase());
            field.setTransformType("NONE");
            field.setSortOrder(sortOrder++);
            fields.add(field);
        }

        // 如果上面没匹配到，尝试更宽松的模式
        if (fields.isEmpty()) {
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                    "<DataField\\s+name=\"([^\"]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m2 = p2.matcher(content);
            while (m2.find()) {
                RuleModelInputField field = new RuleModelInputField();
                field.setFieldName(m2.group(1));
                field.setFieldLabel(m2.group(1));
                field.setFieldType("STRING");
                field.setDataType("CONTINUOUS");
                field.setTransformType("NONE");
                field.setSortOrder(sortOrder++);
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * 从 PMML 内容提取输出字段
     */
    private java.util.List<RuleModelOutputField> extractPmmlOutputFields(String content) {
        java.util.List<RuleModelOutputField> fields = new java.util.ArrayList<>();

        // 提取 OutputField
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<OutputField\\s+name=\"([^\"]+)\"[^>]*dataType=\"([^\"]+)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(content);

        int sortOrder = 0;
        while (matcher.find()) {
            RuleModelOutputField field = new RuleModelOutputField();
            field.setFieldName(matcher.group(1));
            field.setFieldLabel(matcher.group(1));
            field.setFieldType(mapPmmlDataType(matcher.group(2)));
            field.setSortOrder(sortOrder++);
            fields.add(field);
        }

        // 如果上面没匹配到，尝试更宽松的模式
        if (fields.isEmpty()) {
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
                    "<OutputField\\s+name=\"([^\"]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m2 = p2.matcher(content);
            while (m2.find()) {
                RuleModelOutputField field = new RuleModelOutputField();
                field.setFieldName(m2.group(1));
                field.setFieldLabel(m2.group(1));
                field.setFieldType("STRING");
                field.setSortOrder(sortOrder++);
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * 映射 PMML 数据类型到标准类型
     */
    private String mapPmmlDataType(String dataType) {
        if (dataType == null) return "STRING";
        String dt = dataType.toUpperCase();
        switch (dt) {
            case "DOUBLE":
            case "FLOAT":
                return "DOUBLE";
            case "INTEGER":
                return "INTEGER";
            case "BOOLEAN":
                return "BOOLEAN";
            case "DATE":
            case "DATETIME":
            case "TIME":
                return "DATE";
            default:
                return "STRING";
        }
    }

    /**
     * PMML 解析结果内部类
     */
    private static class PmmlParseResult {
        private String modelType;
        private String modelFormat;
        private java.util.List<RuleModelInputField> inputFields;
        private java.util.List<RuleModelOutputField> outputFields;
        private java.util.Map<String, Object> modelConfig;

        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }
        public String getModelFormat() { return modelFormat; }
        public void setModelFormat(String modelFormat) { this.modelFormat = modelFormat; }
        public java.util.List<RuleModelInputField> getInputFields() { return inputFields; }
        public void setInputFields(java.util.List<RuleModelInputField> inputFields) { this.inputFields = inputFields; }
        public java.util.List<RuleModelOutputField> getOutputFields() { return outputFields; }
        public void setOutputFields(java.util.List<RuleModelOutputField> outputFields) { this.outputFields = outputFields; }
        public java.util.Map<String, Object> getModelConfig() { return modelConfig; }
        public void setModelConfig(java.util.Map<String, Object> modelConfig) { this.modelConfig = modelConfig; }
    }
}