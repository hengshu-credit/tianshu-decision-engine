package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class RuleDraftServiceTest {

    private FixtureService fixture;
    private RuleDraftService service;
    private RuleRevision beforeRevision;
    private RuleDefinitionContent beforeContent;
    private List<RuleDefinitionInputField> beforeFields;
    private List<RuleDefinitionOutputField> beforeOutputFields;
    private Integer beforeDesignVersion;

    @Before
    public void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                RuleDefinitionContent.class);
        fixture = new FixtureService();
        beforeRevision = copy(fixture.databaseRevision);
        beforeContent = copy(fixture.databaseContent);
        beforeFields = copyInputs(fixture.databaseFields);
        beforeOutputFields =
                copyOutputs(fixture.databaseOutputFields);
        beforeDesignVersion = fixture.definition.getCurrentVersion();

        FixtureTransactionManager transactionManager =
                new FixtureTransactionManager(fixture);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(fixture);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        service = (RuleDraftService) proxyFactory.getProxy();
    }

    @Test
    public void saveUpdatesRevisionContentAndFieldProjectionWithSameModelJson() {
        RuleDraftSaveRequest request = request(30L, 6L, 0, modelJson());

        RuleDraftSaveResponse saved = service.save(request);

        assertEquals(modelJson(), saved.getRevision().getModelJson());
        assertEquals(modelJson(), fixture.savedContent.getModelJson());
        assertEquals(inputNames(fixture.persistedFields),
                inputNames(fixture.resolvedFields.getInputFields()));
        assertEquals(outputNames(fixture.persistedOutputFields),
                outputNames(
                        fixture.resolvedFields.getOutputFields()));
        assertEquals(outputNames(fixture.databaseOutputFields),
                outputNames(
                        fixture.resolvedFields.getOutputFields()));
        assertEquals(1, saved.getRevision().getLockVersion().intValue());
        assertEquals(Integer.valueOf(6), saved.getDesignVersion());
        assertTrue(saved.isCompileSuccess());
        assertEquals(List.of("revision", "content", "fields", "designVersion"),
                fixture.writeOrder);
    }

    @Test
    public void restoreEffectiveProjectionReplacesRejectedDraftContentAndFields() {
        RuleRevision effective = new RuleRevision();
        effective.setDefinitionId(30L);
        effective.setModelJson("{\"script\":\"effective = 1\"}");
        effective.setCompiledScript("return effective;");
        effective.setCompiledType("QLEXPRESS");
        effective.setOpenApiConfigJson("{\"enabled\":true}");

        service.restoreEffectiveProjection(effective);

        assertEquals(effective.getModelJson(),
                fixture.databaseContent.getModelJson());
        assertEquals(effective.getCompiledScript(),
                fixture.databaseContent.getCompiledScript());
        assertEquals(effective.getOpenApiConfigJson(),
                fixture.databaseContent.getOpenApiConfigJson());
        assertEquals(Integer.valueOf(1),
                fixture.databaseContent.getCompileStatus());
        assertEquals(inputNames(fixture.resolvedFields.getInputFields()),
                inputNames(fixture.databaseFields));
        assertEquals(outputNames(fixture.resolvedFields.getOutputFields()),
                outputNames(fixture.databaseOutputFields));
        assertEquals(List.of("content", "fields", "designVersion"),
                fixture.writeOrder);
    }

    @Test
    public void failureAfterRevisionUpdateRollsBackAllThreeProjections() {
        fixture.failFieldPersistence = true;

        assertThrows(IllegalStateException.class,
                () -> service.save(request(30L, 6L, 0, modelJson())));

        assertEquals(beforeRevision, fixture.databaseRevision);
        assertEquals(beforeContent, fixture.databaseContent);
        assertEquals(beforeFields, fixture.databaseFields);
        assertEquals(beforeOutputFields,
                fixture.databaseOutputFields);
        assertEquals(beforeDesignVersion,
                fixture.definition.getCurrentVersion());
    }

    @Test
    public void outputFailureAfterInputWriteRollsBackAllFiveSnapshots() {
        fixture.failOutputPersistence = true;

        assertThrows(IllegalStateException.class,
                () -> service.save(
                        request(30L, 6L, 0, modelJson())));

        assertTrue(fixture.inputWrittenBeforeOutputFailure);
        assertTrue(fixture.outputProjectionMutatedBeforeFailure);
        assertFalse(beforeOutputFields.equals(
                fixture.preRollbackOutputFields));
        assertEquals(beforeRevision, fixture.databaseRevision);
        assertEquals(beforeContent, fixture.databaseContent);
        assertEquals(beforeFields, fixture.databaseFields);
        assertEquals(beforeOutputFields,
                fixture.databaseOutputFields);
        assertEquals(beforeDesignVersion,
                fixture.definition.getCurrentVersion());
    }

    @Test
    public void contentWriteReturningZeroRollsBackRevisionAndAllSnapshots() {
        fixture.failContentPersistence = true;

        assertThrows(IllegalStateException.class,
                () -> service.save(request(30L, 6L, 0, modelJson())));

        assertEquals(beforeRevision, fixture.databaseRevision);
        assertEquals(beforeContent, fixture.databaseContent);
        assertEquals(beforeFields, fixture.databaseFields);
        assertEquals(beforeOutputFields,
                fixture.databaseOutputFields);
        assertEquals(beforeDesignVersion,
                fixture.definition.getCurrentVersion());
    }

    @Test
    public void designVersionCasConflictRollsBackEveryProjection() {
        fixture.failDesignVersionCas = true;

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class,
                () -> service.save(request(30L, 6L, 0, modelJson())));

        assertEquals(409, error.getHttpStatus());
        assertEquals("DRAFT_LOCK_CONFLICT", error.getCode());
        assertEquals(beforeRevision, fixture.databaseRevision);
        assertEquals(beforeContent, fixture.databaseContent);
        assertEquals(beforeFields, fixture.databaseFields);
        assertEquals(beforeOutputFields,
                fixture.databaseOutputFields);
        assertEquals(beforeDesignVersion,
                fixture.definition.getCurrentVersion());
    }

    @Test
    public void unfinishedDraftIsSavedWithDiagnosticsAndClearsOldExecutableScript() {
        fixture.compileResult =
                CompileResult.fail("QL_PARSE_ERROR: 第 1 行语法错误");
        fixture.databaseRevision.setCompiledScript("return old;");
        fixture.databaseContent.setCompiledScript("return old;");
        fixture.databaseContent.setCompileStatus(1);

        String unfinished = "{\"script\":\"_result = {\"}";
        RuleDraftSaveResponse saved = service.save(request(30L, 6L, 0, unfinished));
        assertTrue(saved.getIssues().stream()
                .anyMatch(issue -> "QL_PARSE_ERROR".equals(issue.getCode())));
        assertFalse(saved.isCompileSuccess());
        assertEquals("DRAFT", saved.getRevision().getState());
        assertEquals(Integer.valueOf(1), saved.getRevision().getLockVersion());
        assertEquals(unfinished, fixture.databaseRevision.getModelJson());
        assertEquals(unfinished, fixture.databaseContent.getModelJson());
        assertEquals(Integer.valueOf(2), fixture.databaseContent.getCompileStatus());
        assertNull(fixture.databaseRevision.getCompiledScript());
        assertNull(fixture.databaseContent.getCompiledScript());
    }

    @Test
    public void unknownUppercaseCompilerPrefixDoesNotLeakAsIssueCode() {
        fixture.compileResult =
                CompileResult.fail("DATABASE_TIMEOUT: internal detail");

        RuleDraftSaveResponse saved = service.save(request(30L, 6L, 0, modelJson()));
        assertTrue(saved.getIssues().stream()
                .anyMatch(issue -> "COMPILE_FAILED".equals(issue.getCode())));
        assertFalse(saved.getIssues().stream()
                .anyMatch(issue -> "DATABASE_TIMEOUT".equals(issue.getCode())));
    }

    @Test
    public void missingRevisionOrLockVersionIsRejectedBeforeAnyWrite() {
        RuleDraftSaveRequest request = request(30L, null, null, modelJson());

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class, () -> service.save(request));

        assertEquals(400, error.getHttpStatus());
        assertEquals("DRAFT_SAVE_CONTRACT_INVALID", error.getCode());
        assertTrue(fixture.writeOrder.isEmpty());
    }

    @Test
    public void staleLockVersionRejectsSaveAndRollsBack() {
        fixture.databaseRevision.setLockVersion(2);

        RuleGovernanceException error = assertThrows(
                RuleGovernanceException.class,
                () -> service.save(request(30L, 6L, 1, modelJson())));

        assertEquals(409, error.getHttpStatus());
        assertEquals("DRAFT_LOCK_CONFLICT", error.getCode());
        assertEquals(Integer.valueOf(2), fixture.databaseRevision.getLockVersion());
        assertEquals(beforeContent, fixture.databaseContent);
        assertEquals(beforeFields, fixture.databaseFields);
    }

    @Test
    public void contentMapperExplicitlyClearsNullableProjectionFields() {
        RuleDraftService target = new RuleDraftService();
        List<String> updates = new ArrayList<>();
        RuleDefinitionContentMapper mapper = (RuleDefinitionContentMapper) Proxy.newProxyInstance(
                RuleDefinitionContentMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionContentMapper.class}, (proxy, method, args) -> {
                    if ("update".equals(method.getName())) {
                        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) args[1];
                        updates.add(wrapper.getSqlSet());
                        assertEquals(4, wrapper.getParamNameValuePairs().values().stream()
                                .filter(java.util.Objects::isNull).count());
                        return 1;
                    }
                    throw new AssertionError("Unexpected mapper call: " + method.getName());
                });
        ReflectionTestUtils.setField(target, "contentMapper", mapper);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setId(16L);
        target.persistContent(content);
        assertEquals(1, updates.size());
        assertTrue(updates.get(0).contains("compiled_script="));
        assertTrue(updates.get(0).contains("compiled_type="));
        assertTrue(updates.get(0).contains("compile_message="));
        assertTrue(updates.get(0).contains("open_api_config_json="));
    }

    @Test
    public void contentMapperUpdateReturningZeroIsRejected() {
        RuleDraftService target = new RuleDraftService();
        ReflectionTestUtils.setField(target, "contentMapper",
                zeroWriteContentMapper());
        RuleDefinitionContent content =
                new RuleDefinitionContent();
        content.setId(16L);
        content.setDefinitionId(30L);

        assertThrows(IllegalStateException.class,
                () -> target.persistContent(content));
    }

    @Test
    public void contentMapperInsertReturningZeroIsRejected() {
        RuleDraftService target = new RuleDraftService();
        ReflectionTestUtils.setField(target, "contentMapper",
                zeroWriteContentMapper());
        RuleDefinitionContent content =
                new RuleDefinitionContent();
        content.setDefinitionId(30L);

        assertThrows(IllegalStateException.class,
                () -> target.persistContent(content));
    }

    private static String modelJson() {
        return "{\"script\":\"riskScore = applicant.age + 1\","
                + "\"scriptVarRefs\":[{\"refCode\":\"applicant.age\","
                + "\"varId\":91,\"refType\":\"DATA_OBJECT\"}]}";
    }

    private static RuleDraftSaveRequest request(Long definitionId, Long revisionId,
                                                Integer lockVersion, String modelJson) {
        RuleDraftSaveRequest request = new RuleDraftSaveRequest();
        request.setDefinitionId(definitionId);
        request.setRevisionId(revisionId);
        request.setLockVersion(lockVersion);
        request.setModelJson(modelJson);
        return request;
    }

    private static List<String> inputNames(List<RuleDefinitionInputField> fields) {
        return fields.stream().map(RuleDefinitionInputField::getFieldName)
                .collect(Collectors.toList());
    }

    private static List<String> outputNames(
            List<RuleDefinitionOutputField> fields) {
        return fields.stream()
                .map(RuleDefinitionOutputField::getFieldName)
                .collect(Collectors.toList());
    }

    private static RuleDefinitionContentMapper zeroWriteContentMapper() {
        return (RuleDefinitionContentMapper) Proxy.newProxyInstance(
                RuleDefinitionContentMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionContentMapper.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == int.class || type == Integer.class) {
                        return 0;
                    }
                    if (type == boolean.class
                            || type == Boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static RuleRevision copy(RuleRevision source) {
        RuleRevision copy = new RuleRevision();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setRevisionNo(source.getRevisionNo());
        copy.setState(source.getState());
        copy.setBaseRevisionId(source.getBaseRevisionId());
        copy.setModelJson(source.getModelJson());
        copy.setCompiledScript(source.getCompiledScript());
        copy.setCompiledType(source.getCompiledType());
        copy.setOpenApiConfigJson(source.getOpenApiConfigJson());
        copy.setInputSchemaJson(source.getInputSchemaJson());
        copy.setOutputSchemaJson(source.getOutputSchemaJson());
        copy.setContentDigest(source.getContentDigest());
        copy.setValidationReportDigest(source.getValidationReportDigest());
        copy.setLockVersion(source.getLockVersion());
        return copy;
    }

    private static RuleDefinitionContent copy(RuleDefinitionContent source) {
        RuleDefinitionContent copy = new RuleDefinitionContent();
        copy.setId(source.getId());
        copy.setDefinitionId(source.getDefinitionId());
        copy.setModelJson(source.getModelJson());
        copy.setCompiledScript(source.getCompiledScript());
        copy.setCompiledType(source.getCompiledType());
        copy.setCompileStatus(source.getCompileStatus());
        copy.setCompileMessage(source.getCompileMessage());
        copy.setCompileTime(source.getCompileTime());
        copy.setScriptMode(source.getScriptMode());
        copy.setOpenApiConfigJson(source.getOpenApiConfigJson());
        return copy;
    }

    private static List<RuleDefinitionInputField> copyInputs(
            List<RuleDefinitionInputField> source) {
        List<RuleDefinitionInputField> copy = new ArrayList<>();
        for (RuleDefinitionInputField field : source) {
            RuleDefinitionInputField item = new RuleDefinitionInputField();
            item.setId(field.getId());
            item.setDefinitionId(field.getDefinitionId());
            item.setVarId(field.getVarId());
            item.setRefType(field.getRefType());
            item.setFieldName(field.getFieldName());
            item.setScriptName(field.getScriptName());
            item.setFieldType(field.getFieldType());
            item.setSortOrder(field.getSortOrder());
            item.setStatus(field.getStatus());
            copy.add(item);
        }
        return copy;
    }

    private static List<RuleDefinitionOutputField> copyOutputs(
            List<RuleDefinitionOutputField> source) {
        List<RuleDefinitionOutputField> copy = new ArrayList<>();
        for (RuleDefinitionOutputField field : source) {
            RuleDefinitionOutputField item =
                    new RuleDefinitionOutputField();
            item.setId(field.getId());
            item.setDefinitionId(field.getDefinitionId());
            item.setVarId(field.getVarId());
            item.setRefType(field.getRefType());
            item.setFieldName(field.getFieldName());
            item.setFieldLabel(field.getFieldLabel());
            item.setScriptName(field.getScriptName());
            item.setFieldType(field.getFieldType());
            item.setTransformType(field.getTransformType());
            item.setTransformParams(field.getTransformParams());
            item.setValidValues(field.getValidValues());
            item.setSortOrder(field.getSortOrder());
            item.setStatus(field.getStatus());
            item.setCreateTime(field.getCreateTime());
            copy.add(item);
        }
        return copy;
    }

    static class FixtureService extends RuleDraftService {
        private final RuleDefinition definition = new RuleDefinition();
        private RuleRevision databaseRevision = new RuleRevision();
        private RuleDefinitionContent databaseContent =
                new RuleDefinitionContent();
        private List<RuleDefinitionInputField> databaseFields =
                new ArrayList<>();
        private List<RuleDefinitionOutputField> databaseOutputFields =
                new ArrayList<>();
        private RuleDefinitionContent savedContent;
        private List<RuleDefinitionInputField> persistedFields =
                new ArrayList<>();
        private List<RuleDefinitionOutputField> persistedOutputFields =
                new ArrayList<>();
        private final RuleFieldAnalyzer.ResolvedFields resolvedFields;
        private CompileResult compileResult =
                CompileResult.ok("return riskScore;", "QLEXPRESS");
        private boolean failFieldPersistence;
        private boolean failOutputPersistence;
        private boolean inputWrittenBeforeOutputFailure;
        private boolean outputProjectionMutatedBeforeFailure;
        private List<RuleDefinitionOutputField>
                preRollbackOutputFields;
        private boolean failContentPersistence;
        private boolean failDesignVersionCas;
        private final List<String> writeOrder = new ArrayList<>();

        FixtureService() {
            definition.setId(30L);
            definition.setProjectId(4L);
            definition.setModelType("SCRIPT");
            definition.setCurrentVersion(5);

            databaseRevision.setId(6L);
            databaseRevision.setDefinitionId(30L);
            databaseRevision.setRevisionNo(2);
            databaseRevision.setState("DRAFT");
            databaseRevision.setModelJson("{\"script\":\"old = 1\"}");
            databaseRevision.setLockVersion(0);

            databaseContent.setId(16L);
            databaseContent.setDefinitionId(30L);
            databaseContent.setModelJson("{\"script\":\"old = 1\"}");
            databaseContent.setCompileStatus(0);

            RuleDefinitionInputField old = input("old", 1L);
            databaseFields.add(old);
            databaseOutputFields.add(
                    output("oldDecision", 2L));
            RuleDefinitionInputField resolved = input("applicant.age", 91L);
            RuleDefinitionOutputField resolvedOutput =
                    output("decision", 92L);
            resolvedFields = new RuleFieldAnalyzer.ResolvedFields(
                    Collections.singletonList(resolved),
                    Collections.singletonList(resolvedOutput));
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return Long.valueOf(30L).equals(definitionId) ? definition : null;
        }

        @Override
        protected RuleRevision requireEditableDraft(Long definitionId,
                                                    Long revisionId) {
            if (!Long.valueOf(30L).equals(definitionId)
                    || !Long.valueOf(6L).equals(revisionId)
                    || !"DRAFT".equals(databaseRevision.getState())) {
                return null;
            }
            return copy(databaseRevision);
        }

        @Override
        protected RuleFieldAnalyzer.ResolvedFields resolveFields(
                RuleDefinition definition, String modelJson) {
            return resolvedFields;
        }

        @Override
        protected RuleDependencyClosureService.DependencyClosure
        resolveDependencies(RuleDefinition definition, RuleRevision revision,
                            RuleFieldAnalyzer.ResolvedFields fields) {
            return RuleDependencyClosureService.DependencyClosure.of(
                    Collections.emptyList(), Collections.emptyList());
        }

        @Override
        protected CompileResult compile(RuleDefinition definition,
                                        String modelJson) {
            return compileResult;
        }

        @Override
        protected boolean compareAndSetDraft(RuleRevision revision,
                                             int expectedLockVersion) {
            if (!"DRAFT".equals(databaseRevision.getState())
                    || !Integer.valueOf(expectedLockVersion)
                    .equals(databaseRevision.getLockVersion())) {
                return false;
            }
            writeOrder.add("revision");
            databaseRevision = copy(revision);
            return true;
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return copy(databaseContent);
        }

        @Override
        protected void persistContent(RuleDefinitionContent content) {
            writeOrder.add("content");
            databaseContent = copy(content);
            savedContent = copy(content);
            if (failContentPersistence) {
                throw new IllegalStateException(
                        "content mapper returned zero");
            }
        }

        @Override
        protected void persistResolvedFields(
                Long definitionId,
                RuleFieldAnalyzer.ResolvedFields fields) {
            writeOrder.add("fields");
            databaseFields = new ArrayList<>();
            databaseOutputFields = new ArrayList<>();
            databaseFields.addAll(
                    copyInputs(fields.getInputFields()));
            persistedFields = copyInputs(fields.getInputFields());
            if (failFieldPersistence) {
                throw new IllegalStateException("field persistence failed");
            }
            for (RuleDefinitionOutputField output
                    : fields.getOutputFields()) {
                databaseOutputFields.add(
                        copyOutputs(Collections.singletonList(output))
                                .get(0));
                if (failOutputPersistence) {
                    inputWrittenBeforeOutputFailure =
                            !databaseFields.isEmpty();
                    outputProjectionMutatedBeforeFailure =
                            !databaseOutputFields.isEmpty();
                    throw new IllegalStateException(
                            "output persistence failed after input write");
                }
            }
            persistedOutputFields =
                    copyOutputs(fields.getOutputFields());
        }

        @Override
        protected int incrementDesignVersion(RuleDefinition definition) {
            writeOrder.add("designVersion");
            if (failDesignVersionCas) {
                throw new RuleGovernanceException(
                        409, "DRAFT_LOCK_CONFLICT",
                        "design version conflict",
                        Collections.emptyList());
            }
            definition.setCurrentVersion(definition.getCurrentVersion() + 1);
            return definition.getCurrentVersion();
        }

        private static RuleDefinitionInputField input(String name, Long varId) {
            RuleDefinitionInputField field = new RuleDefinitionInputField();
            field.setDefinitionId(30L);
            field.setVarId(varId);
            field.setRefType("DATA_OBJECT");
            field.setFieldName(name);
            field.setScriptName(name);
            field.setFieldType("NUMBER");
            field.setStatus(1);
            return field;
        }

        private static RuleDefinitionOutputField output(
                String name, Long varId) {
            RuleDefinitionOutputField field =
                    new RuleDefinitionOutputField();
            field.setDefinitionId(30L);
            field.setVarId(varId);
            field.setRefType("VARIABLE");
            field.setFieldName(name);
            field.setFieldLabel(name);
            field.setScriptName(name);
            field.setFieldType("STRING");
            field.setStatus(1);
            return field;
        }
    }

    private static final class FixtureTransactionManager
            extends AbstractPlatformTransactionManager {
        private final FixtureService fixture;
        private RuleRevision revisionSnapshot;
        private RuleDefinitionContent contentSnapshot;
        private List<RuleDefinitionInputField> fieldSnapshot;
        private List<RuleDefinitionOutputField> outputFieldSnapshot;
        private Integer designVersionSnapshot;

        private FixtureTransactionManager(FixtureService fixture) {
            this.fixture = fixture;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction,
                               TransactionDefinition definition) {
            revisionSnapshot = copy(fixture.databaseRevision);
            contentSnapshot = copy(fixture.databaseContent);
            fieldSnapshot = copyInputs(fixture.databaseFields);
            outputFieldSnapshot =
                    copyOutputs(fixture.databaseOutputFields);
            designVersionSnapshot =
                    fixture.definition.getCurrentVersion();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            fixture.preRollbackOutputFields =
                    copyOutputs(fixture.databaseOutputFields);
            fixture.databaseRevision = copy(revisionSnapshot);
            fixture.databaseContent = copy(contentSnapshot);
            fixture.databaseFields = copyInputs(fieldSnapshot);
            fixture.databaseOutputFields =
                    copyOutputs(outputFieldSnapshot);
            fixture.definition.setCurrentVersion(
                    designVersionSnapshot);
        }
    }
}
