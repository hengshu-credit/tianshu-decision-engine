package com.hengshucredit.rule.server.consumer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rule-engine.kafka")
public class RuleKafkaLogProperties {
    private boolean enabled;
    private Map<String, Long> topicProjects = new LinkedHashMap<>();
    private String groupId = "rule-engine-server";
    private long retryIntervalMs = 1000;
    /** 首次失败之后的额外重试次数。 */
    private long maxRetries = 2;

    public String[] topics() {
        return topicProjects.keySet().toArray(String[]::new);
    }

    public void validate(String bootstrapServers) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Kafka日志已启用，必须显式配置 spring.kafka.bootstrap-servers");
        }
        if (topicProjects == null || topicProjects.isEmpty()) {
            throw new IllegalArgumentException("Kafka日志已启用，必须配置 rule-engine.kafka.topic-projects");
        }
        topicProjects.forEach((topic, projectId) -> {
            if (topic == null || !topic.matches("[a-zA-Z0-9._-]{1,245}")
                    || ".".equals(topic) || "..".equals(topic) || topic.endsWith(".DLT")
                    || projectId == null || projectId <= 0) {
                throw new IllegalArgumentException("Kafka日志 topic-projects 必须绑定合法主题和正项目ID，不能订阅死信主题");
            }
        });
        if (groupId == null || groupId.isBlank() || retryIntervalMs < 0 || maxRetries < 0) {
            throw new IllegalArgumentException("Kafka日志 group-id 必须非空，重试次数与间隔必须非负");
        }
    }
}
