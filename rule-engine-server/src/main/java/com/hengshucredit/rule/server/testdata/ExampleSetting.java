package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.hengshucredit.rule.server.service.RuleTestSchemaService;
import java.util.List;

record ExampleSetting(String mode, String fixed, Long method, java.util.function.Supplier<JsonNode> caseValue) {
    ExampleSetting(String mode, String fixed, Long method) { this(mode, fixed, method, null); }
    static final ExampleSetting AUTO = new ExampleSetting("AUTO",null,null);

    static ExampleSetting resolve(String mode,String fixed,Long method,Long matched) {
        if (mode == null || mode.isBlank()) mode = method != null ? "RULE" : fixed != null && !fixed.isEmpty() ? "FIXED" : "AUTO";
        return "AUTO".equals(mode) && matched != null ? new ExampleSetting("RULE",null,matched) : new ExampleSetting(mode,fixed,method);
    }
    boolean configured() { return !"AUTO".equals(mode); }
    JsonNode value(String type,String path,String label,String options,List<MockRule> rules) {
        return value(type,path,label,options,rules,null);
    }
    JsonNode value(String type,String path,String label,String options,List<MockRule> rules,String defaultValue) {
        return switch (mode) {
            case "CASE" -> caseValue.get().deepCopy();
            case "NONE" -> MissingNode.getInstance();
            case "FIXED" -> fixed(type);
            case "RULE" -> {
                if (method == null) throw new IllegalArgumentException(path+" 尚未选择 Mock 方法");
                yield TestMockService.sample(type,path,label,method,options,rules);
            }
            case "AUTO" -> TestMockService.sample(type,path,label,null,options,rules,defaultValue);
            default -> throw new IllegalArgumentException("未知示例方式："+mode);
        };
    }
    private JsonNode fixed(String type) {
        if (fixed == null || fixed.isEmpty()) return MissingNode.getInstance();
        if (fixed.startsWith("\"") && fixed.endsWith("\"") && "STRING".equals(MockRule.group(type))) {
            try { return MockRule.parse(fixed); }
            catch (IllegalArgumentException ignored) { /* 兼容旧配置中的带引号文本 */ }
        }
        String inputType = switch (type == null ? "STRING" : type) {
            case "SET", "COLLECTION" -> "LIST";
            case "BYTE", "SHORT" -> "INTEGER";
            case "BIGDECIMAL" -> "DECIMAL";
            case "TIMESTAMP" -> "LONG";
            default -> type == null ? "STRING" : type;
        };
        Object configured = RuleTestSchemaService.parseConfiguredValue(fixed, inputType);
        return MockRule.JSON.valueToTree(configured);
    }
}
