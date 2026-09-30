package com.hengshucredit.rule.server.transfer;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class OfflineRuleCodeConflictTest {
    @BeforeClass public static void metadata() {
        OfflineProjectBindingTest.metadata();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinition.class);
    }

    @Test public void previewDetectsWholeDatabaseRuleCodeConflictWhenCreatingProject() {
        var f = fixture();
        var result = f.previewService.preview(bundle(), options("SUFFIX", true));
        List<?> conflicts = (List<?>) result.get("conflicts");
        assertEquals(1, conflicts.size());
        assertEquals("RULE_CODE_CONFLICT", ((TransferConflict) conflicts.get(0)).conflictType());
        assertEquals("SUFFIX", ((TransferConflict) conflicts.get(0)).recommendedAction());
    }

    @Test public void foreignProjectRuleIsNeverReusedOrOverwritten() {
        for (String policy : List.of("REUSE", "OVERWRITE")) {
            var f = fixture();
            assertThrows(IllegalArgumentException.class, () -> f.service.apply(bundle(), options(policy, false), "tester"));
            assertTrue(f.drafts.isEmpty());
        }
    }

    @Test public void crossProjectCopyUsesRequestedSuffixAndTargetProject() {
        var f = fixture();
        var created = new java.util.ArrayList<RuleDefinition>();
        ReflectionTestUtils.setField(f.service, "definitionService", new com.hengshucredit.rule.server.service.RuleDefinitionService() {
            @Override public RuleDefinition createWithContent(RuleDefinition rule) {
                rule.setId(200L); created.add(rule); return rule;
            }
        });
        ReflectionTestUtils.setField(f.service, "lifecycleService", new com.hengshucredit.rule.server.service.RuleLifecycleService() {
            @Override public com.hengshucredit.rule.model.entity.RuleRevision createDraft(Long id, Long base) {
                var revision = new com.hengshucredit.rule.model.entity.RuleRevision(); revision.setId(300L); return revision;
            }
        });
        ReflectionTestUtils.setField(f.service, "draftService", new com.hengshucredit.rule.server.service.RuleDraftService() {
            @Override public com.hengshucredit.rule.model.dto.RuleDraftSaveResponse save(com.hengshucredit.rule.model.dto.RuleDraftSaveRequest request) {
                return new com.hengshucredit.rule.model.dto.RuleDraftSaveResponse();
            }
        });
        var result = f.service.apply(bundle(), options("SUFFIX", false), "tester");
        assertEquals("nested_copy", created.get(0).getRuleCode());
        assertEquals(Long.valueOf(99), created.get(0).getProjectId());
        assertEquals(200L, ((Map<?, ?>) result.get("resourceIdMapping")).get("RULE:1"));
    }

    private static OfflineProjectBindingTest.Fixture fixture() {
        var f = new OfflineProjectBindingTest.Fixture();
        RuleDefinition existing = new RuleDefinition(); existing.setId(77L); existing.setProjectId(42L); existing.setRuleCode("nested");
        RuleDefinitionMapper mapper = (RuleDefinitionMapper) Proxy.newProxyInstance(RuleDefinitionMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionMapper.class}, (proxy, method, args) -> {
                    var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) args[0]; query.getSqlSegment();
                    return query.getParamNameValuePairs().containsValue("nested") ? existing : null;
                });
        ReflectionTestUtils.setField(f.service, "ruleDefinitionMapper", mapper);
        ReflectionTestUtils.setField(f.previewService, "ruleDefinitionMapper", mapper);
        return f;
    }

    private static byte[] bundle() {
        return new TransferBundleCodec().encode(new TransferBundle(List.of("RULE:1"), List.of(new TransferBundle.Resource(
                "RULE:1", Map.of("ruleCode", "nested", "scope", "GLOBAL", "projectId", 0), List.of(), List.of())), List.of()));
    }

    private static TransferImportOptions options(String policy, boolean create) {
        return new TransferImportOptions(create ? null : 99L, "PROJECT", "REUSE", policy, "_copy", create, "target", "目标", Map.of());
    }
}
