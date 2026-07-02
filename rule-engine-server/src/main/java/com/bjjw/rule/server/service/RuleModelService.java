package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjjw.rule.core.pmml.PMMLModelExecutor;
import com.bjjw.rule.model.entity.*;
import com.bjjw.rule.server.mapper.*;
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
        try {
            String modelContent = Base64.getEncoder().encodeToString(file.getBytes());

            String modelFormat = detectFormat(file.getOriginalFilename());

            Map<String, Object> modelConfig = null;
            List<RuleModelInputField> inputFields = null;
            List<RuleModelOutputField> outputFields = null;

            if (testParams != null && !testParams.isEmpty()) {
                modelConfig = new HashMap<>();
                modelConfig.put("testParams", testParams);
            }

            if ("PMML".equals(modelFormat)) {
                String rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                PmmlParseResult result = parsePmml(rawContent);
                modelConfig = result.getModelConfig();
                inputFields = result.getInputFields();
                outputFields = result.getOutputFields();
                if (modelType == null || modelType.isEmpty()) {
                    modelType = result.getModelType();
                }
            }

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
            if (modelConfig != null) {
                model.setModelConfig(com.alibaba.fastjson.JSON.toJSONString(modelConfig));
            }
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
     * 更新模型元信息（不包括文件内容和编码/项目等核心字段）
     */
    public void update(RuleModel model) {
        RuleModel existing = modelMapper.selectById(model.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        // 只更新允许变更的字段，避免覆盖文件内容等核心数据
        existing.setModelName(model.getModelName());
        existing.setDescription(model.getDescription());
        existing.setTargetCategories(model.getTargetCategories());
        existing.setModelVersion(model.getModelVersion());
        if (model.getStatus() != null) {
            existing.setStatus(model.getStatus());
        }
        modelMapper.updateById(existing);
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
        if (lower.endsWith(".txt")) return "LIGHTGBM";
        return "PMML";
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
    public Map<String, Object> execute(Long modelId, Map<String, Object> params) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        if (model.getModelContent() == null || model.getModelContent().isEmpty()) {
            throw new IllegalArgumentException("模型文件内容为空");
        }

        if ("PMML".equals(model.getModelFormat())) {
            return executePmml(model, params);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", model.getModelFormat() + " 格式暂不支持在线执行，仅 PMML 格式支持测试");
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", model.getModelFormat());
        result.put("inputParams", params);
        return result;
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
            Map<String, Object> outputs = pmmlExecutor.evaluate(model.getModelContent(), params);
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
        existing.setMissingValue(field.getMissingValue());
        existing.setDefaultValue(field.getDefaultValue());
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
        existing.setVarId(field.getVarId());
        existing.setRefType(field.getRefType());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setTransformType(field.getTransformType());
        existing.setTargetField(field.getTargetField());
        outputFieldMapper.updateById(existing);
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
