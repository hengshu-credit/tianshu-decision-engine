package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionRef;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.dto.RuleQueryDTO;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RuleDefinitionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void pagedDefinitionQueriesUseStableCreateTimeAndIdOrder() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinition.class);
        List<String> sqlSegments = new ArrayList<>();
        RuleDefinitionMapper mapper = mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectPage".equals(method.getName())) {
                sqlSegments.add(((LambdaQueryWrapper<RuleDefinition>) args[1]).getSqlSegment());
                return args[0];
            }
            return defaultValue(method.getReturnType());
        });
        RuleDefinitionService service = new RuleDefinitionService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        service.pageList(new RuleQueryDTO());
        service.pageListForProject(1, 10, null, null, null,
                null, null, null, null, null, null, null, null);

        assertEquals(2, sqlSegments.size());
        for (String sqlSegment : sqlSegments) {
            String normalized = sqlSegment.replace("`", "").replace(" ", "").toLowerCase();
            assertTrue(sqlSegment, normalized.contains("orderbycreate_timedesc,iddesc")
                    || normalized.contains("orderbycreatetimedesc,iddesc"));
        }
    }

    @Test
    public void createWithContentInitializesUnpublishedRuleWithoutDraftRevision() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(15L);
        definition.setProjectId(0L);
        definition.setModelType("TABLE");
        definition.setStatus(1);
        definition.setPublishedVersion(9);
        final RuleDefinition[] insertedDefinition = {null};

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                insertedDefinition[0] = (RuleDefinition) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class,
                (proxy, method, args) -> "insert".equals(method.getName()) ? 1 : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "fieldAnalyzer", new RecordingRuleFieldAnalyzer());
        ReflectionTestUtils.setField(service, "lifecycleService", new RuleLifecycleService() {
            @Override
            public RuleRevision ensureDraft(Long definitionId) {
                throw new AssertionError("新建规则不能自动创建草稿修订");
            }
        });

        service.createWithContent(definition);

        assertSame(definition, insertedDefinition[0]);
        assertEquals(Integer.valueOf(0), insertedDefinition[0].getStatus());
        assertNull(insertedDefinition[0].getPublishedVersion());
    }

    @Test
    public void pageListForProjectIncludesRuleFieldMetadataForChildCalls() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(11L);
        definition.setRuleCode("MONTHLY_REPAYMENT_MATRIX");
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(11L);
        input.setFieldName("credit_amount");
        input.setScriptName("CREDIT_AMOUNT");
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setDefinitionId(11L);
        output.setFieldName("monthly_success_repayment_amount");
        output.setScriptName("monthly_success_repayment_amount");

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectPage".equals(method.getName())) {
                Page<RuleDefinition> page = (Page<RuleDefinition>) args[0];
                page.setRecords(Collections.singletonList(definition));
                page.setTotal(1);
                return page;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleDefinitionOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(output);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleDefinitionInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(input);
            return defaultValue(method.getReturnType());
        }));

        IPage<RuleDefinition> page = service.pageListForProject(1, 20, 7L, null, null,
                null, null, null, null, null, null, null, null);

        assertEquals(1, page.getRecords().size());
        assertEquals(1, page.getRecords().get(0).getInputFieldsJson().size());
        assertEquals("CREDIT_AMOUNT",
                page.getRecords().get(0).getInputFieldsJson().get(0).getScriptName());
        assertEquals(1, page.getRecords().get(0).getOutputFieldsJson().size());
        assertEquals("monthly_success_repayment_amount",
                page.getRecords().get(0).getOutputFieldsJson().get(0).getScriptName());
    }

    @Test
    public void projectListMarksOnlyLinkedGlobalRulesWithBindingIds() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition projectRule = new RuleDefinition();
        projectRule.setId(11L);
        projectRule.setProjectId(9L);
        projectRule.setScope("PROJECT");
        RuleDefinition globalRule = new RuleDefinition();
        globalRule.setId(30L);
        globalRule.setProjectId(0L);
        globalRule.setScope("GLOBAL");
        RuleDefinitionRef binding = new RuleDefinitionRef();
        binding.setId(15L);
        binding.setDefinitionId(30L);
        binding.setProjectId(9L);
        final int[] bindingQueries = {0};

        ReflectionTestUtils.setField(service, "baseMapper",
                mapper(RuleDefinitionMapper.class,
                        (proxy, method, args) -> {
                            if ("selectPage".equals(method.getName())) {
                                Page<RuleDefinition> page =
                                        (Page<RuleDefinition>) args[0];
                                page.setRecords(Arrays.asList(
                                        projectRule, globalRule));
                                page.setTotal(2);
                                return page;
                            }
                            return defaultValue(method.getReturnType());
                        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> "selectList".equals(
                                method.getName())
                                ? Collections.emptyList()
                                : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> "selectList".equals(
                                method.getName())
                                ? Collections.emptyList()
                                : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "refMapper",
                mapper(RuleDefinitionRefMapper.class,
                        (proxy, method, args) -> {
                            if ("selectList".equals(method.getName())) {
                                bindingQueries[0]++;
                                return Collections.singletonList(binding);
                            }
                            return defaultValue(method.getReturnType());
                        }));

        IPage<RuleDefinition> page = service.pageListForProject(
                1, 20, 9L, null, null, null, null,
                null, null, null, null, null, null);

        assertNull(page.getRecords().get(0).getProjectBindingId());
        assertEquals(Long.valueOf(15L),
                page.getRecords().get(1).getProjectBindingId());
        assertEquals(1, bindingQueries[0]);
    }

    @Test
    public void linkedGlobalRuleIsAvailableInsideProject() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(88L);
        definition.setProjectId(0L);
        definition.setScope("GLOBAL");

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return definition;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "refMapper", mapper(RuleDefinitionRefMapper.class, (proxy, method, args) -> {
            if ("selectCount".equals(method.getName())) return 1L;
            return defaultValue(method.getReturnType());
        }));

        assertTrue(service.isDefinitionAvailableInProject(88L, 7L));
    }

    @Test
    public void getVersionByIdRequiresDefinitionOwnership() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinitionVersion snapshot = new RuleDefinitionVersion();
        snapshot.setId(81L);
        snapshot.setDefinitionId(30L);
        snapshot.setVersion(4);
        snapshot.setModelJson("{\"source\":\"v4\"}");
        ReflectionTestUtils.setField(service, "versionMapper",
                mapper(RuleDefinitionVersionMapper.class, (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return snapshot;
                    return defaultValue(method.getReturnType());
                }));

        assertSame(snapshot, service.getVersionById(30L, 81L));
        assertNull(service.getVersionById(31L, 81L));
    }

    @Test
    public void compareVersionsReportsChangedSections() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinitionVersion left = version(1, "{\"a\":1}", "return 1;");
        RuleDefinitionVersion right = version(2, "{\"a\":2}", "return 1;");
        left.setOpenApiConfigJson("{\"enabled\":false}");
        right.setOpenApiConfigJson("{\"enabled\":true}");
        final int[] calls = {0};
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleDefinitionVersionMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) {
                return calls[0]++ == 0 ? left : right;
            }
            return defaultValue(method.getReturnType());
        }));

        Map<String, Object> result = service.compareVersions(1L, 1, 2);

        assertSame(left, result.get("left"));
        assertSame(right, result.get("right"));
        assertTrue((Boolean) result.get("modelJsonChanged"));
        assertFalse((Boolean) result.get("compiledScriptChanged"));
        assertTrue((Boolean) result.get("openApiConfigChanged"));
    }

    @Test
    public void rollbackToVersionRestoresSnapshotAndRefreshesFields() {
        RuleDefinitionService service = new RuleDefinitionService();
        RuleDefinition definition = new RuleDefinition();
        definition.setId(10L);
        definition.setProjectId(7L);
        definition.setModelType("DECISION_TABLE");
        definition.setCurrentVersion(3);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setId(20L);
        content.setDefinitionId(10L);
        RuleDefinitionVersion snapshot = version(2, "{\"rules\":[]}", "return 2;");
        snapshot.setCompiledType("QL");
        snapshot.setOpenApiConfigJson("{\"enabled\":true}");
        RuleRevision draft = new RuleRevision();
        draft.setId(6L);
        draft.setDefinitionId(10L);
        draft.setState("DRAFT");
        draft.setLockVersion(2);
        final RuleDefinitionContent[] updatedContent = {null};
        final RuleDraftSaveRequest[] savedRequest = {null};

        ReflectionTestUtils.setField(service, "baseMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return definition;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return content;
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            if ("updateById".equals(method.getName())) {
                updatedContent[0] = (RuleDefinitionContent) args[0];
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "versionMapper", mapper(RuleDefinitionVersionMapper.class, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) return snapshot;
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "lifecycleService",
                new RuleLifecycleService() {
                    @Override
                    public RuleRevision createDraft(
                            Long definitionId, Long baseRevisionId) {
                        return draft;
                    }
                });
        ReflectionTestUtils.setField(service, "ruleDraftService",
                new RuleDraftService() {
                    @Override
                    public RuleDraftSaveResponse save(
                            RuleDraftSaveRequest request) {
                        savedRequest[0] = request;
                        return new RuleDraftSaveResponse();
                    }
                });

        service.rollbackToVersion(10L, 2);

        assertEquals(Long.valueOf(10L), savedRequest[0].getDefinitionId());
        assertEquals(Long.valueOf(6L), savedRequest[0].getRevisionId());
        assertEquals(Integer.valueOf(2), savedRequest[0].getLockVersion());
        assertEquals("{\"rules\":[]}", savedRequest[0].getModelJson());
        assertEquals("{\"enabled\":true}",
                savedRequest[0].getOpenApiConfigJson());
        assertEquals(Boolean.TRUE,
                savedRequest[0].getUpdateOpenApiConfig());
        assertSame(content, updatedContent[0]);
        assertEquals("rollback to v2", updatedContent[0].getCompileMessage());
    }

    @Test
    public void saveScriptDelegatesCallerSuppliedAtomicSaveContract() {
        RuleDefinitionService service = new RuleDefinitionService();
        final RuleDraftSaveRequest[] saved = {null};
        ReflectionTestUtils.setField(service, "ruleDraftService",
                new RuleDraftService() {
                    @Override
                    public RuleDraftSaveResponse save(
                            RuleDraftSaveRequest request) {
                        saved[0] = request;
                        return new RuleDraftSaveResponse();
                    }
                });
        RuleDraftSaveRequest request = new RuleDraftSaveRequest();
        request.setDefinitionId(10L);
        request.setRevisionId(6L);
        request.setLockVersion(3);
        request.setModelJson("{\"script\":\"score = amount * 2\","
                + "\"scriptVarRefs\":[{\"varId\":9,"
                + "\"refType\":\"VARIABLE\",\"refCode\":\"amount\"}]}");

        service.saveScript(request);

        assertSame(request, saved[0]);
        JSONObject model = JSON.parseObject(request.getModelJson());
        assertEquals("score = amount * 2", model.getString("script"));
        assertEquals(Long.valueOf(9L), model.getJSONArray("scriptVarRefs")
                .getJSONObject(0).getLong("varId"));
    }

    @Test
    public void legacySaveAndRefreshSignaturesRequireExplicitDraftContract() {
        RuleDefinitionService service = new RuleDefinitionService();
        ReflectionTestUtils.setField(service, "ruleDraftService",
                new RuleDraftService() {
                    @Override
                    public RuleDraftSaveResponse save(
                            RuleDraftSaveRequest request) {
                        throw new AssertionError(
                                "frozen revisions must not be saved");
                    }
                });

        RuleGovernanceException saveError = org.junit.Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.saveContent(10L, "{}"));
        RuleGovernanceException refreshError = org.junit.Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.refreshFields(10L, "{}", "SCRIPT"));
        RuleGovernanceException scriptError = org.junit.Assert.assertThrows(
                RuleGovernanceException.class,
                () -> service.saveScript(10L, "score = 1"));

        assertEquals(400, saveError.getHttpStatus());
        assertEquals("DRAFT_SAVE_CONTRACT_REQUIRED",
                saveError.getCode());
        assertEquals("DRAFT_SAVE_CONTRACT_REQUIRED",
                refreshError.getCode());
        assertEquals("DRAFT_SAVE_CONTRACT_REQUIRED",
                scriptError.getCode());
    }

    @Test
    public void refreshFieldsDelegatesCompleteCallerSuppliedContract() {
        RuleDefinitionService service = new RuleDefinitionService();
        final RuleDraftSaveRequest[] saved = {null};
        RuleDraftSaveResponse expected = new RuleDraftSaveResponse();
        ReflectionTestUtils.setField(service, "ruleDraftService",
                new RuleDraftService() {
                    @Override
                    public RuleDraftSaveResponse save(
                            RuleDraftSaveRequest request) {
                        saved[0] = request;
                        return expected;
                    }
                });
        RuleDraftSaveRequest request = new RuleDraftSaveRequest();
        request.setDefinitionId(10L);
        request.setRevisionId(6L);
        request.setLockVersion(3);
        request.setModelJson("{\"script\":\"score = 1\"}");

        RuleDraftSaveResponse actual = service.refreshFields(request);

        assertSame(expected, actual);
        assertSame(request, saved[0]);
    }

    @Test
    public void deleteWithContentDeletesApiDocumentationScenarios() {
        RuleDefinitionService service = new RuleDefinitionService();
        RecordingApiDocScenarioService scenarioService = new RecordingApiDocScenarioService();
        ReflectionTestUtils.setField(service, "baseMapper",
                mapper(RuleDefinitionMapper.class, (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "contentMapper",
                mapper(RuleDefinitionContentMapper.class,
                        (proxy, method, args) -> defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(service, "apiDocScenarioService", scenarioService);

        service.deleteWithContent(12L);

        assertEquals(Long.valueOf(12L), scenarioService.deletedDefinitionId);
    }

    private static RuleDefinitionVersion version(int version, String modelJson, String script) {
        RuleDefinitionVersion result = new RuleDefinitionVersion();
        result.setDefinitionId(1L);
        result.setVersion(version);
        result.setModelJson(modelJson);
        result.setCompiledScript(script);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        return 0;
    }

    private static class RecordingRuleFieldAnalyzer extends RuleFieldAnalyzer {
        private Long definitionId;
        private String modelJson;
        private String modelType;
        private Long projectId;

        @Override
        public void analyzeAndPersist(Long definitionId, String modelJson, String modelType, Long projectId) {
            this.definitionId = definitionId;
            this.modelJson = modelJson;
            this.modelType = modelType;
            this.projectId = projectId;
        }
    }

    private static class RecordingApiDocScenarioService extends RuleApiDocScenarioService {
        private Long deletedDefinitionId;

        @Override
        public void deleteByDefinition(Long definitionId) {
            this.deletedDefinitionId = definitionId;
        }
    }
}
