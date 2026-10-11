package com.hengshucredit.rule.server.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.ResolvedField;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        return builder -> builder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
            @Override
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString();
                if (value == null || value.trim().isEmpty()) {
                    return null;
                }
                String text = value.trim();
                try {
                    return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception ignored) {
                    try {
                        return LocalDateTime.parse(text, DATE_TIME_FORMAT);
                    } catch (Exception ignoredAgain) {
                        return LocalDate.parse(text, DATE_FORMAT).atTime(LocalTime.MIN);
                    }
                }
            }
        }).serializerByType(RuleTestSchema.class, new TestSchemaSerializer())
                .mixIn(com.hengshucredit.rule.model.entity.RuleExecutionLog.class, StatisticsInternalMixin.class);
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"ruleSetStatsJson", "ruleSetStatsStatus", "ruleSetStatsVersion"})
    private abstract static class StatisticsInternalMixin { }

    /** 仅测试结构把引用ID写为文本，保持其它业务实体的Long序列化规则不变。 */
    private static final class TestSchemaSerializer extends JsonSerializer<RuleTestSchema> {
        @Override
        public void serialize(RuleTestSchema schema, JsonGenerator generator, SerializerProvider provider) throws IOException {
            ObjectMapper mapper = (ObjectMapper) generator.getCodec();
            generator.writeStartObject();
            fields("inputs", schema.getInputs(), generator, mapper);
            fields("runtimeNodes", schema.getRuntimeNodes(), generator, mapper);
            fields("outputs", schema.getOutputs(), generator, mapper);
            generator.writeObjectField("sampleParams", schema.getSampleParams());
            generator.writeStringField("sampleParamsJson", mapper.writeValueAsString(schema.getSampleParams()));
            generator.writeObjectField("diagnostics", schema.getDiagnostics());
            if (schema.getExecutionIdentity() != null) generator.writeObjectField("executionIdentity", schema.getExecutionIdentity());
            generator.writeEndObject();
        }

        private void fields(String name, List<ResolvedField> values, JsonGenerator generator, ObjectMapper mapper) throws IOException {
            if (values == null) { generator.writeNullField(name); return; }
            generator.writeArrayFieldStart(name);
            for (ResolvedField field : values) {
                if (field == null) { generator.writeNull(); continue; }
                // 使用原字段serializer保留default/example/validValues/dependencies及以后新增的属性。
                ObjectNode node = mapper.valueToTree(field);
                if (field.getRefId() != null) node.put("refId", field.getRefId().toString());
                generator.writeTree(node);
            }
            generator.writeEndArray();
        }
    }
}
