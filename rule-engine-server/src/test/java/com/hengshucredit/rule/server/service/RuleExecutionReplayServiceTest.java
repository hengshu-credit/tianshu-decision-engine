package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class RuleExecutionReplayServiceTest {

    @Test
    public void replayUsesHistoricalImmutableArtifactWhenLogIdentityMatches() {
        RuleRevision revision = new RuleRevision();
        revision.setId(41L);
        revision.setDefinitionId(7L);
        revision.setRevisionNo(3);
        revision.setArtifactId(91L);
        revision.setModelJson("{\"resultVar\":\"score\"}");
        revision.setCompiledScript("score = 100;");

        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(91L);
        artifact.setArtifactDigest("sha-historical");

        RuleExecutionReplayService service = service(revision, artifact);
        RulePublished current = published(7L, 5, 52L, 192L, "sha-current");
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode("RULE_A");
        log.setProjectCode("PROJECT_A");
        log.setRootRuleId(7L);
        log.setRuleVersion(3);
        log.setRevisionId(41L);
        log.setArtifactDigest("sha-historical");
        List<String> warnings = new ArrayList<>();

        RulePublished replay = ReflectionTestUtils.invokeMethod(service,
                "resolveReplayPublished", log, current, warnings);

        Assert.assertNotSame(current, replay);
        Assert.assertEquals(Long.valueOf(91L), replay.getArtifactId());
        Assert.assertEquals(Long.valueOf(41L), replay.getRevisionId());
        Assert.assertEquals("sha-historical", replay.getArtifactDigest());
        Assert.assertTrue(warnings.get(0).contains("不可变制品"));
    }

    @Test
    public void replayFallsBackWhenHistoricalArtifactIsMissing() {
        RuleRevision revision = new RuleRevision();
        revision.setId(41L);
        revision.setDefinitionId(7L);
        revision.setArtifactId(null);

        RuleExecutionReplayService service = service(revision, null);
        RulePublished current = published(7L, 5, 52L, 192L, "sha-current");
        RuleExecutionLog log = new RuleExecutionLog();
        log.setRootRuleId(7L);
        log.setRevisionId(41L);
        List<String> warnings = new ArrayList<>();

        RulePublished replay = ReflectionTestUtils.invokeMethod(service,
                "resolveReplayPublished", log, current, warnings);

        Assert.assertSame(current, replay);
        Assert.assertEquals(1, warnings.size());
        Assert.assertTrue(warnings.get(0).contains("回退当前发布版本"));
    }

    private RuleExecutionReplayService service(RuleRevision revision, DecisionArtifact artifact) {
        RuleExecutionReplayService service = new RuleExecutionReplayService();
        RuleRevisionMapper revisions = proxy(RuleRevisionMapper.class, revision);
        DecisionArtifactMapper artifacts = proxy(DecisionArtifactMapper.class, artifact);
        ReflectionTestUtils.setField(service, "revisionMapper", revisions);
        ReflectionTestUtils.setField(service, "artifactMapper", artifacts);
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Object value) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> "selectById".equals(method.getName()) ? value : null);
    }

    private RulePublished published(Long definitionId, int version, Long revisionId,
                                   Long artifactId, String digest) {
        RulePublished published = new RulePublished();
        published.setRuleCode("RULE_A");
        published.setProjectCode("PROJECT_A");
        published.setDefinitionId(definitionId);
        published.setVersion(version);
        published.setRevisionId(revisionId);
        published.setArtifactId(artifactId);
        published.setArtifactDigest(digest);
        published.setModelType("SCRIPT");
        return published;
    }
}
