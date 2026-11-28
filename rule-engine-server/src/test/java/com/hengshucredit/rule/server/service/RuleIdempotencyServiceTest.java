package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleIdempotencyServiceTest {
    @org.junit.Test
    public void institutionKeysAndPersistedCallerRemainStableAcrossTokenRenewal() {
        var auth = com.hengshucredit.rule.server.auth.ProjectAuthContext.institution(2L, 3L, "institution", "API_KEY", null, true);
        String digest = "0".repeat(64);
        String scoped = ExecutionDispatch.credentialKey(digest, 2L, 3L);
        org.junit.Assert.assertEquals(64, scoped.length());
        org.junit.Assert.assertFalse(scoped.matches("[a-f0-9]{64}"));
        org.junit.Assert.assertNotEquals(scoped, ExecutionDispatch.credentialKey(digest, 3L, 3L));
        org.junit.Assert.assertNotEquals(scoped, ExecutionDispatch.credentialKey(digest, 2L, 4L));
        org.junit.Assert.assertEquals(digest, ExecutionDispatch.credentialKey(digest, null, 3L));
        var dispatch = ExecutionDispatch.of(auth.withToken(90L, "token", "VALID"), "SDK", true, null);
        String stored = com.hengshucredit.rule.server.artifact.CanonicalJson.write(dispatch);
        org.junit.Assert.assertEquals(dispatch, com.alibaba.fastjson.JSON.parseObject(stored, ExecutionDispatch.class));
        org.junit.Assert.assertTrue(ExecutionDispatch.sameCaller(auth.withToken(91L, "next", "VALID"), stored));
        org.junit.Assert.assertFalse(ExecutionDispatch.sameCaller(auth, null));
    }


    @Test public void statusDtoKeepsTheSameExplicitNullsAsTheRawResultEndpoint() {
        var service=new RuleExecutionStateService();
        var state=new RuleExecutionStateService.State(1L,8L,9L,"rule","key","digest","trace","SUCCEEDED",1,
                3L,3L,"artifact",null,"{\"snapshotVersion\":2,\"result\":{\"score\":null}}",null,null,null,null);
        var output=(Map<?,?>)service.completedResult(state).getResult();
        assertEquals(java.util.Collections.singletonMap("score",null),output);
    }

    @Test
    public void legacyStatusRepairsOnlyFromItsMatchingSuccessfulAudit() {
        var service = new RuleExecutionStateService();
        var sql =
                new org.springframework.jdbc.core.JdbcTemplate() {
                    @Override
                    public <T> java.util.List<T> queryForList(
                            String query, Class<T> type, Object... args) {
                        org.junit.Assert.assertTrue(
                                query.contains("success=1")
                                        && query.contains("revision_id <=> ?")
                                        && query.contains("artifact_digest <=> ?"));
                        org.junit.Assert.assertArrayEquals(
                                new Object[] {"trace", 8L, 9L, 2, 3L, "artifact"}, args);
                        return java.util.List.of(
                                type.cast("{\"bbox\":{\"width\":90},\"score\":null}"));
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", sql);
        var state =
                new RuleExecutionStateService.State(
                        1L,
                        8L,
                        9L,
                        "rule",
                        "key",
                        "digest",
                        "trace",
                        "SUCCEEDED",
                        2,
                        3L,
                        3L,
                        "artifact",
                        null,
                        "{\"result\":{\"bbox\":{}},\"experimentResolvedParams\":{}}",
                        null,
                        null,
                        null,
                        null);
        var result = service.publicResult(state);
        org.junit.Assert.assertFalse(result.containsKey("experimentResolvedParams"));
        org.junit.Assert.assertTrue(result.getJSONObject("result").containsKey("score"));
        assertEquals(90, result.getJSONObject("result").getJSONObject("bbox").getIntValue("width"));
    }

    @Test
    public void completedStatePreservesSharedObjectsAndNullDecisionFields() {
        var result = new com.hengshucredit.rule.model.dto.RuleResult();
        var box = Map.of("width", 90);
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("prepared", box);
        values.put("actual", box);
        values.put("score", null);
        result.setResult(values);
        var stored = RuleIdempotencyService.encodeResult(result, Map.of("input", 1));
        var parsed =
                com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(
                        stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(values, parsed.get("result"));
        assertEquals(Map.of("input", 1), parsed.get("experimentResolvedParams"));
    }

    @Test
    public void multipartReplayKeepsBytesAndTreatsLiteralFileLikeObjectsAsOrdinaryInput() throws Exception {
        var upload = new org.springframework.mock.web.MockMultipartFile("p.raw", "原件.bin", "application/octet-stream", new byte[]{0,-1,42});
        var literal = Map.of("name", "ordinary", "bytes", "unchanged");
        var snapshot = ExecutionRequestSnapshot.capture(Map.of("nested.key", java.util.List.of(upload, literal)));
        String json = com.hengshucredit.rule.server.artifact.CanonicalJson.write(snapshot.checkpoint());
        var row = new RuleExecutionStateService.State(1L, 8L, 9L, "rule", "key", "digest", "trace", "QUEUED", 0,
                3L, 3L, "artifact", json, null, null, null, null, null);
        var restored = new RuleIdempotencyService().requestParams(row);
        var nested = (java.util.List<?>) restored.get("nested.key");
        var file = (org.springframework.web.multipart.MultipartFile) nested.get(0);
        assertEquals("原件.bin", file.getOriginalFilename());
        assertEquals(upload.getContentType(), file.getContentType());
        org.junit.Assert.assertArrayEquals(upload.getBytes(), file.getBytes());
        assertEquals(literal, nested.get(1));
        assertEquals(json, com.hengshucredit.rule.server.artifact.CanonicalJson.write(ExecutionRequestSnapshot.capture(restored).checkpoint()));
    }

    @Test
    public void checkpointKeepsOriginalParamsForTraceOnlyResume() throws Exception {
        RuleExecutionStateService.State state = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "RUNNING", 1,
                3L, 3L, "artifact", null, null, null, null, null, null);
        RuleIdempotencyService service = new RuleIdempotencyService();
        RuleIdempotencyService.Decision decision = RuleIdempotencyService.Decision.claimed(
                state, "owner", 3600, 120, 3L, "artifact");

        String checkpoint = service.withDecision(decision, () -> {
            service.recordRequestParams(decision, Map.of("orderNo", "A-1", "amount", 88));
            service.recordRequestParams(decision, Map.of("orderNo", "A-1", "amount", 88.0, "projected", true));
            return service.snapshotCheckpoint(new VariableResolutionInvocationCache(), Map.of("orderNo", "A-1"));
        });
        RuleExecutionStateService.State saved = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "WAITING_EXTERNAL", 1,
                3L, 3L, "artifact", checkpoint, null, null, null, null, null);
        assertEquals(Map.of("orderNo", "A-1", "amount", 88), service.requestParams(saved));
    }
}
