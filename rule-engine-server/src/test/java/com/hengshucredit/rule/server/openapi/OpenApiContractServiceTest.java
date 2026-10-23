package com.hengshucredit.rule.server.openapi;

import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.junit.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class OpenApiContractServiceTest {
    @Test
    public void draftRenamesDoNotChangePublishedRequestOrResponseReferences() {
        try (Fixture fixture = new Fixture()) {
            fixture.liveInput = input("draftAge", 1);
            fixture.liveOutput = output("draftDecision", 1);
            var resolved = fixture.resolve();
            assertEquals(Map.of("VARIABLE:11", "publishedAge"), resolved.getInputReferences());
            assertEquals(Map.of("VARIABLE:12", "publishedDecision"), resolved.getOutputReferences());
            assertEquals(0, fixture.liveFieldReads);
        }
    }

    @Test
    public void removingDraftFieldsDoesNotRemoveThePublishedContract() {
        try (Fixture fixture = new Fixture()) {
            fixture.liveInput = null;
            fixture.liveOutput = null;
            var resolved = fixture.resolve();
            assertEquals("publishedAge", resolved.getInputReferences().get("VARIABLE:11"));
            assertEquals("publishedDecision", resolved.getOutputReferences().get("VARIABLE:12"));
        }
    }

    @Test
    public void invalidPublishedSnapshotNeverFallsBackToDraftFields() {
        try (Fixture fixture = new Fixture()) {
            fixture.fields = new RuleFieldAnalyzer.ResolvedFields(List.of(), List.of(),
                    List.of(RuleValidationIssue.error("FROZEN_REVISION_FIELD_SNAPSHOT_INVALID", "$", "bad digest")),
                    Set.of(), Map.of(), Map.of());
            OpenApiException error = assertThrows(OpenApiException.class, fixture::resolve);
            assertEquals(500, error.getStatus().getHttpStatus());
            assertEquals(0, fixture.liveFieldReads);
        }
    }

    @Test
    public void routingAndExecutionComeFromOnePublishedReadDuringConcurrentPublication() {
        try (Fixture fixture = new Fixture()) {
            // First routing read selected v1; a second read would already see v2.
            fixture.published.setVersion(2);
            fixture.published.setRevisionId(200L);
            fixture.published.setArtifactId(201L);
            var resolved = fixture.resolve();
            assertEquals(Integer.valueOf(1), resolved.getPublished().getVersion());
            assertEquals(Long.valueOf(100), resolved.getPublished().getRevisionId());
            assertEquals(0, fixture.secondPublishedReads);
        }
    }

    @Test
    public void disabledFrozenReferencesCannotBeSelectedInEitherDirection() {
        try (Fixture fixture = new Fixture()) {
            fixture.fields = new RuleFieldAnalyzer.ResolvedFields(
                    List.of(input("disabledInput", 0)), List.of(output("disabledOutput", 0)));
            var resolved = fixture.resolve();
            assertTrue(resolved.getInputReferences().isEmpty());
            assertTrue(resolved.getOutputReferences().isEmpty());
        }
    }

    @Test
    public void legacyPublicationWithoutArtifactKeepsExistingFieldResolution() {
        try (Fixture fixture = new Fixture()) {
            fixture.published.setRevisionId(null);
            fixture.published.setArtifactId(null);
            fixture.published.setArtifactDigest(null);
            BeanUtils.copyProperties(fixture.published, fixture.projection);
            var resolved = fixture.resolve();
            assertEquals("liveAge", resolved.getInputReferences().get("VARIABLE:11"));
            assertEquals("liveDecision", resolved.getOutputReferences().get("VARIABLE:12"));
        }
    }

    private static RuleDefinitionInputField input(String name, int status) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setVarId(11L); field.setRefType("VARIABLE"); field.setScriptName(name); field.setStatus(status);
        return field;
    }

    private static RuleDefinitionOutputField output(String name, int status) {
        RuleDefinitionOutputField field = new RuleDefinitionOutputField();
        field.setVarId(12L); field.setRefType("VARIABLE"); field.setScriptName(name); field.setStatus(status);
        return field;
    }

    private static final class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final RulePublished published = new RulePublished();
        final PublishedOpenApiContract projection = new PublishedOpenApiContract();
        RuleDefinitionInputField liveInput = input("liveAge", 1);
        RuleDefinitionOutputField liveOutput = output("liveDecision", 1);
        RuleFieldAnalyzer.ResolvedFields fields = new RuleFieldAnalyzer.ResolvedFields(
                List.of(input("publishedAge", 1)), List.of(output("publishedDecision", 1)));
        int liveFieldReads;
        int secondPublishedReads;

        Fixture() {
            published.setId(50L); published.setDefinitionId(10L); published.setRuleCode("R");
            published.setProjectCode("P"); published.setVersion(1); published.setStatus(1);
            published.setRevisionId(100L); published.setArtifactId(101L); published.setArtifactDigest("digest-v1");
            published.setOpenApiConfigJson("{\"enabled\":true,"
                    + "\"successDataTemplate\":\"${response}\",\"errorDataTemplate\":{}}");
            BeanUtils.copyProperties(published, projection);
            RuleProjectAuth auth = new RuleProjectAuth();
            auth.setId(5L); auth.setProjectId(7L); auth.setStatus(1);
            RuleProject project = new RuleProject();
            project.setId(7L); project.setProjectCode("P"); project.setStatus(1);
            singleton("authMapper", proxy(RuleProjectAuthMapper.class, (method, args) -> auth));
            singleton("publishedMapper", proxy(RulePublishedMapper.class, (method, args) -> {
                if ("selectOpenApiContract".equals(method)) return projection;
                if ("selectOne".equals(method)) { secondPublishedReads++; return published; }
                throw new AssertionError("unexpected published access: " + method);
            }));
            singleton("projectService", new RuleProjectService() {
                @Override public RuleProject getById(java.io.Serializable id) { return project; }
            });
            singleton("definitionService", new RuleDefinitionService() {
                @Override public List<RuleDefinitionInputField> listInputFields(Long id) {
                    liveFieldReads++; return liveInput == null ? List.of() : List.of(liveInput);
                }
                @Override public List<RuleDefinitionOutputField> listOutputFields(Long id) {
                    liveFieldReads++; return liveOutput == null ? List.of() : List.of(liveOutput);
                }
            });
            singleton("publishedFieldSnapshotResolver", new PublishedRuleFieldSnapshotResolver() {
                @Override public RuleFieldAnalyzer.ResolvedFields resolve(RulePublished selected) { return fields; }
            });
            context.registerBean(OpenApiContractService.class);
            context.refresh();
        }

        private void singleton(String name, Object bean) { context.getBeanFactory().registerSingleton(name, bean); }
        OpenApiContractService.ResolvedContract resolve() {
            return context.getBean(OpenApiContractService.class).resolve(5L, "AUTH", "R");
        }
        @Override public void close() { context.close(); }
    }

    private interface Invocation { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (target, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(target);
                    default -> target == args[0];
                };
            }
            return invocation.invoke(method.getName(), args);
        });
    }
}
