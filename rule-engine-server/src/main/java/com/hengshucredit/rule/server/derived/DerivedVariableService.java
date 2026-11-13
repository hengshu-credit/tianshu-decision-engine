package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.service.OperandDependencyCollector;
import com.hengshucredit.rule.server.service.OperandValueResolver;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleVariableService;
import com.hengshucredit.rule.server.service.VariableResolveOptions;
import com.hengshucredit.rule.server.artifact.ArtifactRuntimeSnapshotService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DerivedVariableService {
    @Resource
    private ApplicationHistoryRepository historyRepository;
    @Resource private IndexedHistoryCount indexedCounts;
    @Resource
    private ExecutionLogHistoryRepository executionLogHistoryRepository;
    @Resource
    private RuleFunctionService functionService;
    @Resource
    private RuleVariableService variableService;
    @Resource
    private com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper dataObjectFieldMapper;

    public Map<String, String> referencePaths(Long projectId) {
        return referencePaths(projectId, Map.of());
    }

    public Map<String, String> objectReferencePaths(Long projectId) {
        Map<String, String> result = new LinkedHashMap<>();
        variableService.buildRefScriptNameMap(projectId).forEach((key, path) -> {
            if (key.startsWith("DATA_OBJECT:")) result.put(key, path);
        });
        return result;
    }

    public Map<String, String> referencePaths(Long projectId, Map<String, Object> values) {
        Map<String, String> paths = new LinkedHashMap<>(variableService.buildRefScriptNameMap(projectId));
        Map<String, String> original = new LinkedHashMap<>(paths);
        List<Long> ids = paths.keySet().stream().filter(key -> key.startsWith("DATA_OBJECT:"))
                .map(key -> Long.valueOf(key.substring("DATA_OBJECT:".length()))).toList();
        if (!ids.isEmpty()) HistoryFieldValues.applyAliases(paths, dataObjectFieldMapper.selectByIds(ids));
        HistoryFieldValues.preferAssignedObjectPaths(paths, original, values);
        return paths;
    }

    public Map<String, HistoricalFieldDefinition> historyDefinitions(List<RuleVariable> variables,
            List<com.hengshucredit.rule.model.entity.RuleModel> models, Map<String, String> paths) {
        List<Long> ids = paths.keySet().stream().filter(key -> key.startsWith("DATA_OBJECT:"))
                .map(key -> Long.valueOf(key.substring("DATA_OBJECT:".length()))).toList();
        var fields = ids.isEmpty() || dataObjectFieldMapper == null ? List.<com.hengshucredit.rule.model.entity.RuleDataObjectField>of()
                : dataObjectFieldMapper.selectByIds(ids);
        return HistoricalFieldDefinition.build(variables, fields, models, paths);
    }

    public Map<String, HistoricalFieldDefinition> historyDefaults(Long projectId) {
        var definitions = historyDefinitions(variableService.listByProject(projectId, null), List.of(), referencePaths(projectId));
        Map<String, HistoricalFieldDefinition> defaults = new LinkedHashMap<>();
        definitions.forEach((key, field) -> {
            if ("INPUT".equals(field.source()) || "API".equals(field.source())) defaults.put(key, field);
        });
        return defaults;
    }

    public static void prepareSnapshot(VariableResolveOptions options, String modelJson,
                                       ArtifactRuntimeSnapshotService.RuntimeSnapshot snapshot) {
        options.setDerivedReferencePaths(HistoryFieldValues.frozenPaths(snapshot));
        options.setDataObjectReferencePaths(new LinkedHashMap<>(snapshot.getReferencePaths()));
        options.setHistoryFieldDefinitions(HistoricalFieldDefinition.build(snapshot.getVariables(),
                snapshot.getDataObjectFields(), snapshot.getModels(), options.getDerivedReferencePaths()));
        java.util.Set<Long> derivedIds = new java.util.HashSet<>();
        snapshot.getVariables().stream().filter(variable -> "DERIVED".equals(variable.getVarSource()))
                .forEach(variable -> derivedIds.add(variable.getId()));
        if (snapshot.isImported() && snapshot.getVariables().stream().anyMatch(variable ->
                "DERIVED".equals(variable.getVarSource()) && "HISTORY".equals(JSON.parseObject(variable.getSourceConfig()).getString("mode")))) {
            throw new IllegalArgumentException("导入制品的历史统计字段尚未建立本地 ID 映射，不能查询本地进件历史");
        }
        if (derivedIds.isEmpty() || modelJson == null) return;
        java.util.Set<String> required = options.getRequiredScriptNames() == null
                ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(options.getRequiredScriptNames());
        for (var reference : OperandDependencyCollector.collectReferences(JSON.parse(modelJson))) {
            if ("VARIABLE".equals(reference.getRefType()) && derivedIds.contains(reference.getRefId())) {
                required.add(options.getDerivedReferencePaths().get("VARIABLE:" + reference.getRefId()));
            }
        }
        options.setRequiredScriptNames(required);
    }

    public Object resolve(RuleVariable variable, Map<String, Object> values, Map<String, String> paths,
                          Map<Long, RuleFunction> functions) {
        return resolve(variable, values, paths, functions, null);
    }

    public Object resolve(RuleVariable variable, Map<String, Object> values, Map<String, String> paths,
                          Map<Long, RuleFunction> functions, Map<String, HistoricalFieldDefinition> definitions) {
        return resolve(variable, values, paths, functions, definitions, null);
    }

    public Object resolve(RuleVariable variable, Map<String, Object> values, Map<String, String> paths,
                          Map<Long, RuleFunction> functions, Map<String, HistoricalFieldDefinition> definitions,
                          VariableResolveOptions options) {
        JSONObject config = JSON.parseObject(variable.getSourceConfig());
        DerivedVariableConfig.validate(config);
        Map<String, Object> references = new LinkedHashMap<>();
        paths.forEach((key, path) -> references.put(key, HistoryFieldValues.read(values, path)));
        for (JSONObject input : DerivedVariableConfig.currentInputs(config)) {
            for (var reference : OperandDependencyCollector.collectReferences(input)) {
                if (reference.getVersionId() != null) continue;
                if ("FUNCTION".equals(reference.getRefType())) continue;
                String key = reference.getRefType() + ":" + reference.getRefId();
                DerivedVariableConfig.require(paths.containsKey(key), "衍生上游字段不在当前执行快照中: " + key);
            }
        }
        OperandValueResolver.FunctionInvoker invoker = (id, code, args) -> {
            RuleFunction function = functions == null ? functionService.getById(id) : functions.get(id);
            DerivedVariableConfig.require(function != null && Integer.valueOf(1).equals(function.getStatus()), "衍生引用函数不存在或未生效: " + id);
            return functionService.invokeSnapshot(function, args);
        };
        Map<JSONObject, Object> inputValues = new LinkedHashMap<>();
        java.util.function.Function<JSONObject, Object> input = operand -> {
            if (!inputValues.containsKey(operand)) inputValues.put(operand, OperandValueResolver.resolve(operand, values, references, invoker));
            return inputValues.get(operand);
        };
        if ("EXPRESSION".equals(config.getString("mode"))) return checkedValue(variable, input.apply(config.getJSONObject("expression")));
        var context = RuntimeContextBridge.currentContext();
        Map<String, Object> root = context.rootRule();
        Long projectId = root.get("projectId") instanceof Number n ? n.longValue() : variable.getProjectId();
        Long rootRuleId = root.get("id") instanceof Number n ? n.longValue() : null;
        LocalDateTime before = root.isEmpty() ? LocalDateTime.now() : context.startedAt();
        long window = config.getLongValue("window");
        LocalDateTime from = historyStart(before, window, config.getString("windowUnit"));
        java.util.Set<String> historyKeys = new java.util.LinkedHashSet<>();
        for (JSONObject field : DerivedVariableConfig.historicalFields(config)) {
            String key = DerivedVariableConfig.fieldKey(field);
            historyKeys.add(key);
            if (definitions != null) {
                HistoricalFieldDefinition definition = definitions.get(key);
                DerivedVariableConfig.require(definition != null && definition.queryable(),
                        "历史字段未开启结果记录；默认仅支持请求入参和三方调用结果: " + key);
            }
        }
        if (indexedCounts != null && simpleCount(config)) {
            JSONObject geo = config.getJSONObject("geo");
            IndexedHistoryCount.Region region = geo == null ? null : new IndexedHistoryCount.Region(
                    DerivedVariableConfig.fieldKey(geo.getJSONObject("longitudeField")),
                    DerivedVariableConfig.fieldKey(geo.getJSONObject("latitudeField")),
                    HistoryQuery.number(input.apply(geo.getJSONObject("longitude"))).doubleValue(),
                    HistoryQuery.number(input.apply(geo.getJSONObject("latitude"))).doubleValue(),
                    HistoryQuery.number(input.apply(geo.getJSONObject("radiusMeters"))).doubleValue());
            var selection = new IndexedHistoryCount.Selection(config.getString("scope"), projectId, rootRuleId, before, region);
            java.util.function.Supplier<Map<LocalDateTime, IndexedHistoryCount.Count>> load =
                    () -> indexedCounts.count(selection, countWindows(config, options, before, from));
            var count = options == null || options.getInvocationCache() == null ? load.get().get(from)
                    : options.getInvocationCache().historyCount(selection, from, load);
            if (count != null) {
                recordHistory(variable, config, from, before, count.candidates());
                return checkedValue(variable, count.matched());
            }
        }
        List<HistoryQuery.Row> history = executionLogHistoryRepository == null
                ? historyRepository.query(config.getString("scope"), projectId, rootRuleId, from, before)
                : executionLogHistoryRepository.query(config.getString("scope"), projectId, rootRuleId,
                from, before, historyKeys, definitions == null ? Map.of() : definitions);
        Object result = HistoryQuery.evaluate(config, history, input, invoker);
        recordHistory(variable, config, from, before, history.size());
        return checkedValue(variable, result);
    }

    private void recordHistory(RuleVariable variable, JSONObject config, LocalDateTime from, LocalDateTime before, long candidates) {
        RuntimeContextBridge.addTraceEvent(new LinkedHashMap<>(Map.of("type", "DERIVED_HISTORY", "variableId", variable.getId() == null ? 0L : variable.getId(),
                "scope", config.getString("scope"), "from", from.toString(), "before", before.toString(), "candidateCount", candidates)));
    }

    private static boolean simpleCount(JSONObject config) {
        return "HISTORY".equals(config.getString("mode")) && "COUNT".equals(config.getString("aggregate"))
                && !"LATEST_PER_SUBJECT".equals(config.getString("recordMode"))
                && DerivedVariableConfig.objects(config,"steps").isEmpty() && DerivedVariableConfig.objects(config,"filters").isEmpty();
    }

    private static List<LocalDateTime> countWindows(JSONObject config, VariableResolveOptions options,
                                                     LocalDateTime before, LocalDateTime from) {
        var windows = new java.util.LinkedHashSet<LocalDateTime>();
        windows.add(from);
        if (options != null && options.getRuntimeSnapshot() != null) {
            List<RuleVariable> variables = options.getInvocationCache() == null ? List.of() : options.getInvocationCache().rootVariables();
            if (variables.isEmpty()) variables = options.getRuntimeSnapshot().getVariables();
            for (RuleVariable other : variables) {
                if (!"DERIVED".equals(other.getVarSource())) continue;
                var candidate = JSON.parseObject(other.getSourceConfig());
                if (candidate != null && simpleCount(candidate)
                        && java.util.Objects.equals(candidate.get("geo"),config.get("geo"))
                        && java.util.Objects.equals(candidate.get("scope"),config.get("scope"))) {
                    windows.add(historyStart(before,candidate.getLongValue("window"),candidate.getString("windowUnit")));
                    if (windows.size() == 32) break;
                }
            }
        }
        return List.copyOf(windows);
    }

    static LocalDateTime historyStart(LocalDateTime before, long window, String unit) {
        return switch (unit) {
            case "MINUTE" -> before.minusMinutes(window);
            case "HOUR" -> before.minusHours(window);
            case "CALENDAR_DAY" -> before.toLocalDate().minusDays(window - 1).atStartOfDay();
            default -> before.minusDays(window);
        };
    }

    private Object checkedValue(RuleVariable variable, Object value) {
        if (value == null) return null;
        String type = variable.getVarType() == null ? "OBJECT" : variable.getVarType();
        boolean valid = switch (type) {
            case "NUMBER", "DOUBLE", "INTEGER", "INT", "LONG", "FLOAT", "DECIMAL", "PROBABILITY" -> value instanceof Number;
            case "STRING", "ENUM" -> value instanceof String;
            case "BOOLEAN" -> value instanceof Boolean;
            case "LIST", "ARRAY", "VECTOR" -> value instanceof java.util.Collection<?> || value.getClass().isArray();
            case "MAP", "OBJECT" -> value instanceof Map<?, ?>;
            case "DATE", "DATETIME" -> value instanceof String || value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.Date;
            default -> false;
        };
        DerivedVariableConfig.require(valid, "衍生结果与字段类型 " + type + " 不一致，请调整表达式或使用显式类型转换");
        return value;
    }
}
