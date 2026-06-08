package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjjw.rule.model.entity.*;
import com.bjjw.rule.server.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.annotation.Resource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
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
            String changeLog, String testParams) {
        try {
            // 1. 读取文件内容（Base64）
            String modelContent = java.util.Base64.getEncoder().encodeToString(file.getBytes());

            // 2. 根据文件扩展名检测格式
            String modelFormat = detectFormat(file.getOriginalFilename());

            // 3. 解析模型（目前支持 PMML 自动解析，其他格式需手动配置字段）
            Map<String, Object> modelConfig = null;
            List<RuleModelInputField> inputFields = null;
            List<RuleModelOutputField> outputFields = null;

            // 如果传入了测试参数，初始化 modelConfig
            if (testParams != null && !testParams.isEmpty()) {
                modelConfig = new HashMap<>();
                modelConfig.put("testParams", testParams);
            }

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
     * 解析 PMML 文件，使用标准 DOM 解析
     */
    private PmmlParseResult parsePmml(String content) {
        PmmlParseResult result = new PmmlParseResult();
        result.setModelFormat("PMML");
        result.setModelType("ML");

        try {
            // 1. 使用 DOM 解析 PMML XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(content)));

            // 2. 检测模型类型
            result.setModelType(detectModelTypeFromDOM(doc));

            // 3. 提取输入字段
            result.setInputFields(extractInputFieldsFromDOM(doc));

            // 4. 提取输出字段
            result.setOutputFields(extractOutputFieldsFromDOM(doc));

            // 5. 提取模型配置
            result.setModelConfig(extractModelConfigFromDOM(doc));

        } catch (Exception e) {
            throw new RuntimeException("PMML解析失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 从 DOM 提取输入字段（DataField + MiningSchema）
     */
    private java.util.List<RuleModelInputField> extractInputFieldsFromDOM(Document doc) {
        java.util.List<RuleModelInputField> fields = new java.util.ArrayList<>();
        int sortOrder = 0;

        // 构建 DataField map：name -> element attributes
        Map<String, Map<String, String>> dataFieldMap = new java.util.LinkedHashMap<>();
        NodeList dataFieldNodes = doc.getElementsByTagName("DataField");
        for (int i = 0; i < dataFieldNodes.getLength(); i++) {
            Element df = (Element) dataFieldNodes.item(i);
            String name = df.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                Map<String, String> attrs = new java.util.HashMap<>();
                attrs.put("name", name);
                attrs.put("displayName", df.getAttribute("displayName"));
                attrs.put("dataType", df.getAttribute("dataType"));
                attrs.put("optype", df.getAttribute("optype"));
                attrs.put("missingValueStrategy", df.getAttribute("missingValueStrategy"));
                dataFieldMap.put(name, attrs);
            }
        }

        // 构建 MiningField map：name -> element attributes（区分 active / target）
        Set<String> activeFieldNames = new java.util.HashSet<>();
        Set<String> targetFieldNames = new java.util.HashSet<>();
        Map<String, Map<String, String>> miningFieldMap = new java.util.LinkedHashMap<>();
        NodeList miningSchemaNodes = doc.getElementsByTagName("MiningSchema");
        for (int i = 0; i < miningSchemaNodes.getLength(); i++) {
            Element miningSchema = (Element) miningSchemaNodes.item(i);
            NodeList miningFieldNodes = miningSchema.getElementsByTagName("MiningField");
            for (int j = 0; j < miningFieldNodes.getLength(); j++) {
                Element mf = (Element) miningFieldNodes.item(j);
                String name = mf.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    String usageType = mf.getAttribute("usageType");
                    String importanceStr = mf.getAttribute("importance");
                    Map<String, String> attrs = new java.util.HashMap<>();
                    attrs.put("name", name);
                    attrs.put("usageType", usageType);
                    attrs.put("importance", importanceStr);
                    miningFieldMap.put(name, attrs);
                    if ("active".equalsIgnoreCase(usageType) || usageType == null || usageType.isEmpty()) {
                        activeFieldNames.add(name);
                    }
                    if ("target".equalsIgnoreCase(usageType)) {
                        targetFieldNames.add(name);
                    }
                }
            }
        }

        // 构建 LocalTransformations map：name -> preprocess steps
        Map<String, java.util.List<Map<String, String>>> localTransMap = new java.util.LinkedHashMap<>();
        NodeList ltNodes = doc.getElementsByTagName("LocalTransformations");
        for (int i = 0; i < ltNodes.getLength(); i++) {
            Element lt = (Element) ltNodes.item(i);
            NodeList derivedFieldNodes = lt.getElementsByTagName("DerivedField");
            for (int j = 0; j < derivedFieldNodes.getLength(); j++) {
                Element df = (Element) derivedFieldNodes.item(j);
                String name = df.getAttribute("name");
                String optype = df.getAttribute("optype");
                String dataType = df.getAttribute("dataType");
                if (name != null && !name.isEmpty()) {
                    Map<String, String> derived = new java.util.HashMap<>();
                    derived.put("name", name);
                    derived.put("optype", optype);
                    derived.put("dataType", dataType);
                    // 存储到 input fields 作为预处理信息
                    java.util.List<Map<String, String>> list = localTransMap.computeIfAbsent(name, k -> new java.util.ArrayList<>());
                    list.add(derived);
                }
            }
        }

        // 优先使用 MiningSchema 中的 active 字段作为输入
        boolean hasMiningSchema = !activeFieldNames.isEmpty();
        if (!hasMiningSchema) {
            // 如果没有 MiningSchema，使用所有 DataField
            activeFieldNames.addAll(dataFieldMap.keySet());
        }

        for (String fieldName : activeFieldNames) {
            if (targetFieldNames.contains(fieldName)) continue; // 跳过目标字段

            Map<String, String> dfAttrs = dataFieldMap.get(fieldName);
            Map<String, String> mfAttrs = miningFieldMap.get(fieldName);

            RuleModelInputField field = new RuleModelInputField();
            field.setFieldName(fieldName);
            field.setScriptName(fieldName);

            // displayName 或 name
            String displayName = dfAttrs != null ? dfAttrs.get("displayName") : null;
            field.setFieldLabel(displayName != null && !displayName.isEmpty() ? displayName : fieldName);

            // dataType 映射
            String dataType = dfAttrs != null ? dfAttrs.get("dataType") : null;
            field.setFieldType(mapDataType(dataType));

            // optype（CONTINUOUS / CATEGORICAL / ORDINAL）
            String optype = dfAttrs != null ? dfAttrs.get("optype") : null;
            field.setDataType(optype != null && !optype.isEmpty() ? optype.toUpperCase() : "CONTINUOUS");

            // 缺失值处理策略
            String missingStrategy = dfAttrs != null ? dfAttrs.get("missingValueStrategy") : null;
            if (missingStrategy != null && !missingStrategy.isEmpty()) {
                field.setMissingValue(missingStrategy);
            }

            // 有效值列表（从 DataField 的 Value 子元素提取）
            NodeList valueNodes = null;
            if (dfAttrs != null) {
                // 需要重新找 DataField 元素来获取 Value 子元素
                for (int i = 0; i < dataFieldNodes.getLength(); i++) {
                    Element df = (Element) dataFieldNodes.item(i);
                    if (fieldName.equals(df.getAttribute("name"))) {
                        valueNodes = df.getElementsByTagName("Value");
                        break;
                    }
                }
            }
            java.util.List<String> validVals = new java.util.ArrayList<>();
            if (valueNodes != null) {
                for (int i = 0; i < valueNodes.getLength(); i++) {
                    Element val = (Element) valueNodes.item(i);
                    String valStr = val.getAttribute("value");
                    if (valStr != null && !valStr.isEmpty()) {
                        validVals.add(valStr);
                    }
                }
            }
            if (!validVals.isEmpty()) {
                field.setValidValues(com.alibaba.fastjson.JSON.toJSONString(validVals));
                // 取第一个有效值作为默认值
                field.setDefaultValue(validVals.get(0));
            }

            // 区间范围（从 Interval 子元素提取）
            NodeList intervalNodes = null;
            if (dfAttrs != null) {
                for (int i = 0; i < dataFieldNodes.getLength(); i++) {
                    Element df = (Element) dataFieldNodes.item(i);
                    if (fieldName.equals(df.getAttribute("name"))) {
                        intervalNodes = df.getElementsByTagName("Interval");
                        break;
                    }
                }
            }
            java.util.List<String> intervals = new java.util.ArrayList<>();
            if (intervalNodes != null) {
                for (int i = 0; i < intervalNodes.getLength(); i++) {
                    Element interval = (Element) intervalNodes.item(i);
                    intervals.add(formatIntervalFromDOM(interval));
                }
            }
            if (!intervals.isEmpty()) {
                field.setTransformParams(com.alibaba.fastjson.JSON.toJSONString(intervals));
            }

            // importance score
            if (mfAttrs != null) {
                String importanceStr = mfAttrs.get("importance");
                if (importanceStr != null && !importanceStr.isEmpty()) {
                    try {
                        field.setImportanceScore(new BigDecimal(importanceStr));
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 预处理信息
            if (localTransMap.containsKey(fieldName)) {
                field.setTransformType("DERIVED");
                field.setTransformParams(com.alibaba.fastjson.JSON.toJSONString(localTransMap.get(fieldName)));
            } else {
                field.setTransformType("NONE");
            }

            field.setSortOrder(sortOrder++);
            fields.add(field);
        }

        return fields;
    }

    /**
     * 从 DOM 提取输出字段（OutputField）
     */
    private java.util.List<RuleModelOutputField> extractOutputFieldsFromDOM(Document doc) {
        java.util.List<RuleModelOutputField> fields = new java.util.ArrayList<>();
        int sortOrder = 0;

        // 优先从 Output 元素中提取 OutputField（这是 PMML 标准输出定义）
        NodeList outputNodes = doc.getElementsByTagName("Output");
        if (outputNodes.getLength() > 0) {
            Element output = (Element) outputNodes.item(0);
            NodeList outputFieldNodes = output.getElementsByTagName("OutputField");
            for (int i = 0; i < outputFieldNodes.getLength(); i++) {
                Element of = (Element) outputFieldNodes.item(i);
                fields.add(buildOutputField(of, sortOrder++));
            }
        }

        // 如果没有 Output 元素，从模型根元素直接获取 OutputField（某些简化格式）
        if (fields.isEmpty()) {
            // 遍历所有 OutputField（可能在模型元素下）
            NodeList allOutputFieldNodes = doc.getElementsByTagName("OutputField");
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < allOutputFieldNodes.getLength(); i++) {
                Element of = (Element) allOutputFieldNodes.item(i);
                // 跳过已经在 Output 元素中的
                if (seen.contains(of.getAttribute("name"))) continue;
                seen.add(of.getAttribute("name"));
                fields.add(buildOutputField(of, sortOrder++));
            }
        }

        // 如果还是没有，从模型元素中找 target 字段作为隐式输出
        if (fields.isEmpty()) {
            NodeList miningSchemaNodes = doc.getElementsByTagName("MiningSchema");
            for (int i = 0; i < miningSchemaNodes.getLength(); i++) {
                Element miningSchema = (Element) miningSchemaNodes.item(i);
                NodeList miningFieldNodes = miningSchema.getElementsByTagName("MiningField");
                for (int j = 0; j < miningFieldNodes.getLength(); j++) {
                    Element mf = (Element) miningFieldNodes.item(j);
                    if ("target".equalsIgnoreCase(mf.getAttribute("usageType"))) {
                        RuleModelOutputField field = new RuleModelOutputField();
                        field.setFieldName(mf.getAttribute("name"));
                        field.setFieldLabel(mf.getAttribute("name"));
                        field.setFieldType("STRING");
                        field.setIsProbability(0);
                        field.setSortOrder(sortOrder++);
                        fields.add(field);
                    }
                }
            }
        }

        return fields;
    }

    /**
     * 从单个 OutputField DOM 元素构建输出字段对象
     */
    private RuleModelOutputField buildOutputField(Element of, int sortOrder) {
        RuleModelOutputField field = new RuleModelOutputField();
        String name = of.getAttribute("name");
        String displayName = of.getAttribute("displayName");
        field.setFieldName(name);
        field.setFieldLabel(displayName != null && !displayName.isEmpty() ? displayName : name);

        // dataType
        field.setFieldType(mapDataType(of.getAttribute("dataType")));

        // targetField
        String targetField = of.getAttribute("targetField");
        if (targetField != null && !targetField.isEmpty()) {
            field.setTargetField(targetField);
        }

        // feature - 判断是否为概率输出
        String feature = of.getAttribute("feature");
        if (feature != null && !feature.isEmpty()) {
            field.setFeatureName(feature);
            if ("probability".equalsIgnoreCase(feature)) {
                field.setIsProbability(1);
            } else {
                field.setIsProbability(0);
            }
        } else {
            field.setIsProbability(0);
        }

        // category（类别标签）
        String value = of.getAttribute("value");
        if (value != null && !value.isEmpty()) {
            field.setCategory(value);
        }

        field.setSortOrder(sortOrder);
        return field;
    }

    /**
     * 从 DOM 提取模型配置信息
     */
    private java.util.Map<String, Object> extractModelConfigFromDOM(Document doc) {
        java.util.Map<String, Object> config = new java.util.LinkedHashMap<>();

        // PMML Header
        NodeList headerNodes = doc.getElementsByTagName("Header");
        if (headerNodes.getLength() > 0) {
            Element header = (Element) headerNodes.item(0);
            String description = header.getAttribute("description");
            String copyright = header.getAttribute("copyright");
            String appName = header.getAttribute("appName");
            if (description != null && !description.isEmpty()) config.put("description", description);
            if (copyright != null && !copyright.isEmpty()) config.put("copyright", copyright);
            if (appName != null && !appName.isEmpty()) config.put("appName", appName);
        }

        // 模型基本信息（从第一个模型元素）
        String[] modelTags = {"RegressionModel", "TreeModel", "NeuralNetwork", "GeneralRegressionModel",
                "NaiveBayesModel", "SupportVectorMachineModel", "RandomForestModel"};
        for (String tag : modelTags) {
            NodeList modelNodes = doc.getElementsByTagName(tag);
            if (modelNodes.getLength() > 0) {
                Element model = (Element) modelNodes.item(0);
                config.put("modelName", model.getAttribute("modelName"));
                config.put("functionName", model.getAttribute("functionName"));
                config.put("algorithmName", model.getAttribute("algorithmName"));
                config.put("modelTag", tag);
                break;
            }
        }

        return config;
    }

    /**
     * 从 DOM 检测模型类型
     */
    private String detectModelTypeFromDOM(Document doc) {
        if (hasTag(doc, "XGBoostTreeModel") || hasTag(doc, "XGBoostRegressionModel")) {
            return "XGBOOST";
        }
        if (hasTag(doc, "LightGBMModel")) {
            return "LIGHTGBM";
        }
        if (hasTag(doc, "RegressionModel")) {
            Element model = getFirstElement(doc, "RegressionModel");
            if (model != null && "classification".equalsIgnoreCase(model.getAttribute("functionName"))) {
                return "LR";
            }
            return "LR";
        }
        if (hasTag(doc, "TreeModel")) {
            return "TREE";
        }
        if (hasTag(doc, "NeuralNetwork")) {
            return "NEURAL_NET";
        }
        if (hasTag(doc, "SupportVectorMachineModel")) {
            return "SVM";
        }
        if (hasTag(doc, "RandomForestModel")) {
            return "RANDOM_FOREST";
        }
        if (hasTag(doc, "NaiveBayesModel")) {
            return "NAIVE_BAYES";
        }
        if (hasTag(doc, "GeneralRegressionModel")) {
            return "GLM";
        }
        if (hasTag(doc, "MiningModel")) {
            return "MINING";
        }
        return "ML";
    }

    private boolean hasTag(Document doc, String tag) {
        return doc.getElementsByTagName(tag).getLength() > 0;
    }

    private Element getFirstElement(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        return null;
    }

    /**
     * 格式化区间为字符串
     */
    private String formatIntervalFromDOM(Element interval) {
        StringBuilder sb = new StringBuilder();
        String leftMargin = interval.getAttribute("leftMargin");
        String rightMargin = interval.getAttribute("rightMargin");
        String closure = interval.getAttribute("closure");
        if (leftMargin != null && !leftMargin.isEmpty()) {
            sb.append(leftMargin).append(" < ");
        }
        if (closure != null && closure.contains("closedClosed")) {
            sb.append("[x]");
        } else if (closure != null && closure.contains("closedOpen")) {
            sb.append("(x]");
        } else if (closure != null && closure.contains("openClosed")) {
            sb.append("[x)");
        } else {
            sb.append("(x)");
        }
        if (rightMargin != null && !rightMargin.isEmpty()) {
            sb.append(" < ").append(rightMargin);
        }
        return sb.toString();
    }

    /**
     * 映射 PMML DataType 到标准数据类型
     */
    private String mapDataType(String dataType) {
        if (dataType == null) return "STRING";
        String dt = dataType.toUpperCase();
        switch (dt) {
            case "DOUBLE":
            case "FLOAT":
            case "NUMBER":
                return "DOUBLE";
            case "INTEGER":
            case "LONG":
            case "INT":
                return "INTEGER";
            case "BOOLEAN":
                return "BOOLEAN";
            case "DATE":
            case "DATE_TIME":
            case "DATETIME":
            case "TIME":
                return "DATE";
            case "STRING":
            case "TEXT":
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

    /**
     * 执行模型测试
     * @param modelId 模型ID
     * @param params 输入参数
     * @return 执行结果（Map）
     */
    public Map<String, Object> execute(Long modelId, Map<String, Object> params) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        if (model.getModelContent() == null || model.getModelContent().isEmpty()) {
            throw new IllegalArgumentException("模型文件内容为空");
        }

        String format = model.getModelFormat();
        if ("PMML".equals(format)) {
            return executePmml(model, params);
        }

        // 其他格式暂不支持，给出提示
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", format + " 格式暂不支持在线执行，仅 PMML 格式支持测试");
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", format);
        result.put("inputParams", params);
        return result;
    }

    /**
     * 执行 PMML 模型
     */
    private Map<String, Object> executePmml(RuleModel model, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("modelCode", model.getModelCode());
        result.put("modelFormat", "PMML");

        try {
            // 1. 解码 Base64
            byte[] contentBytes = Base64.getDecoder().decode(model.getModelContent());
            String pmmlContent = new String(contentBytes, StandardCharsets.UTF_8);

            // 2. 解析 PMML XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(pmmlContent.getBytes(StandardCharsets.UTF_8)));

            // 3. 识别模型类型并执行
            Element root = doc.getDocumentElement();
            String modelName = root.getAttribute("modelName");
            result.put("modelName", modelName);

            // 检查是否有 RegressionModel
            NodeList regressionNodes = doc.getElementsByTagName("RegressionModel");
            NodeList treeNodes = doc.getElementsByTagName("TreeModel");
            NodeList neuralNodes = doc.getElementsByTagName("NeuralNetwork");

            if (regressionNodes.getLength() > 0) {
                Element regModel = (Element) regressionNodes.item(0);
                executeRegressionModel(regModel, params, result);
            } else if (treeNodes.getLength() > 0) {
                Element treeModel = (Element) treeNodes.item(0);
                executeTreeModel(treeModel, params, result);
            } else if (neuralNodes.getLength() > 0) {
                Element nnModel = (Element) neuralNodes.item(0);
                executeNeuralNetworkModel(nnModel, params, result);
            } else {
                // 通用：提取所有 OutputField 名称作为结果字段
                NodeList outputFields = doc.getElementsByTagName("OutputField");
                Map<String, Object> outputs = new LinkedHashMap<>();
                for (int i = 0; i < outputFields.getLength(); i++) {
                    Element of = (Element) outputFields.item(i);
                    String name = of.getAttribute("name");
                    String dataType = of.getAttribute("dataType");
                    String optype = of.getAttribute("optype");
                    if (name != null && !name.isEmpty()) {
                        // 对于未知输出字段，尝试从参数中映射
                        Object val = params.get(name);
                        outputs.put(name, val != null ? val : "（待计算）");
                    }
                }
                result.put("success", true);
                result.put("outputs", outputs);
                result.put("note", "检测到模型类型，输出字段已返回。完整预测需要 JPMML 库支持。");
            }

            result.put("success", true);
            result.put("inputParams", params);
            result.put("executeTimeMs", System.currentTimeMillis() - startTime);

        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "PMML 执行异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 执行 PMML RegressionModel（线性回归/逻辑回归）
     */
    private void executeRegressionModel(Element regModel, Map<String, Object> params, Map<String, Object> result) {
        String functionName = regModel.getAttribute("functionName");
        result.put("modelType", "RegressionModel");
        result.put("functionName", functionName);

        // 提取输入字段（NormalizationSchema）
        NodeList paramList = regModel.getElementsByTagName("Parameter");
        Map<String, Double> paramMap = new HashMap<>();
        for (int i = 0; i < paramList.getLength(); i++) {
            Element param = (Element) paramList.item(i);
            paramMap.put(param.getAttribute("name"), parseDouble(param.getAttribute("value")));
        }

        // 提取回归表
        NodeList tableList = regModel.getElementsByTagName("RegressionTable");
        Map<String, Object> outputs = new LinkedHashMap<>();

        if (tableList.getLength() > 0) {
            Element table = (Element) tableList.item(0);
            String targetField = regModel.getAttribute("targetFieldName");
            NodeList numPredictors = table.getElementsByTagName("NumericPredictor");

            double score = 0.0;
            for (int i = 0; i < numPredictors.getLength(); i++) {
                Element np = (Element) numPredictors.item(i);
                String pname = np.getAttribute("name");
                double coefficient = parseDouble(np.getAttribute("coefficient"));
                Object inputVal = params.get(pname);
                if (inputVal != null) {
                    score += coefficient * toDouble(inputVal);
                }
            }

            // 加上截距
            NodeList intercepts = table.getElementsByTagName("Intercept");
            if (intercepts.getLength() > 0) {
                Element intercept = (Element) intercepts.item(0);
                score += parseDouble(intercept.getAttribute("value"));
            }

            if ("classification".equals(functionName)) {
                // 逻辑回归：计算概率
                double expScore = Math.exp(-score);
                double probability = 1.0 / (1.0 + expScore);
                outputs.put(targetField + "_score", score);
                outputs.put(targetField + "_probability", probability);
                outputs.put(targetField + "_class", probability > 0.5 ? "1" : "0");
            } else {
                outputs.put(targetField, score);
            }
        }

        result.put("outputs", outputs);
    }

    /**
     * 执行 PMML TreeModel（决策树）
     */
    private void executeTreeModel(Element treeModel, Map<String, Object> params, Map<String, Object> result) {
        String targetField = treeModel.getAttribute("targetField");
        result.put("modelType", "TreeModel");
        result.put("targetField", targetField);

        NodeList nodeList = treeModel.getElementsByTagName("Node");
        Map<String, Object> outputs = new LinkedHashMap<>();

        // 简单实现：找到匹配的叶节点
        String predictedValue = findTreePrediction(treeModel, params);
        outputs.put(targetField, predictedValue != null ? predictedValue : "未知");
        result.put("outputs", outputs);
    }

    private String findTreePrediction(Element treeModel, Map<String, Object> params) {
        // 递归查找叶节点
        NodeList childNodes = treeModel.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                Element child = (Element) childNodes.item(i);
                String tagName = child.getTagName();
                if ("Node".equals(tagName)) {
                    String predicted = evaluateNode(child, params);
                    if (predicted != null) return predicted;
                }
            }
        }
        return null;
    }

    private String evaluateNode(Element node, Map<String, Object> params) {
        // 检查是否为叶节点
        NodeList children = node.getChildNodes();
        boolean hasScore = false;
        boolean isNode = false;
        String score = null;

        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element child = (Element) children.item(i);
                String tag = child.getTagName();
                if ("Score".equals(tag)) {
                    score = child.getTextContent();
                    hasScore = true;
                } else if ("Node".equals(tag)) {
                    isNode = true;
                    String predicted = evaluateNode(child, params);
                    if (predicted != null) return predicted;
                }
            }
        }

        // 检查是否满足此节点的谓词条件
        String predicate = node.getAttribute("predicate");
        if (predicate != null && !predicate.isEmpty()) {
            // 简化谓词评估
            if (!evaluatePredicate(predicate, params)) {
                return null;
            }
        }

        if (hasScore) {
            return score != null ? score.trim() : null;
        }
        if (!isNode) {
            // 叶节点但无 score，取 defaultValue
            return node.getAttribute("defaultValue");
        }
        return null;
    }

    private boolean evaluatePredicate(String predicate, Map<String, Object> params) {
        // 简化谓词评估：检查参数值
        // 谓词格式如: "[age] > 30" 或 "not [income] < 5000"
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(not\\s+)?\\[(\\w+)\\]\\s*(<|>|=|<=|>=)\\s*([\\d.]+)");
            java.util.regex.Matcher m = p.matcher(predicate.trim());
            while (m.find()) {
                boolean not = m.group(1) != null;
                String field = m.group(2);
                String op = m.group(3);
                double threshold = parseDouble(m.group(4));
                Object val = params.get(field);
                if (val == null) return false;
                double v = toDouble(val);
                boolean matched;
                switch (op) {
                    case ">": matched = v > threshold; break;
                    case "<": matched = v < threshold; break;
                    case ">=": matched = v >= threshold; break;
                    case "<=": matched = v <= threshold; break;
                    default: matched = v == threshold;
                }
                if (not) matched = !matched;
                if (!matched) return false;
            }
            return true;
        } catch (Exception e) {
            return true; // 解析失败，保守返回 true
        }
    }

    /**
     * 执行 PMML NeuralNetwork（神经网络）
     */
    private void executeNeuralNetworkModel(Element nnModel, Map<String, Object> params, Map<String, Object> result) {
        result.put("modelType", "NeuralNetwork");
        result.put("note", "神经网络模型检测到，完整前向传播需要 JPMML 库支持。当前返回参数映射。");

        // 提取所有 NeuralInput 和 NeuralOutput
        Map<String, Object> outputs = new LinkedHashMap<>();
        NodeList inputLayers = nnModel.getElementsByTagName("NeuralInput");
        NodeList outputLayers = nnModel.getElementsByTagName("NeuralOutput");

        for (int i = 0; i < outputLayers.getLength(); i++) {
            Element output = (Element) outputLayers.item(i);
            String field = output.getAttribute("output");
            Object val = params.get(field);
            outputs.put(field, val != null ? val : "（待计算）");
        }

        result.put("outputs", outputs);
    }

    private double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString().trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    /**
     * 保存模型的测试参数（JSON）
     */
    public void saveTestParams(Long modelId, String testParams) {
        RuleModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        // 合并到 modelConfig 中
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
            Object testParams = config.get("testParams");
            return testParams != null ? testParams.toString() : null;
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
        // 更新关联映射相关字段
        existing.setVarId(field.getVarId());
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
        // 更新关联映射相关字段
        existing.setVarId(field.getVarId());
        existing.setScriptName(field.getScriptName());
        existing.setFieldLabel(field.getFieldLabel());
        existing.setFieldType(field.getFieldType());
        existing.setTransformType(field.getTransformType());
        existing.setTargetField(field.getTargetField());
        outputFieldMapper.updateById(existing);
    }
}