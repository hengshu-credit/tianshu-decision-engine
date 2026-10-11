package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.Assert.*;

public class ExperimentRuleVersionTest {
    @Test public void groupsResolveTheirSelectedBusinessLineAndUseItsInputContract() {
        RuleExperimentService service = new RuleExperimentService();
        RulePublished latest = new RulePublished(); latest.setDefinitionId(7L); latest.setRevisionId(30L); latest.setVersion(3);
        RulePublished fixed = new RulePublished(); fixed.setDefinitionId(7L); fixed.setRevisionId(10L); fixed.setVersion(1);
        ReflectionTestUtils.setField(service, "publishedMapper", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RulePublishedMapper.class}, (p, m, a) -> latest));
        ReflectionTestUtils.setField(service, "versionBindings", new RuleVersionBindingService() {
            @Override public RuleDefinitionVersion resolveSnapshot(Long id, Long binding) {
                assertEquals(Long.valueOf(7), id); assertEquals(Long.valueOf(40), binding);
                RuleDefinitionVersion version = new RuleDefinitionVersion(); version.setRevisionId(10L); version.setArtifactId(11L); return version;
            }
            @Override public RulePublished resolvePublished(RulePublished published, Long binding) { assertSame(latest, published); assertEquals(Long.valueOf(40), binding); return fixed; }
        });
        ReflectionTestUtils.setField(service, "publishedFields", new PublishedRuleFieldSnapshotResolver() {
            @Override public RuleFieldAnalyzer.ResolvedFields resolve(RulePublished published) {
                RuleDefinitionInputField input = new RuleDefinitionInputField(); input.setScriptName(published == fixed ? "historicalInput" : "currentInput"); input.setFieldType("STRING");
                return new RuleFieldAnalyzer.ResolvedFields(List.of(input), List.of());
            }
        });
        RuleExperimentGroup fixedGroup = new RuleExperimentGroup(); fixedGroup.setRuleId(7L); fixedGroup.setStatus(1); fixedGroup.setVersionMode("FIXED"); fixedGroup.setVersionBindingId(40L);
        RuleExperimentGroup latestGroup = new RuleExperimentGroup(); latestGroup.setRuleId(7L); latestGroup.setStatus(1);
        RuleExperiment experiment = new RuleExperiment(); experiment.setGroups(List.of(fixedGroup, latestGroup));
        assertSame(fixed, ReflectionTestUtils.invokeMethod(service, "findPublishedRule", experiment, fixedGroup));
        assertSame(latest, ReflectionTestUtils.invokeMethod(service, "findPublishedRule", experiment, latestGroup));
        RuleFieldAnalyzer.ResolvedFields fields = ReflectionTestUtils.invokeMethod(service, "resolveTestFields", experiment, List.of(7L));
        assertEquals(List.of("historicalInput", "currentInput"), fields.getInputFields().stream().map(RuleDefinitionInputField::getScriptName).toList());
    }

    @Test public void malformedVersionStrategiesCannotSilentlyFallBackToLatest() {
        var service = new RuleExperimentService(); var group = new RuleExperimentGroup(); group.setRuleId(7L);
        group.setVersionMode("FIXED"); assertThrows(IllegalArgumentException.class, () -> service.validateGroupVersion(group));
        group.setVersionMode("LATEST"); group.setVersionBindingId(8L); assertThrows(IllegalArgumentException.class, () -> service.validateGroupVersion(group));
        group.setVersionMode("unknown"); assertThrows(IllegalArgumentException.class, () -> service.validateGroupVersion(group));
    }
}
