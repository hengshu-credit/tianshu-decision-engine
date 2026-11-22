package com.hengshucredit.rule.server.testdata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.hengshucredit.rule.model.dto.*;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import com.hengshucredit.rule.server.service.RuleTestTargetResolver;
import com.hengshucredit.rule.server.service.RuleTestSchemaService;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestMockService {
    private final RuleTestTargetResolver targets;
    private final RuleTestSchemaService schemas;
    private final ConsoleResourceAccess access;
    private final TestDataCatalog catalog;
    private final MockFieldBindings methodBindings;
    private final RuleVariableMapper variables;
    private final RuleDataObjectMapper objects;
    private final RuleDataObjectFieldMapper fields;
    private final RuleVariableOptionMapper variableOptions;
    private final RuleDataObjectFieldOptionMapper fieldOptions;
    public record Sample(String paramsJson, RuleTestExecutionIdentity executionIdentity) { }

    @Transactional(readOnly = true)
    public Sample generate(long id, RuleTestSchemaRequest request) {
        access.require("RULE",id,"view");
        var target = targets.resolve(id,request.getExecutionSource(),null,null,request.getProjectId());
        return new Sample(examples(schemas.build(target).getInputs(), target.snapshot(), target.projectId()).toString(), target.identity());
    }

    public ObjectNode examples(List<ResolvedField> inputs, com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot, Long projectId) {
        if (inputs.isEmpty()) return MockRule.JSON.createObjectNode();
        var varIds = ids(inputs,"VARIABLE");
        var rootIds = ids(inputs,"DATA_OBJECT_ROOT");
        var fieldIds = ids(inputs,"DATA_OBJECT");
        List<RuleDataObjectField> members;
        List<RuleDataObject> roots;
        if (snapshot != null) {
            members = snapshot.getDataObjectFields();
            roots = snapshot.getDataObjects();
        } else {
            if (!fieldIds.isEmpty()) fields.selectByIds(fieldIds).forEach(f -> rootIds.add(f.getObjectId()));
            roots = rootIds.isEmpty() ? List.of() : objects.selectByIds(rootIds);
            members = rootIds.isEmpty() ? List.of() : fields.selectList(new LambdaQueryWrapper<RuleDataObjectField>()
                    .in(RuleDataObjectField::getObjectId,rootIds).eq(RuleDataObjectField::getStatus,1));
        }
        var rootMap = byId(roots,RuleDataObject::getId);
        var fieldMap = byId(members,RuleDataObjectField::getId);
        members.stream().map(RuleDataObjectField::getRefVariableId).filter(Objects::nonNull).forEach(varIds::add);
        // 冻结版本提供字段结构。本机资源使用当前获准的测试配置，外部制品中的 ID 属于另一命名空间。
        boolean local = snapshot == null || !snapshot.isImported();
        var boundVars = local && !varIds.isEmpty() ? variables.selectByIds(varIds) : List.<RuleVariable>of();
        var boundRoots = snapshot == null ? roots : local && !rootMap.isEmpty() ? objects.selectByIds(rootMap.keySet()) : List.<RuleDataObject>of();
        var boundFields = snapshot == null ? members : local && !fieldMap.isEmpty() ? fields.selectByIds(fieldMap.keySet()) : List.<RuleDataObjectField>of();
        Map<Long,String> varEnums = Map.of(), fieldEnums = Map.of();
        if (snapshot == null) {
            var enumVars = inputs.stream().filter(f -> "ENUM".equals(f.getValueType()) && "VARIABLE".equals(f.getRefType()))
                    .map(ResolvedField::getRefId).filter(Objects::nonNull).toList();
            if (!enumVars.isEmpty()) varEnums = optionsByOwner(variableOptions.selectList(new LambdaQueryWrapper<RuleVariableOption>()
                    .in(RuleVariableOption::getVariableId,enumVars)),RuleVariableOption::getVariableId,RuleVariableOption::getOptionValue);
            var enumFields = members.stream().filter(f -> "ENUM".equals(f.getVarType())).map(RuleDataObjectField::getId).toList();
            if (!enumFields.isEmpty()) fieldEnums = optionsByOwner(fieldOptions.selectList(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                    .in(RuleDataObjectFieldOption::getFieldId,enumFields)),RuleDataObjectFieldOption::getFieldId,RuleDataObjectFieldOption::getOptionValue);
        }
        var bindings = new Bindings(settings(MockFieldBindings.Target.VARIABLE,boundVars,RuleVariable::getId,RuleVariable::getExampleMode,RuleVariable::getExampleValue,RuleVariable::getMockRuleId),
                settings(MockFieldBindings.Target.DATA_OBJECT_ROOT,boundRoots,RuleDataObject::getId,RuleDataObject::getExampleMode,RuleDataObject::getExampleValue,RuleDataObject::getMockRuleId),
                settings(MockFieldBindings.Target.DATA_OBJECT,boundFields,RuleDataObjectField::getId,RuleDataObjectField::getExampleMode,RuleDataObjectField::getExampleValue,RuleDataObjectField::getMockRuleId),fieldEnums);
        var rules = catalog.rulesForProject(projectId);
        var result = MockRule.JSON.createObjectNode();
        Map<Long,JsonNode> samples = new HashMap<>();
        for (ResolvedField input : inputs) {
            String path = input.getScriptName();
            if (path == null || path.isBlank()) continue;
            Long ref = input.getRefId();
            RuleDataObjectField member = "DATA_OBJECT".equals(input.getRefType()) ? fieldMap.get(ref) : null;
            RuleDataObject root = member != null ? rootMap.get(member.getObjectId())
                    : "DATA_OBJECT_ROOT".equals(input.getRefType()) ? rootMap.get(ref) : null;
            JsonNode value;
            if (root != null) {
                JsonNode object = samples.computeIfAbsent(root.getId(), ignored ->
                        objectSample(root,members,rootMap,fieldMap,snapshot == null ? Map.of() : snapshot.getReferencePaths(),rules,bindings));
                if (object.isMissingNode()) continue;
                if (member == null) value = object;
                else {
                    String relative = relativePath(root,member,rootMap,fieldMap,snapshot == null ? Map.of() : snapshot.getReferencePaths());
                    if (bindings.roots().getOrDefault(root.getId(),ExampleSetting.AUTO).configured() && path.endsWith("."+relative)) {
                        writePath(result,path.substring(0,path.length()-relative.length()-1),object,true);
                        continue;
                    }
                    value = object;
                    StringBuilder prefix = new StringBuilder(path.endsWith(relative) ? path.substring(0,path.length()-relative.length()) : "");
                    for (String part : relative.split("\\.")) {
                        if (value == null || value.isArray()) break;
                        value = value.get(part);
                        prefix.append(part).append('.');
                    }
                    if (value != null && value.isArray() && !prefix.isEmpty()) path = prefix.substring(0,prefix.length()-1);
                    if (value == null) continue;
                }
            } else {
                ExampleSetting binding = ref == null ? null : switch (Objects.toString(input.getRefType(),"")) {
                    case "VARIABLE" -> bindings.variables().get(ref);
                    case "DATA_OBJECT" -> bindings.fields().get(ref);
                    default -> null;
                };
                String options = input.getValidValues();
                if ((options == null || options.isBlank() || "[]".equals(options)) && ref != null && "VARIABLE".equals(input.getRefType()))
                    options = varEnums.get(ref);
                if (binding == null) binding = ExampleSetting.resolve(null,input.getExampleValue(),null,null);
                value = binding.value(input.getValueType(),path,input.getLabel(),options,rules,input.getDefaultValue());
            }
            if (!value.isMissingNode()) writePath(result,path,value,true);
        }
        return result;
    }
    record Bindings(Map<Long,ExampleSetting> variables, Map<Long,ExampleSetting> roots, Map<Long,ExampleSetting> fields, Map<Long,String> fieldEnums) { }
    private static <T> Map<Long,String> optionsByOwner(List<T> rows,Function<T,Long> owner,Function<T,String> value) {
        return rows.stream().collect(Collectors.groupingBy(owner,Collectors.mapping(value,Collectors.collectingAndThen(
                Collectors.toList(),list -> MockRule.JSON.valueToTree(list).toString()))));
    }
    private <T> Map<Long,ExampleSetting> settings(MockFieldBindings.Target target,List<T> rows,Function<T,Long> id,
            Function<T,String> mode,Function<T,String> fixed,Function<T,Long> method) {
        var matches = methodBindings.matches(target,rows.stream().map(id).toList());
        Map<Long,ExampleSetting> settings = new HashMap<>();
        for (T row : rows) {
            Long key = id.apply(row);
            var setting = ExampleSetting.resolve(mode.apply(row),fixed.apply(row),method.apply(row),matches.get(key));
            if (setting.configured() || mode.apply(row) != null) settings.put(key,setting);
        }
        return settings;
    }
    private static Set<Long> ids(List<ResolvedField> inputs,String type) {
        return inputs.stream().filter(f -> type.equals(f.getRefType())).map(ResolvedField::getRefId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }
    private static <T> Map<Long,T> byId(List<T> rows,Function<T,Long> key) {
        return rows.stream().collect(Collectors.toMap(key,Function.identity(),(a,b)->a));
    }
    private static String relativePath(RuleDataObject root,RuleDataObjectField member,Map<Long,RuleDataObject> roots,
            Map<Long,RuleDataObjectField> fields,Map<String,String> paths) {
        String full = paths.get("DATA_OBJECT:"+member.getId());
        if (full == null) full = RuleFieldAnalyzer.buildObjectFieldScriptName(member,roots,fields);
        String prefix = paths.getOrDefault("DATA_OBJECT_ROOT:"+root.getId(),
                root.getScriptName()==null || root.getScriptName().isBlank() ? root.getObjectCode() : root.getScriptName())+".";
        if (full == null || !full.startsWith(prefix)) throw new IllegalArgumentException("对象字段路径与所属对象不匹配");
        return full.substring(prefix.length());
    }
    static JsonNode objectSample(RuleDataObject root,List<RuleDataObjectField> members,Map<Long,RuleDataObject> roots,
            Map<Long,RuleDataObjectField> fields,Map<String,String> paths,List<MockRule> rules,Bindings bindings) {
        var rootSetting = bindings.roots().getOrDefault(root.getId(),ExampleSetting.AUTO);
        JsonNode result = rootSetting.value("OBJECT",root.getObjectCode(),root.getObjectLabel(),null,rules);
        if (result.isMissingNode()) return result;
        if (!result.isObject()) throw new IllegalArgumentException("整体对象示例必须是 JSON 对象");
        var children = members.stream().filter(f -> root.getId().equals(f.getObjectId()))
                .sorted(Comparator.comparingInt(f -> relativePath(root,f,roots,fields,paths).split("\\.").length)).toList();
        Set<String> boundParents = new HashSet<>();
        if (rootSetting.configured()) boundParents.add("");
        for (var child : children) {
            String path = relativePath(root,child,roots,fields,paths);
            ExampleSetting binding = bindings.fields().get(child.getId());
            if ((binding == null || !binding.configured()) && child.referencesValue()) binding = bindings.variables().get(child.getRefVariableId());
            if ((binding == null || !binding.configured()) && boundParents.stream().anyMatch(path::startsWith)) continue;
            if (binding != null && binding.configured()) boundParents.add(path+".");
            var setting = binding == null ? ExampleSetting.AUTO : binding;
            String rootPath = paths.getOrDefault("DATA_OBJECT_ROOT:"+root.getId(), root.getScriptName() == null || root.getScriptName().isBlank() ? root.getObjectCode() : root.getScriptName());
            writePath(result,path,setting.value(child.getVarType(),rootPath+"."+path,child.getVarLabel(),bindings.fieldEnums().get(child.getId()),rules),setting.configured());
        }
        return result;
    }
    static JsonNode sample(String type,String path,String label,Long binding,String options,List<MockRule> rules) {
        return sample(type,path,label,binding,options,rules,null);
    }
    static JsonNode sample(String type,String path,String label,Long binding,String options,List<MockRule> rules,String defaultValue) {
        String group = MockRule.group(type);
        MockRule rule = null;
        if (binding != null) {
            rule = rules.stream().filter(r -> binding.equals(r.id()) && group.equals(r.valueType())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("请检查 "+path+" 绑定的 Mock 规则和数据类型"));
        }
        if (options != null && !options.isBlank()) {
            var values = MockRule.parse(options);
            if (values.isArray() && !values.isEmpty()) {
                List<JsonNode> choices = new ArrayList<>();
                for (var v : values) choices.add(v.isObject() && v.has("optionValue") ? v.get("optionValue") : v);
                if (rule == null) return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                JsonNode value = rule.sample();
                if (!choices.contains(value)) throw new IllegalArgumentException(path+" 的 Mock 值不在枚举范围内");
                return value;
            }
        }
        if (rule == null) {
            String key = path.substring(path.lastIndexOf('.')+1);
            rule = rules.stream().filter(r -> group.equals(r.valueType()) && (r.matches(path,label) || r.matches(key,label))).findFirst().orElse(null);
            if (rule == null && !"PROBABILITY".equals(type)) rule = rules.stream().filter(r -> group.equals(r.valueType())
                    && "*".equals(r.matchNames())).findFirst().orElse(null);
        }
        if (rule == null && defaultValue != null && !defaultValue.isEmpty())
            return MockRule.JSON.valueToTree(RuleTestSchemaService.parseConfiguredValue(defaultValue,type == null ? "STRING" : type));
        return MockRule.checked(type,rule == null ? MockRule.fallback(type) : rule.sample());
    }
    static void writePath(JsonNode parent,String path,JsonNode value,boolean replace) {
        if (parent instanceof ArrayNode array) {
            if (array.isEmpty()) array.addObject();
            for (JsonNode item : array) writePath(item,path,value,replace);
            return;
        }
        if (!(parent instanceof ObjectNode object)) throw new IllegalArgumentException("Mock 数据的父字段必须是对象："+path);
        int dot = path.indexOf('.');
        if (dot < 0) {
            if (value.isMissingNode()) object.remove(path);
            else if (replace || !object.has(path)) object.set(path,value.deepCopy());
            return;
        }
        String first = path.substring(0,dot);
        JsonNode next = object.get(first);
        if (next == null && value.isMissingNode()) return;
        if (next == null) next = object.putObject(first);
        writePath(next,path.substring(dot+1),value,replace);
    }
}
