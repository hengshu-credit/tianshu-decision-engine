package com.hengshucredit.rule.server.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.derived.HistoryFieldIndex;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Only stable IDs and explicitly selected dimensions enter the inverted index. */
@Component
@ConditionalOnProperty(name = "rule-engine.search.enabled", havingValue = "true")
public class LogSearchDocuments {
    private final CredentialCipher cipher;
    private final ProjectAuthProperties keys;
    private final ObjectMapper json;

    public LogSearchDocuments(
            CredentialCipher cipher, ProjectAuthProperties keys, ObjectMapper json) {
        this.cipher = cipher;
        this.keys = keys;
        this.json = json;
    }

    private Map<String, Object> base(
            ElasticLogClient.Ref ref, String traceId, Long project, String code, Long actor) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", ref.id());
        meta.put("stamp", ref.stamp());
        meta.put("created_at", ElasticLogClient.date(ref.time()));
        meta.put("trace_id", traceId);
        meta.put("project_id", project);
        meta.put("project_code", code);
        meta.put("actor_user_id", actor);
        return meta;
    }

    public List<ElasticLogClient.Document> execution(
            ElasticLogClient.Ref ref, RuleExecutionLog log, String frames, boolean current) {
        var meta =
                base(
                        ref,
                        log.getTraceId(),
                        log.getExecutionProjectId(),
                        log.getProjectCode(),
                        log.getActorUserId());
        meta.put("source", log.getSource());
        meta.put("root_rule_id", log.getRootRuleId());
        meta.put("rule_code", log.getRuleCode());
        meta.put("started_at", ElasticLogClient.date(log.getStartedAt()));
        if (log.getStartedAt() != null)
            meta.put("started_micros", ElasticLogClient.microsAtOrAfter(log.getStartedAt()));
        meta.put("rule_version", log.getRuleVersion());
        meta.put("revision_id", log.getRevisionId());
        meta.put("attempt_no", log.getAttemptNo());
        meta.put("success", log.getSuccess());
        meta.put("model_type", log.getModelType());
        meta.put("cost_ms", log.getExecuteTimeMs());
        meta.put("artifact_digest", log.getArtifactDigest());
        var execution = new LinkedHashMap<>(meta);
        execution.put("body", seal(ElasticLogClient.EXECUTION, ref, log));
        var history = new LinkedHashMap<>(meta);
        boolean eligible = current && HistoryFieldIndex.eligible(log);
        history.put("eligible", eligible);
        if (eligible) {
            String snapshot = log.getHistoryFields();
            JSONObject data = JSON.parseObject(snapshot);
            if (data.getIntValue("version") == 1 && data.getJSONObject("fields") != null)
                indexFields(history, data.getJSONObject("fields"));
            // Preserve the original JSON, including explicit nulls, decimal precision and old API
            // IDs.
            history.put("body", seal(ElasticLogClient.HISTORY, ref, snapshot));
        }
        var steps = new LinkedHashMap<>(meta);
        steps.put("eligible", current);
        steps.put("stats_status", log.getRuleSetStatsStatus());
        steps.put("frames", frames == null ? List.of() : JSON.parseArray(frames));
        return List.of(
                new ElasticLogClient.Document(ElasticLogClient.EXECUTION, ref, execution),
                new ElasticLogClient.Document(ElasticLogClient.HISTORY, ref, history),
                new ElasticLogClient.Document(ElasticLogClient.STEPS, ref, steps));
    }

    public ElasticLogClient.Document call(ElasticLogClient.Ref ref, RuleRuntimeCallLog log) {
        var doc =
                base(
                        ref,
                        log.getTraceId(),
                        log.getProjectId(),
                        log.getProjectCode(),
                        log.getActorUserId());
        doc.put("root_trace_id", log.getRootTraceId());
        doc.put("rule_trace_id", log.getRuleTraceId());
        doc.put("call_id", log.getCallId());
        doc.put("module_type", log.getModuleType());
        doc.put("action_type", log.getActionType());
        doc.put("datasource_id", log.getDatasourceId());
        doc.put("target_ref_id", log.getTargetRefId());
        doc.put("target_code", log.getTargetCode());
        doc.put("success", log.getSuccess());
        doc.put("request_success", log.getRequestSuccess());
        doc.put("found", log.getFound());
        doc.put("provider_request", log.getProviderRequest());
        doc.put("cost_ms", log.getCostTimeMs());
        doc.put("attempt_no", log.getAttemptNo());
        doc.put("body", seal(ElasticLogClient.CALL, ref, log));
        return new ElasticLogClient.Document(ElasticLogClient.CALL, ref, doc);
    }

    private String seal(String kind, ElasticLogClient.Ref ref, Object value) {
        try {
            String plain =
                    json.writeValueAsString(
                            Map.of("kind", kind, "stamp", ref.stamp(), "value", value));
            if (plain.length() < 4096) return cipher.encrypt(plain);
            var buffer = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) {
                gzip.write(plain.getBytes(StandardCharsets.UTF_8));
            }
            String compressed = "gz:" + Base64.getEncoder().encodeToString(buffer.toByteArray());
            return cipher.encrypt(compressed.length() < plain.length() ? compressed : plain);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("日志快照序列化失败", e);
        }
    }

    public <T> T open(String kind, ElasticLogClient.Ref ref, String encrypted, Class<T> type) {
        if (encrypted == null) return null;
        try {
            String plain = cipher.decrypt(encrypted);
            if (plain.startsWith("gz:")) {
                try (var gzip =
                        new java.util.zip.GZIPInputStream(
                                new java.io.ByteArrayInputStream(
                                        Base64.getDecoder().decode(plain.substring(3))))) {
                    plain = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            var envelope = json.readTree(plain);
            if (!kind.equals(envelope.path("kind").asText())
                    || !ref.stamp().equals(envelope.path("stamp").asText())) return null;
            return json.treeToValue(envelope.get("value"), type);
        } catch (java.io.IOException | RuntimeException damaged) {
            return null;
        }
    }

    private void indexFields(Map<String, Object> doc, JSONObject fields) {
        Set<String> tokens = new LinkedHashSet<>();
        List<String> complete = new ArrayList<>();
        List<Map<String, Object>> numbers = new ArrayList<>();
        String keyId = keys.getActiveKeyId();
        Mac mac = matcher(keyId);
        for (var field : fields.entrySet()) {
            Object value = field.getValue();
            // A large array stays available in the lossless snapshot. Never query a partial token
            // set.
            if (tokens.size() < 4096 && addTokens(tokens, mac, field.getKey(), value))
                complete.add(field.getKey());
            if (value instanceof Number n
                    && Double.isFinite(n.doubleValue())
                    && numbers.size() < 1024)
                numbers.add(Map.of("field", field.getKey(), "value", n.doubleValue()));
        }
        doc.put("field_keys", fields.keySet());
        doc.put("complete_keys", complete);
        doc.put("match_key_id", keyId);
        doc.put("match_keys", tokens);
        doc.put("numeric_fields", numbers);
    }

    private static boolean addTokens(Set<String> target, Mac mac, String field, Object value) {
        if (value == null || value instanceof String s && s.isBlank()) return true;
        if (value instanceof Collection<?> collection) {
            for (Object item : collection)
                if (target.size() >= 4096 || !addTokens(target, mac, field, item)) return false;
            return true;
        }
        String typed;
        if (value instanceof Number n)
            typed = "n" + new BigDecimal(n.toString()).stripTrailingZeros().toString();
        else if (value instanceof String s) typed = "s" + s;
        else if (value instanceof Boolean b) typed = "b" + b;
        else return false;
        target.add(
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                mac.doFinal(
                                        (field + "\u0000" + typed)
                                                .getBytes(StandardCharsets.UTF_8))));
        return true;
    }

    public String matchKeyId() {
        return keys.getActiveKeyId();
    }

    public Set<String> matchTokens(String field, Object value) {
        Set<String> tokens = new LinkedHashSet<>();
        return addTokens(tokens, matcher(matchKeyId()), field, value) ? tokens : null;
    }

    private Mac matcher(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            keys.getMasterKeys().get(id).getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            byte[] scoped =
                    mac.doFinal("tianshu/history/equality/v1".getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(scoped, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法初始化历史字段匹配密钥", e);
        }
    }
}
