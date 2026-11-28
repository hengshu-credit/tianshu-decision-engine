package com.hengshucredit.rule.server.search;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class LogSearchDocumentsTest {
    public static LogSearchDocuments codec() {
        var keys = testKeys();
        return new LogSearchDocuments(
                new CredentialCipher(keys), keys, new ObjectMapper().findAndRegisterModules());
    }

    private static ProjectAuthProperties testKeys() {
        var keys = new ProjectAuthProperties();
        keys.setActiveKeyId("test");
        keys.setMasterKeys(Map.of("test", "local synthetic test key material"));
        return keys;
    }

    @Test
    public void executionAndHistoryBodiesAreReadableAndMatchTokensRemainTyped() throws Exception {
        var codec = codec();
        var ref = new ElasticLogClient.Ref(12, LocalDateTime.parse("2026-11-26T09:00:00"), 3);
        var log = new RuleExecutionLog();
        log.setId(12L);
        log.setTraceId("root");
        log.setSuccess(1);
        log.setSource("CLIENT_SERVER");
        log.setStartedAt(ref.time());
        log.setCreateTime(ref.time());
        String snapshot =
                "{\"version\":1,\"fields\":{\"VARIABLE:3\":\"secret-device\",\"VARIABLE:4\":null,\"MODEL_OUTPUT:5\":1234567890123456789.0123456789,\"DATA_OBJECT:6\":[1.00,\"1\",0]}}";
        log.setHistoryFields(snapshot);
        log.setTraceInfo("trace-detail".repeat(2500));
        var docs = codec.execution(ref, log, "[]", true);
        assertEquals(
                List.of(
                        ElasticLogClient.EXECUTION,
                        ElasticLogClient.HISTORY,
                        ElasticLogClient.STEPS),
                docs.stream().map(ElasticLogClient.Document::kind).toList());
        var json = new ObjectMapper();
        var execution = json.readTree((String) docs.get(0).source().get("body"));
        assertEquals(log.getTraceInfo(), execution.path("value").path("traceInfo").textValue());
        var feature = docs.get(1).source();
        assertEquals(
                snapshot, json.readTree((String) feature.get("body")).path("value").textValue());
        assertFalse(feature.get("match_keys").toString().contains("secret-device"));
        assertEquals("2026-11-26T09:00:00", feature.get("started_at"));
        assertEquals(
                snapshot,
                codec.open(
                        ElasticLogClient.HISTORY, ref, (String) feature.get("body"), String.class));
        assertNull(
                codec.open(ElasticLogClient.CALL, ref, (String) feature.get("body"), String.class));
        assertNull(
                codec.open(
                        ElasticLogClient.HISTORY,
                        new ElasticLogClient.Ref(12, ref.time(), 4),
                        (String) feature.get("body"),
                        String.class));
        assertEquals(
                log.getTraceInfo(),
                codec.open(
                                ElasticLogClient.EXECUTION,
                                ref,
                                (String) docs.get(0).source().get("body"),
                                RuleExecutionLog.class)
                        .getTraceInfo());
        assertEquals(codec.matchTokens("VARIABLE:1", 1.00), codec.matchTokens("VARIABLE:1", 1));
        assertNotEquals(codec.matchTokens("VARIABLE:1", "1"), codec.matchTokens("VARIABLE:1", 1));
        assertNotEquals(
                codec.matchTokens("VARIABLE:1", "1"), codec.matchTokens("MODEL_OUTPUT:1", "1"));
        assertEquals(0, codec.matchTokens("VARIABLE:1", List.of(" ", List.of())).size());
        assertNull(codec.matchTokens("VARIABLE:1", Map.of("nested", 1)));
        log.setSource("SERVER");
        assertEquals(false, codec.execution(ref, log, null, true).get(1).source().get("eligible"));
        log.setSource("EXPERIMENT_LIVE");
        log.setSuccess(0);
        assertFalse(codec.execution(ref, log, null, true).get(1).source().containsKey("body"));
        log.setSuccess(1);
        assertEquals(false, codec.execution(ref, log, null, false).get(1).source().get("eligible"));
    }

    @Test
    public void runtimeBodyExposesRetainedPayloadAsJson() throws Exception {
        var codec = codec();
        var ref = new ElasticLogClient.Ref(1, LocalDateTime.now(), 1);
        var call = new RuleRuntimeCallLog();
        call.setRawRequestBody("sensitive-request");
        call.setOriginalRequestBody("different-original");
        call.setModuleType("DATASOURCE");
        call.setActionType("API_INVOKE");
        var doc = codec.call(ref, call);
        var value = new ObjectMapper().readTree((String) doc.source().get("body")).path("value");
        assertEquals("sensitive-request", value.path("rawRequestBody").textValue());
        assertEquals("different-original", value.path("originalRequestBody").textValue());
        var restored =
                codec.open(
                        ElasticLogClient.CALL,
                        ref,
                        doc.source().get("body").toString(),
                        RuleRuntimeCallLog.class);
        assertEquals("sensitive-request", restored.getRawRequestBody());
        assertEquals("different-original", restored.getOriginalRequestBody());
    }

    @Test
    public void readsLegacyEncryptedAndCompressedBodiesForTheExpectedRevision() throws Exception {
        var codec = codec();
        var cipher = new CredentialCipher(testKeys());
        var ref = new ElasticLogClient.Ref(12, LocalDateTime.parse("2026-11-26T09:00:00"), 3);
        String envelope = new ObjectMapper().writeValueAsString(
                Map.of("kind", ElasticLogClient.EXECUTION, "stamp", ref.stamp(),
                        "value", Map.of("traceInfo", "旧追踪")));
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(envelope.getBytes(StandardCharsets.UTF_8));
        }
        String compressed = "gz:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
        for (String legacy : List.of(envelope, compressed)) {
            String encrypted = cipher.encrypt(legacy);
            var restored = codec.open(ElasticLogClient.EXECUTION, ref, encrypted, RuleExecutionLog.class);
            assertEquals("旧追踪", restored.getTraceInfo());
            var otherRevision = new ElasticLogClient.Ref(ref.id(), ref.time(), 4);
            assertNull(codec.open(
                    ElasticLogClient.EXECUTION, otherRevision, encrypted, RuleExecutionLog.class));
        }
    }
}
