package com.hengshucredit.rule.server.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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
        });
    }
}
