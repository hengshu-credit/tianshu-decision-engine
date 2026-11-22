package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record MockRule(Long id, String name, String valueType, String matchNames, String generator,
                       String configJson, boolean builtin, Long projectId, String projectName) {
    public MockRule(Long id, String name, String valueType, String matchNames, String generator, String configJson, boolean builtin) {
        this(id,name,valueType,matchNames,generator,configJson,builtin,null,null);
    }
    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static JsonNode parse(String raw) {
        try {
            JsonNode node = JSON.readTree(raw);
            if (node == null || node.isMissingNode()) throw new IllegalArgumentException("请输入有效 JSON");
            return node;
        }
        catch (JsonProcessingException | IllegalArgumentException ex) { throw new IllegalArgumentException("请输入有效 JSON"); }
    }
    static String group(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "INT","INTEGER","BYTE","SHORT","LONG","FLOAT","DOUBLE","DECIMAL","BIGDECIMAL","PROBABILITY","NUMBER" -> "NUMBER";
            case "BOOL","BOOLEAN" -> "BOOLEAN";
            case "ARRAY","LIST","VECTOR","SET","COLLECTION" -> "LIST";
            case "OBJECT","MAP" -> "OBJECT";
            case "DATE","LOCALDATE" -> "DATE";
            case "DATETIME","LOCALDATETIME" -> "DATETIME";
            case "TIMESTAMP" -> "TIMESTAMP";
            default -> "STRING";
        };
    }
    boolean matches(String key, String label) {
        if (matchNames.isBlank()) return false;
        return Arrays.stream(matchNames.split("[,，]")).map(String::trim)
                .map(s -> s.startsWith("$.") ? s.substring(2) : s)
                .anyMatch(s -> !s.isEmpty() && (s.equalsIgnoreCase(key) || s.equalsIgnoreCase(label)));
    }
    void validate() {
        if (name == null || name.isBlank() || name.length() > 128 || matchNames == null || matchNames.length() > 512
                || configJson == null || configJson.length() > 65536 || generator == null || valueType == null
                || !group(valueType).equals(valueType)) throw new IllegalArgumentException("请核对规则名称、字段类型和生成配置");
        JsonNode config = parse(configJson);
        boolean valid = switch (generator) {
            case "FAKER" -> {
                var method = FakerMethods.METHODS.get(config.asText());
                yield config.isTextual() && method != null && method.type().equals(valueType);
            }
            case "FIXED" -> compatible(config);
            case "CHOICE" -> config.isArray() && !config.isEmpty() && config.valueStream().allMatch(this::compatible);
            case "TEMPLATE" -> valueType.equals("STRING") && config.isTextual();
            case "DECIMAL" -> valueType.equals("NUMBER") && config.isArray() && config.size()==3
                    && config.get(0).isNumber() && config.get(1).isNumber() && config.get(2).isInt()
                    && config.get(2).intValue() >= 0 && config.get(2).intValue() <= 8
                    && config.get(0).decimalValue().scale() <= config.get(2).intValue()
                    && config.get(1).decimalValue().scale() <= config.get(2).intValue()
                    && config.get(0).decimalValue().compareTo(config.get(1).decimalValue()) <= 0;
            case "INTEGER" -> valueType.equals("NUMBER") && config.isArray() && config.size() == 2
                    && config.get(0).isIntegralNumber() && config.get(1).isIntegralNumber()
                    && config.get(0).canConvertToInt() && config.get(1).canConvertToInt()
                    && config.get(0).intValue() <= config.get(1).intValue();
            case "TEXT","PHONE","EMAIL","UUID" -> valueType.equals("STRING");
            case "DATE","BOOLEAN","DATETIME","TIMESTAMP" -> valueType.equals(generator);
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("生成方式或 JSON 参数与字段类型不匹配");
    }
    private boolean compatible(JsonNode node) {
        if (node == null || node.isMissingNode()) return false;
        return switch (valueType) {
            case "OBJECT" -> node.isObject();
            case "LIST" -> node.isArray();
            case "BOOLEAN" -> node.isBoolean();
            case "NUMBER","TIMESTAMP" -> node.isNumber();
            default -> node.isTextual();
        };
    }
    JsonNode sample() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        JsonNode config = parse(configJson);
        return switch (generator) {
            case "FAKER" -> FakerGenerator.sample(config.asText());
            case "FIXED" -> config;
            case "CHOICE" -> config.get(rng.nextInt(config.size()));
            case "INTEGER" -> LongNode.valueOf(rng.nextLong(config.get(0).longValue(), config.get(1).longValue()+1));
            case "TEMPLATE" -> TextNode.valueOf(config.asText().replace("{uuid}",UUID.randomUUID().toString())
                    .replace("{date}",LocalDate.now().toString()).replace("{digits}",String.valueOf(rng.nextInt(100000,1000000)))
                    .replace("{phone}","13"+rng.nextLong(100000000L,1000000000L)));
            case "DECIMAL" -> {
                var min = config.get(0).decimalValue();
                var range = config.get(1).decimalValue().subtract(min);
                yield DecimalNode.valueOf(min.add(range.multiply(java.math.BigDecimal.valueOf(rng.nextDouble())))
                        .setScale(config.get(2).intValue(),java.math.RoundingMode.HALF_UP));
            }
            case "PHONE" -> TextNode.valueOf("13" + rng.nextLong(100000000L, 1000000000L));
            case "EMAIL" -> TextNode.valueOf("test" + rng.nextInt(1000000) + "@example.com");
            case "UUID" -> TextNode.valueOf(UUID.randomUUID().toString());
            case "DATETIME","TIMESTAMP" -> fallback(generator);
            case "DATE" -> TextNode.valueOf(LocalDate.now().minusDays(rng.nextInt(365)).toString());
            case "BOOLEAN" -> BooleanNode.valueOf(rng.nextBoolean());
            case "TEXT" -> TextNode.valueOf("示例" + rng.nextInt(1000000));
            default -> throw new IllegalArgumentException("未知的生成方式");
        };
    }
    static JsonNode fallback(String type) {
        var rng = ThreadLocalRandom.current();
        return switch (group(type)) {
            case "NUMBER" -> "PROBABILITY".equals(type) ? DoubleNode.valueOf(rng.nextDouble()) : IntNode.valueOf(rng.nextInt(101));
            case "BOOLEAN" -> BooleanNode.valueOf(rng.nextBoolean());
            case "DATE" -> TextNode.valueOf(LocalDate.now().toString());
            case "DATETIME" -> TextNode.valueOf(LocalDateTime.now().withNano(0).toString());
            case "TIMESTAMP" -> LongNode.valueOf(System.currentTimeMillis());
            case "LIST" -> JSON.createArrayNode();
            case "OBJECT" -> JSON.createObjectNode();
            default -> TextNode.valueOf("示例" + rng.nextInt(1000000));
        };
    }
    static JsonNode checked(String type, JsonNode value) {
        if ("PROBABILITY".equals(type) && (!value.isNumber() || value.decimalValue().signum() < 0 || value.decimalValue().compareTo(java.math.BigDecimal.ONE) > 0))
            throw new IllegalArgumentException("概率的生成范围应在 0 到 1 之间");
        if (Set.of("INT","INTEGER","LONG","BYTE","SHORT").contains(type == null ? "" : type) && !value.isIntegralNumber())
            throw new IllegalArgumentException("整数字段需要整数生成规则");
        return value;
    }
}
