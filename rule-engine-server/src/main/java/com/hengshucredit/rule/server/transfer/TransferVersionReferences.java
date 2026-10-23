package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import java.util.*;

/** Paths, resource IDs and version IDs are distinct; never replace the containing operand. */
final class TransferVersionReferences {
    private static final Set<String> VALUES = Set.of("defaultValue", "exampleValue", "testParams", "testSampleParams", "sampleParams", "description", "optionValue");
    private TransferVersionReferences() { }

    record Reference(String path, String refType, Long refId, Long versionId, String versionSource,
                     String idField, String versionField, List<String> aliases, String scriptPath,
                     Integer idStart, Integer idEnd, Integer versionStart, Integer versionEnd) {
        Reference(String path, String refType, Long refId, Long versionId, String versionSource, String idField, String versionField, List<String> aliases) {
            this(path, refType, refId, versionId, versionSource, idField, versionField, aliases, null, null, null, null, null);
        }
        String key(String resourceKey) { return resourceKey + "|" + path; }
        Set<String> ownedPaths() {
            Set<String> paths = new HashSet<>(aliases);
            if (scriptPath != null) paths.add(scriptPath);
            for (String field : List.of(idField, versionField, "versionOwnerId")) paths.add(TransferJsonPath.child(path, field));
            return paths;
        }
        Map<String, Object> descriptor() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path); result.put("refType", refType); result.put("refId", refId);
            result.put("versionId", versionId); result.put("versionSource", versionSource);
            return result;
        }
    }

    static List<Reference> scan(Object configuration) {
        List<Reference> result = new ArrayList<>();
        visit(configuration, "", null, "", "", result);
        return result;
    }

    private static void visit(Object node, String path, Map<?, ?> parent, String parentPath, String property, List<Reference> result) {
        if (node instanceof Map<?, ?> map) {
            if ("LITERAL".equals(map.get("kind"))) return;
            if ("FIXED".equals(map.get("versionMode"))) {
                boolean rule = map.get("versionBindingId") != null;
                String type = rule ? "RULE" : "FUNCTION".equals(map.get("kind")) ? "FUNCTION" : String.valueOf(map.get("refType"));
                String idField = rule ? "ruleId" : "FUNCTION".equals(type) ? "functionId" : map.containsKey("refId") ? "refId" : map.containsKey("varId") ? "varId" : "_varId";
                String versionField = rule ? "versionBindingId" : "versionId";
                if (!Set.of("RULE", "VARIABLE", "CONSTANT", "MODEL", "MODEL_OUTPUT", "DATA_OBJECT", "DATA_OBJECT_ROOT", "EXTERNAL_API", "FUNCTION").contains(type))
                    throw new IllegalArgumentException("不支持的固定版本引用类型: " + type);
                Long id = positive(map.get(idField)), version = positive(map.get(versionField));
                List<String> aliases = new ArrayList<>();
                if (parent != null) {
                    List<String> candidates = switch (property) {
                        case "leftOperand" -> List.of("leftVarId", "_varId", "varId", "_condVarId");
                        case "rightOperand" -> List.of("rightVarId", "_rightVarId");
                        case "targetOperand" -> List.of("_targetVarId", "varId");
                        case "matchOperand" -> List.of("_matchVarId");
                        case "checkOperand" -> List.of("_checkVarId");
                        case "operand", "sourceOperand", "valueOperand" -> List.of("_varId", "varId");
                        default -> List.of();
                    };
                    for (String alias : candidates) if (Objects.equals(String.valueOf(id), String.valueOf(parent.get(alias))))
                        aliases.add(TransferJsonPath.child(parentPath, alias));
                }
                result.add(new Reference(path, type, id, version, rule ? "RULE_BINDING" : Objects.toString(map.get("versionSource"), "SNAPSHOT"), idField, versionField, aliases));
            }
            for (var entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (("script".equals(key) || "implScript".equals(key) && "SCRIPT".equals(map.get("implType"))) && entry.getValue() instanceof String script && !script.isBlank()) scanScript(script, TransferJsonPath.child(path, key), result);
                if (!VALUES.contains(key)) visit(entry.getValue(), TransferJsonPath.child(path, key), map, path, key, result);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) visit(list.get(i), path + "/" + i, null, "", "", result);
        } else if (node instanceof String text && (text.stripLeading().startsWith("{") || text.stripLeading().startsWith("["))) {
            Object parsed;
            try { parsed = JSON.parse(text); } catch (com.alibaba.fastjson.JSONException invalid) { return; }
            visit(parsed, path + "/@json", null, "", "", result);
        }
    }

    private static void scanScript(String script, String path, List<Reference> result) {
        var tree = com.hengshucredit.rule.core.engine.QLExpressEngineFactory.getInstance().parseToSyntaxTree(script);
        tree.accept(new com.alibaba.qlexpress4.aparser.QLParserBaseVisitor<Void>() {
            @Override public Void visitVarIdExpr(com.alibaba.qlexpress4.aparser.QLParser.VarIdExprContext call) {
                String name = call.varId().getText();
                if (!Set.of("executeRuleVersionById", "executeRuleVersionFieldById", "executeRuleById", "executeRuleFieldById", "resourceVersionValue", "resourceVersionStatus", "invokeFunctionVersionById").contains(name)) return super.visitVarIdExpr(call);
                if (call.argumentList() == null) throw new IllegalArgumentException("固定版本脚本调用缺少参数");
                var args = call.argumentList().expression();
                boolean rule = name.startsWith("executeRule"), function = name.startsWith("invokeFunction");
                boolean latest = Set.of("executeRuleById", "executeRuleFieldById").contains(name);
                int idIndex = rule || function ? 0 : 1, versionIndex = idIndex + 1;
                if (args.size() <= (latest ? idIndex : versionIndex)) throw new IllegalArgumentException("固定版本脚本调用参数不完整");
                String type = rule ? "RULE" : function ? "FUNCTION" : literal(args.get(0).getText());
                String token = latest ? "0" : literal(args.get(versionIndex).getText());
                String source = latest ? "LATEST" : rule ? "RULE_BINDING" : token.contains(":") ? token.substring(0, token.indexOf(':')) : "SNAPSHOT";
                Long version = latest ? 0L : positive(token.contains(":") ? token.substring(token.indexOf(':') + 1) : token);
                var id = args.get(idIndex); var ver = latest ? null : args.get(versionIndex);
                result.add(new Reference(path + "/@call:" + call.getStart().getStartIndex(), type, positive(literal(id.getText())), version, source,
                        "refId", "versionId", List.of(), path, id.getStart().getStartIndex(), id.getStop().getStopIndex() + 1,
                        ver == null ? null : ver.getStart().getStartIndex(), ver == null ? null : ver.getStop().getStopIndex() + 1));
                return super.visitVarIdExpr(call);
            }
        });
    }

    private static String literal(String value) {
        if (value.matches("[1-9][0-9]*")) return value;
        if (value.length() < 2 || !(value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'")))
            throw new IllegalArgumentException("动态脚本版本引用必须先改为明确的资源和版本 ID，不能猜测跨环境映射");
        return value.substring(1, value.length() - 1);
    }

    static Long positive(Object value) {
        if (value == null || !String.valueOf(value).matches("[1-9][0-9]*")) throw new IllegalArgumentException("版本引用必须包含有效 ID");
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("版本引用 ID 超出范围", invalid); }
    }

    static List<Map<String, Object>> descriptors(TransferBundle.Resource resource) {
        Map<String, Map<String, Object>> declared = new LinkedHashMap<>();
        for (Map<String, Object> value : resource.versionReferences()) {
            if (declared.put(String.valueOf(value.get("path")), value) != null) throw new IllegalArgumentException("版本引用路径重复");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Reference reference : scan(resource.configuration())) {
            Map<String, Object> descriptor = declared.remove(reference.path());
            if (descriptor == null) descriptor = reference.descriptor();
            for (var field : reference.descriptor().entrySet()) {
                if (!Objects.equals(String.valueOf(field.getValue()), String.valueOf(descriptor.get(field.getKey()))))
                    throw new IllegalArgumentException("版本清单与配置引用不一致: " + reference.path());
            }
            Map<String, Object> row = new LinkedHashMap<>(descriptor);
            row.put("referenceKey", reference.key(resource.key())); row.put("sourceResourceKey", resource.key()); result.add(row);
        }
        if (!declared.isEmpty()) throw new IllegalArgumentException("版本清单包含不存在的引用路径");
        return result;
    }
}
