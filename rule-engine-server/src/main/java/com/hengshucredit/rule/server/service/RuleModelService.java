package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.core.pmml.PMMLModelExecutor;
import com.hengshucredit.rule.core.function.BuiltinFunctionInvoker;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeSessionManager;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import com.hengshucredit.rule.server.service.onnx.OnnxRuntimeConfig;
import com.hengshucredit.rule.server.service.onnx.OnnxTaskConfig;
import com.hengshucredit.rule.server.service.onnx.OnnxTaskType;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.*;
import org.jpmml.model.PMMLUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RuleModelService {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";
    public static final int DEFAULT_EXECUTION_TIMEOUT_MS = 120000;
    public static final int MIN_EXECUTION_TIMEOUT_MS = 100;
    public static final int MAX_EXECUTION_TIMEOUT_MS = 1800000;

    private final PMMLModelExecutor pmmlExecutor = new PMMLModelExecutor();

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
    @Resource
    private RuleVariableMapper variableMapper;
    @Resource
    private RuleDataObjectFieldMapper dataObjectFieldMapper;
    @Resource
    private ExecutionParameterBinder executionParameterBinder;
    @Resource
    private RuleFunctionService ruleFunctionService;
    @Resource
    private RuleVariableService variableService;
    @Resource
    private OnnxRuntimeSessionManager onnxSessionManager;
    @Resource
    private OnnxModelExecutionService onnxModelExecutionService;
    @Resource
    private ModelExecutionTimeoutExecutor modelExecutionTimeoutExecutor;

    /**
     * 检查模型编码是否与现有模型冲突
     * - GLOBAL: 全局唯一
     * - PROJECT: 在指定项目内唯一，且不能与任何全局编码冲突
     * @param excludeId 编辑时排除自身ID
     * @return true=有冲突，false=可用
     */
    public boolean existsModelCodeConflict(String modelCode, String scope, Long projectId, Long excludeId) {
        if (modelCode == null || modelCode.trim().isEmpty()) return false;
        String trimmedCode = modelCode.trim();

        if (SCOPE_GLOBAL.equals(scope)) {
            LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<RuleModel>()
                    .eq(RuleModel::getScope, SCOPE_GLOBAL)
                    .eq(RuleModel::getModelCode, trimmedCode);
            if (excludeId != null) wrapper.ne(RuleModel::getId, excludeId);
            return modelMapper.selectCount(wrapper) > 0;
        }

        LambdaQueryWrapper<RuleModel> globalWrapper = new LambdaQueryWrapper<RuleModel>()
                .eq(RuleModel::getScope, SCOPE_GLOBAL)
                .eq(RuleModel::getModelCode, trimmedCode);
        if (excludeId != null) globalWrapper.ne(RuleModel::getId, excludeId);
        if (modelMapper.selectCount(globalWrapper) > 0) return true;

        if (projectId != null && projectId > 0) {
            LambdaQueryWrapper<RuleModel> projectWrapper = new LambdaQueryWrapper<RuleModel>()
                    .eq(RuleModel::getScope, SCOPE_PROJECT)
                    .eq(RuleModel::getProjectId, projectId)
                    .eq(RuleModel::getModelCode, trimmedCode);
            if (excludeId != null) projectWrapper.ne(RuleModel::getId, excludeId);
            return modelMapper.selectCount(projectWrapper) > 0;
        }
        return false;
    }

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

    private void fillOutputFields(List<RuleModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> modelIds = list.stream()
                .map(RuleModel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (modelIds.isEmpty()) return;
        List<RuleModelOutputField> fields = outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .in(RuleModelOutputField::getModelId, modelIds)
                        .orderByAsc(RuleModelOutputField::getSortOrder)
                        .orderByAsc(RuleModelOutputField::getId));
        Map<Long, List<RuleModelOutputField>> byModel = fields.stream()
                .collect(Collectors.groupingBy(RuleModelOutputField::getModelId, LinkedHashMap::new, Collectors.toList()));
        list.forEach(model -> model.setOutputFields(byModel.getOrDefault(model.getId(), Collections.emptyList())));
    }

    private void fillInputFields(List<RuleModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> modelIds = list.stream()
                .map(RuleModel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (modelIds.isEmpty()) return;
        List<RuleModelInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .in(RuleModelInputField::getModelId, modelIds)
                        .and(w -> w.isNull(RuleModelInputField::getStatus).or().eq(RuleModelInputField::getStatus, 1))
                        .orderByAsc(RuleModelInputField::getSortOrder)
                        .orderByAsc(RuleModelInputField::getId));
        Map<Long, List<RuleModelInputField>> byModel = fields.stream()
                .collect(Collectors.groupingBy(RuleModelInputField::getModelId, LinkedHashMap::new, Collectors.toList()));
        list.forEach(model -> model.setInputFields(byModel.getOrDefault(model.getId(), Collections.emptyList())));
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
            String changeLog, String testParams) {
        return uploadAndParse(file, projectId, scope, modelCode, modelName, modelType, description,
                changeLog, testParams, null, null, 0, DEFAULT_EXECUTION_TIMEOUT_MS);
    }

    public RuleModel uploadAndParse(MultipartFile file, Long projectId, String scope,
            String modelCode, String modelName, String modelType, String description,
            String changeLog, String testParams, String onnxTaskType, String onnxConfig) {
        return uploadAndParse(file, projectId, scope, modelCode, modelName, modelType, description,
                changeLog, testParams, onnxTaskType, onnxConfig, 0, DEFAULT_EXECUTION_TIMEOUT_MS);
    }

    public RuleModel uploadAndParse(MultipartFile file, Long projectId, String scope,
            String modelCode, String modelName, String modelType, String description,
            String changeLog, String testParams, String onnxTaskType, String onnxConfig,
            Integer preloadOnStartup, Integer executionTimeoutMs) {
        try {
            byte[] fileBytes = file.getBytes();
            String modelContent = Base64.getEncoder().encodeToString(fileBytes);

            String modelFormat = detectFormat(file.getOriginalFilename());

            Map<String, Object> modelConfig = new LinkedHashMap<>();
            List<RuleModelInputField> inputFields = null;
            List<RuleModelOutputField> outputFields = null;

            if ("PMML".equals(modelFormat)) {
                String rawContent = new String(fileBytes, StandardCharsets.UTF_8);
                PmmlParseResult result = parsePmml(rawContent);
                if (result.getModelConfig() != null) modelConfig.putAll(result.getModelConfig());
                inputFields = result.getInputFields();
                outputFields = result.getOutputFields();
                if (modelType == null || modelType.isEmpty()) {
                    modelType = result.getModelType();
                }
            } else if ("ONNX".equals(modelFormat)) {
                com.alibaba.fastjson.JSONObject rawConfig;
                try {
                    rawConfig = onnxConfig == null || onnxConfig.trim().isEmpty()
                            ? new com.alibaba.fastjson.JSONObject()
                            : com.alibaba.fastjson.JSON.parseObject(onnxConfig);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("ONNX 配置不是有效 JSON", e);
                }
                rawConfig.put("onnxTaskType", onnxTaskType);
                OnnxTaskConfig taskConfig = OnnxTaskConfig.parse(rawConfig.toJSONString());
                modelConfig.putAll(taskConfig.toJsonObject());
                modelConfig.put("nodeMetadata", onnxSessionManager.inspect(fileBytes));
                inputFields = buildOnnxInputFields(taskConfig.getTaskType());
                outputFields = buildOnnxOutputFields(taskConfig.getTaskType());
            }

            if (testParams != null && !testParams.isEmpty()) modelConfig.put("testParams", testParams);

            String projectCode = null;
            String projectName = null;
            if (projectId != null && projectId > 0) {
                RuleProject project = projectMapper.selectById(projectId);
                if (project != null) {
                    projectCode = project.getProjectCode();
                    projectName = project.getProjectName();
                }
            }

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
            if (!modelConfig.isEmpty()) {
                model.setModelConfig(com.alibaba.fastjson.JSON.toJSONString(modelConfig));
            }
            model.setPreloadOnStartup(normalizedPreload(preloadOnStartup));
            model.setExecutionTimeoutMs(normalizedExecutionTimeout(executionTimeoutMs));
            model.setInputFieldCount(inputFields != null ? inputFields.size() : 0);
            model.setOutputFieldCount(outputFields != null ? outputFields.size() : 0);
            model.setCurrentVersion(1);
            model.setStatus(1);
            modelMapper.insert(model);

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

            if (outputFields != null) {
                for (int i = 0; i < outputFields.size(); i++) {
                    RuleModelOutputField outputField = outputFields.get(i);
                    outputField.setModelId(model.getId());
                    outputField.setSortOrder(i);
                    outputField.setCreateTime(LocalDateTime.now());
                    outputFieldMapper.insert(outputField);
                }
            }

            saveVersionSnapshot(model.getId(), 1, modelContent, model.getModelConfig(), changeLog, null);

            model.setModelContent(null);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("读取模型文件失败: " + e.getMessage(), e);
        }
    }

    private List<RuleModelInputField> buildOnnxInputFields(OnnxTaskType taskType) {
        List<RuleModelInputField> fields = new ArrayList<>();
        for (OnnxTaskType.FieldSpec spec : taskType.getInputs()) {
            RuleModelInputField field = new RuleModelInputField();
            field.setFieldName(spec.getName());
            field.setFieldLabel(spec.getLabel());
            field.setFieldType(spec.getType());
            field.setDataType("CONTINUOUS");
            field.setTransformType("NONE");
            fields.add(field);
        }
        return fields;
    }

    private List<RuleModelOutputField> buildOnnxOutputFields(OnnxTaskType taskType) {
        List<RuleModelOutputField> fields = new ArrayList<>();
        for (OnnxTaskType.FieldSpec spec : taskType.getOutputs()) {
            RuleModelOutputField field = new RuleModelOutputField();
            field.setFieldName(spec.getName());
            field.setFieldLabel(spec.getLabel());
            field.setFieldType(spec.getType());
            field.setIsProbability(0);
            fields.add(field);
        }
        return fields;
    }

    /**
     * 手动创建模型（不依赖文件上传）
     */
    public RuleModel create(RuleModel model) {
        validateSupportedFormat(model);
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
     * 更新模型元信息（不包括文件内容和编码/项目等核心字段）
     */
    public void update(RuleModel model) {
        RuleModel existing = modelMapper.selectOne(
                withoutModelContent().eq(RuleModel::getId, model.getId()));
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        validateSupportedFormat(existing);
        // 只更新允许变更的字段，避免覆盖文件内容等核心数据
        existing.setModelName(model.getModelName());
        existing.setDescription(model.getDescription());
        existing.setTargetCategories(model.getTargetCategories());
        existing.setModelVersion(model.getModelVersion());
        if (model.getPreloadOnStartup() != null) {
            existing.setPreloadOnStartup(normalizedPreload(model.getPreloadOnStartup()));
        }
        if (model.getExecutionTimeoutMs() != null) {
            existing.setExecutionTimeoutMs(normalizedExecutionTimeout(model.getExecutionTimeoutMs()));
        }
        if ("ONNX".equals(existing.getModelFormat())
                && model.getModelConfig() != null && !model.getModelConfig().trim().isEmpty()) {
            existing.setModelConfig(mergeOnnxRuntimeConfig(existing.getModelConfig(), model.getModelConfig()));
        }
        if (model.getStatus() != null) {
            existing.setStatus(model.getStatus());
        }
        modelMapper.updateById(existing);
    }

    private String mergeOnnxRuntimeConfig(String existingConfig, String requestedConfig) {
        try {
            com.alibaba.fastjson.JSONObject existing = existingConfig == null || existingConfig.trim().isEmpty()
                    ? new com.alibaba.fastjson.JSONObject()
                    : com.alibaba.fastjson.JSON.parseObject(existingConfig);
            com.alibaba.fastjson.JSONObject requested = com.alibaba.fastjson.JSON.parseObject(requestedConfig);
            OnnxRuntimeConfig.from(requested).applyTo(existing);
            return existing.toJSONString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ONNX 运行配置不是有效 JSON", e);
        }
    }

    public Map<String, Object> runtimeCapabilities() {
        return onnxSessionManager.runtimeCapabilities();
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
        validateSupportedFormat(model);
        validatePublishFields(modelId);

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

    public List<RuleModelVersion> listVersions(Long modelId) {
        return versionMapper.selectList(new LambdaQueryWrapper<RuleModelVersion>()
                .eq(RuleModelVersion::getModelId, modelId)
                .orderByDesc(RuleModelVersion::getVersion));
    }

    public RuleModelVersion getVersion(Long modelId, Integer version) {
        return versionMapper.selectOne(new LambdaQueryWrapper<RuleModelVersion>()
                .eq(RuleModelVersion::getModelId, modelId)
                .eq(RuleModelVersion::getVersion, version));
    }

    public Map<String, Object> compareVersions(Long modelId, Integer leftVersion, Integer rightVersion) {
        RuleModelVersion left = getVersion(modelId, leftVersion);
        RuleModelVersion right = getVersion(modelId, rightVersion);
        if (left == null || right == null) {
            throw new IllegalArgumentException("Version not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", left);
        result.put("right", right);
        result.put("modelContentChanged", !equalsText(left.getModelContent(), right.getModelContent()));
        result.put("modelConfigChanged", !equalsText(left.getModelConfig(), right.getModelConfig()));
        return result;
    }

    public void rollbackToVersion(Long modelId, Integer version) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");
        validateSupportedFormat(model);
        RuleModelVersion snapshot = getVersion(modelId, version);
        if (snapshot == null) throw new IllegalArgumentException("Version not found");

        model.setModelContent(snapshot.getModelContent());
        model.setModelConfig(snapshot.getModelConfig());
        model.setCurrentVersion((model.getCurrentVersion() == null ? 0 : model.getCurrentVersion()) + 1);
        modelMapper.updateById(model);
    }

    private boolean equalsText(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    /**
     * 分页查询模型列表
     */
    public IPage<RuleModel> pageList(int pageNum, int pageSize, Long projectId,
            String scope, String modelType, String modelFormat, String modelCode,
            String modelName, String projectCode, String projectName) {
        LambdaQueryWrapper<RuleModel> wrapper = withoutModelContent();

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

        if (projectCode != null && !projectCode.isEmpty()) {
            List<Long> projectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectCode, projectCode))
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
                    new LambdaQueryWrapper<RuleProject>().like(RuleProject::getProjectName, projectName))
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
        clearModelContent(result.getRecords());
        return result;
    }

    /**
     * 获取模型详情（含字段信息）
     */
    public RuleModel getDetail(Long modelId) {
        RuleModel model = modelMapper.selectOne(withoutModelContent().eq(RuleModel::getId, modelId));
        if (model == null) return null;

        model.setInputFields(inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelInputField>()
                        .eq(RuleModelInputField::getModelId, modelId)
                        .orderByAsc(RuleModelInputField::getSortOrder)));

        model.setOutputFields(outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>()
                        .eq(RuleModelOutputField::getModelId, modelId)
                        .orderByAsc(RuleModelOutputField::getSortOrder)));

        model.setModelContent(null);
        return model;
    }

    /**
     * 查询项目下所有模型（非分页，设计器使用）
     */
    public List<RuleModel> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleModel> wrapper = withoutModelContent();
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
        List<RuleModel> models = modelMapper.selectList(wrapper);
        fillInputFields(models);
        fillOutputFields(models);
        clearModelContent(models);
        return models;
    }

    private LambdaQueryWrapper<RuleModel> withoutModelContent() {
        return new LambdaQueryWrapper<RuleModel>()
                .select(RuleModel.class, field -> !"model_content".equals(field.getColumn()));
    }

    private void clearModelContent(List<RuleModel> models) {
        if (models == null) return;
        for (RuleModel model : models) {
            if (model != null) model.setModelContent(null);
        }
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
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型文件名不能为空，仅支持 ONNX 和 PMML 格式");
        }
        String lower = fileName.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pmml")) return "PMML";
        if (lower.endsWith(".onnx")) return "ONNX";
        throw new IllegalArgumentException("不支持的模型格式，仅支持 ONNX 和 PMML");
    }

    // ========== PMML 纯 JPMML 实现 ==========

    /**
     * 解析 PMML 文件，全部使用 JPMML 库实现
     * - 入参从顶层 MiningSchema 的 active 字段读取（排除 target）
     * - 出参从顶层 Output 的 OutputField 读取（仅 isFinalResult="true"）
     * - evaluator 仅用于模型类型检测和配置提取
     */
    private PmmlParseResult parsePmml(String content) {
        PmmlParseResult result = new PmmlParseResult();
        result.setModelFormat("PMML");
        result.setModelType("ML");

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            PMML pmml = PMMLUtil.unmarshal(bis);

            List<Model> models = pmml.getModels();
            if (models == null || models.isEmpty()) {
                throw new RuntimeException("PMML 文件中未找到任何模型");
            }

            Model firstModel = models.get(0);

            // 1. 从 PMML 数据结构直接提取入参（MiningSchema 中非 target 的 active 字段）
            List<RuleModelInputField> inputs = buildInputFieldsFromPmml(firstModel);
            result.setInputFields(inputs);

            // 2. 从 PMML 数据结构直接提取出参（Output 中 isFinalResult="true" 的字段）
            List<RuleModelOutputField> outputs = buildOutputFieldsFromPmml(firstModel);
            result.setOutputFields(outputs);

            // 3. 通过 evaluator 获取模型类型（不调用 getInputFields/getOutputFields，避免链式模型异常）
            result.setModelType(detectModelTypeFromModel(firstModel));
            // 尝试从 evaluator 获取更详细的配置
            try {
                result.setModelConfig(extractModelConfigFromEvaluator(firstModel, pmml));
            } catch (Exception ex) {
                // 链式 MiningModel 评估器可能无法完整初始化，降级使用基础配置
                result.setModelConfig(extractModelConfigFromPmml(firstModel));
            }

        } catch (Exception e) {
            throw new RuntimeException("PMML 解析失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 从 PMML Model 结构提取入参字段（排除 target）
     */
    private java.util.List<RuleModelInputField> buildInputFieldsFromPmml(Model model) {
        java.util.List<RuleModelInputField> fields = new java.util.ArrayList<>();
        org.dmg.pmml.MiningSchema miningSchema = model.getMiningSchema();
        if (miningSchema == null) return fields;

        int sortOrder = 0;
        for (org.dmg.pmml.MiningField mf : miningSchema.getMiningFields()) {
            if (mf.getUsageType() == org.dmg.pmml.MiningField.UsageType.TARGET) continue;
            if (mf.getUsageType() == org.dmg.pmml.MiningField.UsageType.GROUP) continue;

            RuleModelInputField field = new RuleModelInputField();
            String name = mf.getName().getValue();
            field.setFieldName(name);
            field.setScriptName(name);
            field.setFieldLabel(name);
            field.setDataType("CONTINUOUS");
            field.setFieldType("DOUBLE");
            field.setTransformType("NONE");
            field.setSortOrder(sortOrder++);
            fields.add(field);
        }
        return fields;
    }

    /**
     * 从 PMML Model 结构提取出参字段（仅 isFinalResult=true）
     * 对于 MiningModel 链式模型（modelChain），Output 在最后一个 Segment 中，需要遍历查找
     */
    private java.util.List<RuleModelOutputField> buildOutputFieldsFromPmml(Model model) {
        java.util.List<RuleModelOutputField> fields = new java.util.ArrayList<>();
        org.dmg.pmml.Output pmmlOutput = model.getOutput();

        // 对于 MiningModel 如果顶层没有 Output，遍历 Segmentation 找最后一个有 Output 的 segment
        if (pmmlOutput == null && model instanceof org.dmg.pmml.mining.MiningModel) {
            org.dmg.pmml.mining.MiningModel miningModel = (org.dmg.pmml.mining.MiningModel) model;
            org.dmg.pmml.mining.Segmentation segmentation = miningModel.getSegmentation();
            if (segmentation != null && segmentation.hasSegments()) {
                List<org.dmg.pmml.mining.Segment> segments = segmentation.getSegments();
                // 从后往前找第一个有 Output 的 segment（链式模型最后一个 segment 通常是输出）
                for (int i = segments.size() - 1; i >= 0; i--) {
                    org.dmg.pmml.mining.Segment seg = segments.get(i);
                    if (seg.getModel() != null && seg.getModel().getOutput() != null) {
                        pmmlOutput = seg.getModel().getOutput();
                        break;
                    }
                }
            }
        }

        if (pmmlOutput == null) {
            // Fallback: 从 MiningSchema 的 TARGET 字段提取输出（sklearn2pmml 导出的 RegressionModel 无 <Output> 时）
            org.dmg.pmml.MiningSchema miningSchema = model.getMiningSchema();
            if (miningSchema != null) {
                for (org.dmg.pmml.MiningField mf : miningSchema.getMiningFields()) {
                    if (mf.getUsageType() == org.dmg.pmml.MiningField.UsageType.TARGET) {
                        RuleModelOutputField field = new RuleModelOutputField();
                        String name = mf.getName().getValue();
                        field.setFieldName(name);
                        field.setFieldLabel(name);
                        field.setFieldType("DOUBLE");
                        field.setIsProbability(0);
                        field.setSortOrder(0);
                        fields.add(field);
                        break; // 通常只有一个 target
                    }
                }
            }
            return fields;
        }

        int sortOrder = 0;
        for (org.dmg.pmml.OutputField of : pmmlOutput.getOutputFields()) {
            Boolean isFinal = of.isFinalResult();
            if (isFinal != null && !isFinal) continue;

            RuleModelOutputField field = new RuleModelOutputField();
            String name = of.getName().getValue();
            field.setFieldName(name);
            field.setFieldLabel(name);
            field.setFieldType("DOUBLE");
            field.setIsProbability(0);

            if (of.getResultFeature() != null) {
                String feature = of.getResultFeature().value();
                field.setFeatureName(feature);
                if ("probability".equalsIgnoreCase(feature)) {
                    field.setIsProbability(1);
                }
            }
            field.setSortOrder(sortOrder++);
            fields.add(field);
        }
        return fields;
    }

    /**
     * 从 PMML Model 类名检测模型类型
     */
    private String detectModelTypeFromModel(Model model) {
        String className = model.getClass().getSimpleName();
        if (className.contains("XGBoost")) return "XGBOOST";
        if (className.contains("LightGBM")) return "LIGHTGBM";
        if (className.contains("Regression")) return "LR";
        if (className.contains("TreeModel")) return "TREE";
        if (className.contains("NeuralNetwork")) return "NEURAL_NET";
        if (className.contains("SVM")) return "SVM";
        if (className.contains("RandomForest")) return "RANDOM_FOREST";
        if (className.contains("NaiveBayes")) return "NAIVE_BAYES";
        if (className.contains("GeneralRegression")) return "GLM";
        if (className.contains("MiningModel")) return "MINING";

        return "ML";
    }

    /**
     * 从 evaluator 提取模型配置（verify 后可获取 summary）
     */
    private java.util.Map<String, Object> extractModelConfigFromEvaluator(Model model, PMML pmml) {
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();
        try {
            ModelEvaluatorFactory evalFactory = ModelEvaluatorFactory.newInstance();
            ModelEvaluator<?> evaluator = evalFactory.newModelEvaluator(pmml, model);
            evaluator.verify();
            config.put("summary", evaluator.getSummary());
        } catch (Exception e) {
            // ignore
        }
        config.put("modelName", model.getModelName());
        if (model.getMiningFunction() != null) {
            config.put("functionName", model.getMiningFunction().value());
        }
        config.put("algorithmName", model.getAlgorithmName());
        return config;
    }

    /**
     * 从 PMML Model 直接提取配置（不依赖 evaluator）
     */
    private java.util.Map<String, Object> extractModelConfigFromPmml(Model model) {
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("modelName", model.getModelName());
        if (model.getMiningFunction() != null) {
            config.put("functionName", model.getMiningFunction().value());
        }
        config.put("algorithmName", model.getAlgorithmName());
        return config;
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

    // ========== 模型执行 ==========

    /**
     * 执行模型测试
     */
    public List<RuleModelInputField> listInputFields(Long modelId) {
        return inputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>()
                .eq(RuleModelInputField::getModelId, modelId)
                .and(w -> w.isNull(RuleModelInputField::getStatus).or().eq(RuleModelInputField::getStatus, 1))
                .orderByAsc(RuleModelInputField::getSortOrder)
                .orderByAsc(RuleModelInputField::getId));
    }

    public List<RuleModelOutputField> listOutputFields(Long modelId) {
        return outputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>()
                .eq(RuleModelOutputField::getModelId, modelId)
                .orderByAsc(RuleModelOutputField::getSortOrder)
                .orderByAsc(RuleModelOutputField::getId));
    }

    public Map<String, Object> execute(Long modelId, Map<String, Object> params) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        validateSupportedFormat(model);
        if (model.getModelContent() == null || model.getModelContent().isEmpty()) {
            throw new IllegalArgumentException("模型文件内容为空");
        }

        int timeoutMs = normalizedExecutionTimeout(model.getExecutionTimeoutMs());
        return modelExecutionTimeoutExecutor.execute(() -> executeConfiguredModel(model, params), timeoutMs);
    }

    private Map<String, Object> executeConfiguredModel(RuleModel model, Map<String, Object> params) {
        Long modelId = model.getId();
        List<RuleModelInputField> inputFields = listInputFields(modelId);
        Map<String, Object> referenceValues = referenceValues(model.getProjectId(), params);
        Map<String, Object> resolvedParams = OperandValueResolver.bindModelInputs(
                inputFields, params, referenceValues, this::invokeOperandFunction);
        Map<String, Object> boundParams = executionParameterBinder.bindModelInputs(inputFields, resolvedParams);
        if ("PMML".equals(model.getModelFormat())) {
            return executePmml(model, boundParams);
        }
        if ("ONNX".equals(model.getModelFormat())) {
            return executeOnnx(model, boundParams);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", model.getModelFormat() + " 格式暂不支持在线执行，仅 PMML 格式支持测试");
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", model.getModelFormat());
        result.put("inputParams", boundParams);
        return result;
    }

    private void validateSupportedFormat(RuleModel model) {
        String format = model == null || model.getModelFormat() == null
                ? "" : model.getModelFormat().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"ONNX".equals(format) && !"PMML".equals(format)) {
            throw new IllegalArgumentException("不支持的模型格式 "
                    + (format.isEmpty() ? "(empty)" : format) + "，仅支持 ONNX 和 PMML");
        }
    }

    private int normalizedExecutionTimeout(Integer timeoutMs) {
        int value = timeoutMs == null ? DEFAULT_EXECUTION_TIMEOUT_MS : timeoutMs;
        if (value < MIN_EXECUTION_TIMEOUT_MS || value > MAX_EXECUTION_TIMEOUT_MS) {
            throw new IllegalArgumentException("模型执行超时时间须在 " + MIN_EXECUTION_TIMEOUT_MS
                    + " 至 " + MAX_EXECUTION_TIMEOUT_MS + " 毫秒之间");
        }
        return value;
    }

    private int normalizedPreload(Integer preloadOnStartup) {
        int value = preloadOnStartup == null ? 0 : preloadOnStartup;
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("启动预加载配置只能为 0 或 1");
        }
        return value;
    }

    private Map<String, Object> executeOnnx(RuleModel model, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", "ONNX");
        try {
            byte[] modelBytes = Base64.getDecoder().decode(model.getModelContent());
            Map<String, Object> rawOutputs = onnxModelExecutionService.execute(
                    modelBytes, model.getModelConfig(), params);
            result.put("success", true);
            result.put("outputs", applyOutputTransforms(model, params, rawOutputs));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            result.put("success", false);
            result.put("error", "ONNX 执行失败: " + (message == null || message.isEmpty() ? e : message));
        }
        result.put("inputParams", params);
        result.put("executeTimeMs", System.currentTimeMillis() - startTime);
        return result;
    }

    private void validatePublishFields(Long modelId) {
        List<String> errors = new ArrayList<>();
        RuleModel model = modelMapper.selectById(modelId);
        List<RuleModelInputField> inputFields = inputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>()
                .eq(RuleModelInputField::getModelId, modelId)
                .orderByAsc(RuleModelInputField::getSortOrder));
        for (RuleModelInputField field : inputFields) {
            if (field.getStatus() != null && field.getStatus() == 0) {
                continue;
            }
            validateFieldBinding(errors, "输入字段", field.getFieldName(), field.getFieldLabel(),
                    field.getFieldType(), field.getVarId(), field.getRefType(), field.getScriptName(), field.getSourceOperand(), false);
        }

        List<RuleModelOutputField> outputFields = outputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>()
                .eq(RuleModelOutputField::getModelId, modelId)
                .orderByAsc(RuleModelOutputField::getSortOrder));
        for (RuleModelOutputField field : outputFields) {
            validateFieldBinding(errors, "输出字段", field.getFieldName(), field.getFieldLabel(),
                    field.getFieldType(), field.getVarId(), field.getRefType(), field.getScriptName(), field.getTargetOperand(), true);
            String transformError = validateTransformOperand(model, field.getFieldLabel(), field.getFieldName(), field.getTransformOperand());
            if (transformError != null) errors.add(transformError);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("模型发布校验失败：" + String.join("；", errors));
        }
    }

    private void validateFieldBinding(List<String> errors, String section, String fieldName, String fieldLabel,
                                      String fieldType, Long varId, String refType, String scriptName, String operandJson,
                                      boolean writableTarget) {
        String displayName = fieldLabel != null && !fieldLabel.trim().isEmpty() ? fieldLabel : fieldName;
        if (operandJson != null && !operandJson.trim().isEmpty()) {
            try {
                com.alibaba.fastjson.JSONObject operand = com.alibaba.fastjson.JSON.parseObject(operandJson);
                String kind = operand.getString("kind");
                if ("PATH".equals(kind)) return;
                if (!writableTarget && ("FUNCTION".equals(kind) || "LITERAL".equals(kind))) return;
                if ("REFERENCE".equals(kind) && operand.getLong("refId") != null && operand.getString("refType") != null) {
                    String operandRefType = operand.getString("refType").toUpperCase();
                    if (!writableTarget || "VARIABLE".equals(operandRefType)
                            || "DATA_OBJECT".equals(operandRefType) || "DATA_FIELD".equals(operandRefType)) return;
                }
                errors.add(section + "「" + displayName + "」Operand 配置不完整");
                return;
            } catch (Exception e) {
                errors.add(section + "「" + displayName + "」Operand JSON 无效");
                return;
            }
        }
        if (varId == null || refType == null || refType.trim().isEmpty() || scriptName == null || scriptName.trim().isEmpty()) {
            errors.add(section + "「" + displayName + "」未完整关联变量");
            return;
        }
        String actualType = resolveRefVarType(refType, varId);
        if (actualType == null || actualType.trim().isEmpty()) {
            errors.add(section + "「" + displayName + "」关联资源不存在");
            return;
        }
        if (!isTypeCompatible(fieldType, actualType)) {
            errors.add(section + "「" + displayName + "」类型不匹配，模型字段为 " + normalizeType(fieldType) + "，关联字段为 " + normalizeType(actualType));
        }
    }

    private String resolveRefVarType(String refType, Long varId) {
        if (varId == null || refType == null) return null;
        String normalized = refType.trim().toUpperCase();
        if ("VARIABLE".equals(normalized) || "CONSTANT".equals(normalized)) {
            RuleVariable variable = variableMapper.selectById(varId);
            return variable == null ? null : variable.getVarType();
        }
        if ("DATA_OBJECT".equals(normalized) || "DATA_FIELD".equals(normalized)) {
            RuleDataObjectField field = dataObjectFieldMapper.selectById(varId);
            return field == null ? null : field.getVarType();
        }
        if ("MODEL".equals(normalized)) {
            RuleModel model = modelMapper.selectById(varId);
            return model == null ? null : "MODEL";
        }
        return null;
    }

    private boolean isTypeCompatible(String fieldType, String actualType) {
        String expected = normalizeType(fieldType);
        String actual = normalizeType(actualType);
        if (expected.isEmpty() || actual.isEmpty()) return true;
        if (expected.equals(actual)) return true;
        if (isNumericType(expected) && isNumericType(actual)) return true;
        if ("PROBABILITY".equals(expected) && isNumericType(actual)) return true;
        if ("ENUM".equals(expected) && ("STRING".equals(actual) || "ENUM".equals(actual))) return true;
        if ("STRING".equals(expected) && "ENUM".equals(actual)) return true;
        if ("OBJECT".equals(expected) && "MAP".equals(actual)) return true;
        if ("MAP".equals(expected) && "OBJECT".equals(actual)) return true;
        if ("LIST".equals(expected) && "ARRAY".equals(actual)) return true;
        if ("ARRAY".equals(expected) && "LIST".equals(actual)) return true;
        return false;
    }

    private boolean isNumericType(String type) {
        return "NUMBER".equals(type) || "INTEGER".equals(type) || "INT".equals(type)
                || "LONG".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)
                || "DECIMAL".equals(type);
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }

    /**
     * 执行 PMML 模型预测，通过 PMMLModelExecutor 实现
     */
    private Map<String, Object> executePmml(RuleModel model, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", "PMML");

        try {
            Map<String, Object> rawOutputs = pmmlExecutor.evaluate(model.getModelContent(), params);
            Map<String, Object> outputs = applyOutputTransforms(model, params, rawOutputs);
            result.put("success", true);
            result.put("outputs", outputs);
            result.put("inputParams", params);
            result.put("executeTimeMs", System.currentTimeMillis() - startTime);
        } catch (RuntimeException e) {
            String errMsg = e.getMessage();
            if (errMsg == null || errMsg.isEmpty()) errMsg = e.toString();
            result.put("success", false);
            result.put("error", "PMML 执行失败: " + errMsg);
            result.put("inputParams", params);
            result.put("executeTimeMs", System.currentTimeMillis() - startTime);
        }

        return result;
    }

    private Map<String, Object> applyOutputTransforms(RuleModel model, Map<String, Object> params,
                                                       Map<String, Object> rawOutputs) {
        Map<String, Object> original = rawOutputs == null ? Collections.emptyMap() : new LinkedHashMap<>(rawOutputs);
        Map<String, Object> transformed = new LinkedHashMap<>(original);
        if (model == null || model.getId() == null) return transformed;
        Map<String, Object> context = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        if (model.getModelCode() != null && !model.getModelCode().trim().isEmpty()) {
            context.put(model.getModelCode(), original);
        }
        Map<String, Object> referenceValues = new LinkedHashMap<>(referenceValues(model.getProjectId(), context));
        List<RuleModelOutputField> outputFields = listOutputFields(model.getId());
        for (RuleModelOutputField field : outputFields) {
            Object rawValue = original.get(outputKey(field, original));
            OperandValueResolver.write(field.getTargetOperand(), context, rawValue);
            if (field.getVarId() != null && field.getRefType() != null && !field.getRefType().trim().isEmpty()) {
                referenceValues.put(field.getRefType().trim().toUpperCase() + ":" + field.getVarId(), rawValue);
            }
        }
        for (RuleModelOutputField field : outputFields) {
            String transformJson = field.getTransformOperand();
            if (transformJson == null || transformJson.trim().isEmpty()) continue;
            com.alibaba.fastjson.JSONObject transform = com.alibaba.fastjson.JSON.parseObject(transformJson);
            Long functionId = transform.getLong("functionId");
            if (functionId == null) {
                throw new IllegalArgumentException("模型输出字段 " + field.getFieldName() + " 的转换函数缺少 functionId");
            }
            List<Object> args = new ArrayList<>();
            com.alibaba.fastjson.JSONArray operands = transform.getJSONArray("args");
            if (operands != null) {
                for (int i = 0; i < operands.size(); i++) {
                    com.alibaba.fastjson.JSONObject operand = operands.getJSONObject(i);
                    if (operand == null) {
                        throw new IllegalArgumentException("模型输出字段 " + field.getFieldName() + " 的转换参数 " + (i + 1) + " 未配置");
                    }
                    args.add(OperandValueResolver.resolve(
                            operand, context, referenceValues, this::invokeOperandFunction));
                }
            }
            String outputKey = outputKey(field, original);
            transformed.put(outputKey, ruleFunctionService.invoke(functionId, args));
        }
        return transformed;
    }

    private Map<String, Object> referenceValues(Long projectId, Map<String, Object> values) {
        if (variableService == null) return Collections.emptyMap();
        return OperandValueResolver.buildReferenceValues(
                variableService.buildRefScriptNameMap(projectId), values,
                variableService.buildRefConstantValueMap(projectId));
    }

    private Object invokeOperandFunction(Long functionId, String functionCode, List<Object> args) {
        return ruleFunctionService == null
                ? BuiltinFunctionInvoker.invoke(functionCode, args)
                : ruleFunctionService.invoke(functionId, args);
    }

    private String outputKey(RuleModelOutputField field, Map<String, Object> outputs) {
        if (field.getFieldName() != null && outputs.containsKey(field.getFieldName())) return field.getFieldName();
        if (field.getScriptName() != null && outputs.containsKey(field.getScriptName())) return field.getScriptName();
        if (field.getFeatureName() != null && outputs.containsKey(field.getFeatureName())) return field.getFeatureName();
        if (field.getFieldName() != null && !field.getFieldName().trim().isEmpty()) return field.getFieldName();
        if (field.getScriptName() != null && !field.getScriptName().trim().isEmpty()) return field.getScriptName();
        throw new IllegalArgumentException("模型输出字段缺少可写名称");
    }

    /**
     * 保存模型的测试参数（JSON）
     */
    public void saveTestParams(Long modelId, String testParams) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        Map<String, Object> config;
        if (model.getModelConfig() != null && !model.getModelConfig().isEmpty()) {
            try {
                config = com.alibaba.fastjson.JSON.parseObject(model.getModelConfig());
            } catch (Exception e) {
                config = new HashMap<>();
            }
        } else {
            config = new HashMap<>();
        }
        config.put("testParams", testParams);
        model.setModelConfig(com.alibaba.fastjson.JSON.toJSONString(config));
        modelMapper.updateById(model);
    }

    /**
     * 获取模型的测试参数（JSON）
     */
    public String getTestParams(Long modelId) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        if (model.getModelConfig() == null || model.getModelConfig().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> config = com.alibaba.fastjson.JSON.parseObject(model.getModelConfig());
            Object tp = config.get("testParams");
            return tp != null ? tp.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新模型输入字段（关联变量映射）
     */
    public void updateInputField(Long fieldId, RuleModelInputField field) {
        RuleModelInputField existing = inputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输入字段不存在");
        }
        existing.setVarId(field.getVarId());
        existing.setRefType(field.getRefType());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setDefaultValue(field.getDefaultValue());
        existing.setSourceOperand(field.getSourceOperand());
        existing.setDefaultOperand(field.getDefaultOperand());
        existing.setTransformType(field.getTransformType());
        existing.setTransformParams(field.getTransformParams());
        existing.setValidValues(field.getValidValues());
        inputFieldMapper.updateById(existing);
    }

    /**
     * 更新模型输出字段（关联变量映射）
     */
    public void updateOutputField(Long fieldId, RuleModelOutputField field) {
        RuleModelOutputField existing = outputFieldMapper.selectById(fieldId);
        if (existing == null) {
            throw new IllegalArgumentException("输出字段不存在");
        }
        RuleModel model = modelMapper.selectById(existing.getModelId());
        String transformError = validateTransformOperand(model, field.getFieldLabel(), existing.getFieldName(), field.getTransformOperand());
        if (transformError != null) throw new IllegalArgumentException(transformError);
        existing.setVarId(field.getVarId());
        existing.setRefType(field.getRefType());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setTargetField(field.getTargetField());
        existing.setTargetOperand(field.getTargetOperand());
        existing.setTransformOperand(field.getTransformOperand());
        outputFieldMapper.updateById(existing);
    }

    private String validateTransformOperand(RuleModel model, String fieldLabel, String fieldName, String operandJson) {
        if (operandJson == null || operandJson.trim().isEmpty()) return null;
        String displayName = fieldLabel != null && !fieldLabel.trim().isEmpty() ? fieldLabel : fieldName;
        final com.alibaba.fastjson.JSONObject operand;
        try {
            operand = com.alibaba.fastjson.JSON.parseObject(operandJson);
        } catch (Exception e) {
            return "输出字段「" + displayName + "」转换 Operand JSON 无效";
        }
        if (!"FUNCTION".equals(operand.getString("kind"))) {
            return "输出字段「" + displayName + "」转换方法必须引用函数";
        }
        Long functionId = operand.getLong("functionId");
        if (functionId == null) {
            return "输出字段「" + displayName + "」转换函数缺少 functionId";
        }
        RuleFunction function = ruleFunctionService == null ? null : ruleFunctionService.getById(functionId);
        if (function == null) {
            return "输出字段「" + displayName + "」引用的转换函数不存在";
        }
        if (!Integer.valueOf(1).equals(function.getStatus())) {
            return "输出字段「" + displayName + "」引用的转换函数已停用";
        }
        if (!functionInModelScope(model, function)) {
            return "输出字段「" + displayName + "」引用的转换函数不在模型作用域内";
        }

        int expectedCount;
        try {
            com.alibaba.fastjson.JSONArray params = function.getParamsJson() == null || function.getParamsJson().trim().isEmpty()
                    ? new com.alibaba.fastjson.JSONArray()
                    : com.alibaba.fastjson.JSON.parseArray(function.getParamsJson());
            expectedCount = params.size();
        } catch (Exception e) {
            return "输出字段「" + displayName + "」引用的转换函数参数定义无效";
        }
        com.alibaba.fastjson.JSONArray args = operand.getJSONArray("args");
        int actualCount = args == null ? 0 : args.size();
        if (actualCount != expectedCount) {
            return "输出字段「" + displayName + "」转换函数参数数量应为 " + expectedCount + "，实际为 " + actualCount;
        }
        for (int i = 0; i < actualCount; i++) {
            com.alibaba.fastjson.JSONObject arg = args.getJSONObject(i);
            if (arg == null || arg.getString("kind") == null) {
                return "输出字段「" + displayName + "」转换函数参数 " + (i + 1) + " 未配置";
            }
            String kind = arg.getString("kind");
            if ("FUNCTION".equals(kind)) {
                return "输出字段「" + displayName + "」转换函数参数不支持嵌套函数";
            }
            if (!"LITERAL".equals(kind) && !"PATH".equals(kind) && !"REFERENCE".equals(kind)) {
                return "输出字段「" + displayName + "」转换函数参数 " + (i + 1) + " 类型无效";
            }
            if ("PATH".equals(kind) && emptyText(arg.getString("value")) && emptyText(arg.getString("code"))) {
                return "输出字段「" + displayName + "」转换函数路径参数 " + (i + 1) + " 为空";
            }
            if ("REFERENCE".equals(kind) && (arg.getLong("refId") == null || emptyText(arg.getString("refType")))) {
                return "输出字段「" + displayName + "」转换函数引用参数 " + (i + 1) + " 未关联资源 ID";
            }
        }
        return null;
    }

    private boolean functionInModelScope(RuleModel model, RuleFunction function) {
        if (RuleFunctionService.SCOPE_GLOBAL.equals(function.getScope())) return true;
        return model != null
                && SCOPE_PROJECT.equals(model.getScope())
                && RuleFunctionService.SCOPE_PROJECT.equals(function.getScope())
                && Objects.equals(model.getProjectId(), function.getProjectId());
    }

    private boolean emptyText(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 将项目级模型转为全局模型
     */
    public void toGlobal(Long modelId, String newModelCode) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");
        if (SCOPE_GLOBAL.equals(model.getScope())) {
            throw new IllegalArgumentException("该模型已是全局模型，无需转换");
        }
        if (newModelCode == null || newModelCode.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写新的全局模型编码");
        }
        String trimmedCode = newModelCode.trim();
        if (existsModelCodeConflict(trimmedCode, SCOPE_GLOBAL, null, modelId)) {
            throw new IllegalArgumentException("该编码已被其他全局模型使用");
        }
        model.setScope(SCOPE_GLOBAL);
        model.setModelCode(trimmedCode);
        model.setProjectId(null);
        model.setProjectCode(null);
        model.setProjectName(null);
        modelMapper.updateById(model);
    }
}
