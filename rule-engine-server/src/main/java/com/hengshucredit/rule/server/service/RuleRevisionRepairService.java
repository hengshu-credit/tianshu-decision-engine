package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.core.script.QLScriptAnalysis;
import com.hengshucredit.rule.core.script.QLScriptAnalyzer;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleRevisionRepairRequest;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleLifecycleEventMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class RuleRevisionRepairService {
    private static final Set<String> FROZEN_STATES =
            Set.of("REVIEW", "APPROVED", "PUBLISHED", "OFFLINE");

    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleDefinitionContentMapper contentMapper;
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleDefinitionInputFieldMapper inputFieldMapper;
    @Resource
    private RuleDefinitionOutputFieldMapper outputFieldMapper;
    @Resource
    private RuleLifecycleEventMapper eventMapper;
    @Resource
    private QLScriptFieldResolver fieldResolver;
    @Resource
    private RuleLifecycleService lifecycleService;
    @Resource
    private RuleDraftService draftService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    private final QLScriptAnalyzer scriptAnalyzer =
            new QLScriptAnalyzer();

    public RepairPreview preview(Long definitionId) {
        return buildPlan(definitionId).preview;
    }

    @Transactional
    public RuleRevision repair(
            Long definitionId, RuleRevisionRepairRequest request) {
        if (request == null
                || trimToNull(request.getPreviewDigest()) == null) {
            throw governance(400, "REPAIR_REQUEST_INVALID",
                    "previewDigest 为必填项",
                    Collections.singletonList(RuleValidationIssue.error(
                            "REPAIR_REQUEST_INVALID", "$",
                            "修复请求不完整")));
        }
        RuleDefinition lockedDefinition =
                lockDefinition(definitionId);
        RepairPlan plan = buildPlan(
                definitionId, lockedDefinition);
        RepairPreview preview = plan.preview;
        if (!Objects.equals(request.getSourceRevisionId(),
                preview.getSourceRevisionId())
                || !request.getPreviewDigest().equals(
                preview.getPreviewDigest())) {
            throw new RepairPreviewChangedException(
                    "修复预览已变化，请重新预览后执行");
        }
        List<RuleValidationIssue> blockingIssues =
                preview.getIssues().stream()
                        .filter(this::isRepairBlocking)
                        .collect(Collectors.toList());
        if (!preview.getUnresolvedInputs().isEmpty()) {
            for (String input : preview.getUnresolvedInputs()) {
                if (blockingIssues.stream().noneMatch(issue ->
                        "SCRIPT_INPUT_REF_MISSING".equals(
                                issue.getCode())
                                && Objects.equals(input,
                                issue.getDetails().get(
                                        "fieldPath")))) {
                    blockingIssues.add(missingInputIssue(input,
                            preview.getSourceRevisionId()));
                }
            }
        }
        if (!blockingIssues.isEmpty()) {
            String blockingCode =
                    preview.getUnresolvedInputs().isEmpty()
                            ? blockingIssues.get(0).getCode()
                            : "SCRIPT_INPUT_REF_MISSING";
            throw governance(422,
                    blockingCode,
                    "历史稳定 ID 无法覆盖全部脚本输入",
                    blockingIssues);
        }

        RuleRevision draft = plan.draftSnapshot;
        if (draft == null) {
            draft = createDraft(definitionId,
                    preview.getSourceRevisionId());
        }
        RuleDraftSaveRequest saveRequest =
                new RuleDraftSaveRequest();
        saveRequest.setDefinitionId(definitionId);
        saveRequest.setRevisionId(draft.getId());
        saveRequest.setLockVersion(
                draft.getLockVersion() == null
                        ? 0 : draft.getLockVersion());
        saveRequest.setModelJson(plan.repairedModelJson);
        saveRequest.setUpdateOpenApiConfig(false);
        RuleDraftSaveResponse saved = saveDraft(saveRequest);
        List<RuleValidationIssue> saveIssues =
                saved == null || saved.getIssues() == null
                        ? Collections.emptyList()
                        : saved.getIssues();
        List<RuleValidationIssue> unresolvedAfterSave =
                saveIssues.stream()
                        .filter(issue ->
                                "SCRIPT_INPUT_REF_MISSING".equals(
                                        issue.getCode()))
                        .collect(Collectors.toList());
        if (!unresolvedAfterSave.isEmpty()) {
            throw governance(422,
                    "SCRIPT_INPUT_REF_MISSING",
                    "修复后仍有脚本输入缺少稳定 ID",
                    unresolvedAfterSave);
        }
        RuleRevision repaired = saved == null
                ? null : saved.getRevision();
        if (repaired == null) {
            throw new IllegalStateException(
                    "规则草稿修复保存未返回修订");
        }
        insertRepairEvent(repairEvent(
                repaired, preview, plan.recoveredReferences));
        return repaired;
    }

    private RepairPlan buildPlan(Long definitionId) {
        return buildPlan(definitionId,
                loadDefinition(definitionId));
    }

    private RepairPlan buildPlan(
            Long definitionId, RuleDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不存在");
        }
        if (!"SCRIPT".equalsIgnoreCase(
                trimToNull(definition.getModelType()))) {
            throw new IllegalArgumentException(
                    "仅 QL 脚本规则支持历史引用修复");
        }
        RuleDefinitionContent content =
                loadContent(definitionId);
        String currentModelJson = content == null
                ? null : trimToNull(content.getModelJson());
        if (currentModelJson == null) {
            throw new IllegalArgumentException(
                    "规则当前脚本内容不存在");
        }
        JSONObject currentModel;
        try {
            currentModel = JSON.parseObject(currentModelJson);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    "规则当前脚本模型不是有效 JSON", error);
        }
        if (currentModel == null) {
            throw new IllegalArgumentException(
                    "规则当前脚本模型不能为空");
        }

        QLScriptAnalysis analysis = scriptAnalyzer.analyze(
                currentModel.getString("script"));
        List<RuleValidationIssue> issues =
                analysisIssues(analysis);
        List<RuleRevision> revisions =
                safeRevisions(loadRevisions(definitionId));
        if (revisions.stream().filter(Objects::nonNull)
                .filter(revision -> definitionId.equals(revision.getDefinitionId())
                        && "DRAFT".equals(revision.getState()))
                .limit(2).count() > 1) {
            throw governance(409, "DRAFT_ID_REQUIRED",
                    "存在多份草稿，历史修复无法自动选择目标，请打开指定草稿处理",
                    Collections.singletonList(RuleValidationIssue.error(
                            "DRAFT_ID_REQUIRED", "$",
                            "多草稿规则不能通过规则级历史修复入口隐式覆盖")));
        }
        Map<String, CandidateReference> historicalCandidates =
                new LinkedHashMap<>();
        for (RuleRevision revision : revisions) {
            if (revision == null || revision.getId() == null
                    || !definitionId.equals(
                    revision.getDefinitionId())
                    || !FROZEN_STATES.contains(
                    normalize(revision.getState()))) {
                continue;
            }
            JSONObject historicalModel =
                    parseHistoricalModel(revision, issues);
            if (historicalModel == null) {
                continue;
            }
            Map<String, CandidateReference> revisionCandidates =
                    readCandidates(historicalModel.getJSONArray(
                                    "scriptVarRefs"),
                            definition.getProjectId(),
                            revision.getId(), issues);
            for (Map.Entry<String, CandidateReference> entry
                    : revisionCandidates.entrySet()) {
                historicalCandidates.putIfAbsent(
                        entry.getKey(), entry.getValue());
            }
        }

        Map<String, CandidateReference> selected =
                new LinkedHashMap<>();
        Set<String> recoveredKeys = new LinkedHashSet<>();
        List<String> unresolvedInputs = new ArrayList<>();
        for (String input : analysis.getDirectInputs()) {
            List<CandidateReference> historicalMatches =
                    matching(historicalCandidates.values(), input);
            if (historicalMatches.size() == 1) {
                CandidateReference candidate =
                        historicalMatches.get(0);
                selected.put(candidate.identity, candidate);
                recoveredKeys.add(candidate.identity);
                continue;
            }
            if (historicalMatches.size() > 1) {
                issues.add(conflictIssue(input,
                        newestRevisionId(historicalMatches)));
            }
            issues.add(missingInputIssue(input,
                    newestRevisionId(historicalMatches)));
            unresolvedInputs.add(input);
        }
        List<String> recoverableReferenceKeys =
                new ArrayList<>(recoveredKeys);
        Collections.sort(recoverableReferenceKeys);
        unresolvedInputs = unresolvedInputs.stream()
                .distinct().sorted().collect(Collectors.toList());
        Long sourceRevisionId =
                sourceRevisionId(selected.values());
        String repairedModelJson =
                repairedModelJson(currentModel, selected.values());
        boolean projectionDrift = projectionDrift(
                currentModelJson, sourceRevisionId, revisions,
                loadInputFields(definitionId),
                loadOutputFields(definitionId),
                analysis, selected);
        if (projectionDrift) {
            issues.add(RuleValidationIssue.warning(
                            "REVISION_PROJECTION_DRIFT", "$",
                            "历史修订、兼容内容或字段投影存在差异")
                    .withRevisionId(sourceRevisionId)
                    .withSafeDetail("revisionId",
                            sourceRevisionId));
        }
        RuleRevision draftSnapshot =
                currentDraft(definitionId);
        boolean willCreateDraft =
                draftSnapshot == null;
        String previewDigest = previewDigest(
                definitionId, currentModelJson, revisions,
                recoverableReferenceKeys, unresolvedInputs,
                willCreateDraft);
        RepairPreview preview = new RepairPreview(
                sourceRevisionId, recoverableReferenceKeys,
                unresolvedInputs, issues, projectionDrift,
                willCreateDraft, previewDigest);
        List<CandidateReference> recovered =
                selected.values().stream()
                        .filter(candidate -> recoveredKeys.contains(
                                candidate.identity))
                        .sorted(Comparator.comparing(
                                candidate -> candidate.identity))
                        .collect(Collectors.toList());
        return new RepairPlan(preview, repairedModelJson,
                recovered, draftSnapshot);
    }

    private List<RuleValidationIssue> analysisIssues(
            QLScriptAnalysis analysis) {
        List<RuleValidationIssue> issues =
                new ArrayList<>();
        for (QLScriptAnalysis.Diagnostic diagnostic
                : analysis.getDiagnostics()) {
            issues.add(new RuleValidationIssue(
                    diagnostic.getSeverity(),
                    diagnostic.getCode(),
                    diagnostic.getPath(), null, null,
                    diagnostic.getMessage()));
        }
        return issues;
    }

    private Map<String, CandidateReference> readCandidates(
            JSONArray refs, Long projectId, Long revisionId,
            List<RuleValidationIssue> issues) {
        Map<String, CandidateReference> candidates =
                new LinkedHashMap<>();
        if (refs == null) {
            return candidates;
        }
        for (int index = 0; index < refs.size(); index++) {
            Object raw = refs.get(index);
            if (!(raw instanceof JSONObject)) {
                issues.add(RuleValidationIssue.error(
                                "SCRIPT_REFERENCE_INVALID",
                                "$.scriptVarRefs[" + index + "]",
                                "历史脚本稳定引用必须是对象")
                        .withRevisionId(revisionId));
                continue;
            }
            JSONObject ref = (JSONObject) raw;
            Object rawId = ref.get("varId");
            Long refId = exactPositiveLong(rawId);
            String refType = normalize(
                    ref.getString("refType"));
            String historicalRefCode = trimToNull(
                    ref.getString("refCode"));
            if (refId == null || refType == null
                    || historicalRefCode == null) {
                issues.add(RuleValidationIssue.error(
                                "SCRIPT_REFERENCE_INVALID",
                                "$.scriptVarRefs[" + index + "]",
                                "历史稳定引用缺少 varId、refType 或 refCode")
                        .withRevisionId(revisionId));
                continue;
            }
            String identity = refType + ":" + refId;
            if (candidates.containsKey(identity)) {
                continue;
            }
            QLScriptFieldResolver.ValidatedScriptReference validated =
                    validateReference(projectId, refId, refType);
            if (validated == null) {
                issues.add(invalidReferenceIssue(
                        projectId, refId, refType,
                        historicalRefCode, revisionId));
                continue;
            }
            String scriptRoot = trimToNull(
                    validated.getScriptRoot());
            if (scriptRoot == null) {
                issues.add(RuleValidationIssue.error(
                                "REFERENCE_TYPE_MISMATCH",
                                "$.script." + historicalRefCode,
                                "稳定 ID 对应实体没有可用脚本根")
                        .withReference(refType, refId)
                        .withRevisionId(revisionId));
                continue;
            }
            candidates.put(identity,
                    new CandidateReference(
                            identity, refId, refType,
                            scriptRoot, revisionId));
        }
        return candidates;
    }

    private JSONObject parseHistoricalModel(
            RuleRevision revision,
            List<RuleValidationIssue> issues) {
        try {
            JSONObject model =
                    JSON.parseObject(revision.getModelJson());
            if (model != null) {
                return model;
            }
        } catch (RuntimeException ignored) {
            // 统一转为结构化诊断，不能用损坏快照继续恢复。
        }
        issues.add(RuleValidationIssue.warning(
                        "REVISION_MODEL_INVALID", "$",
                        "历史规则修订模型不是有效 JSON")
                .withRevisionId(revision.getId())
                .withSafeDetail("revisionId",
                        revision.getId()));
        return null;
    }

    private List<CandidateReference> matching(
            Iterable<CandidateReference> candidates,
            String input) {
        Map<String, CandidateReference> matches =
                new TreeMap<>();
        for (CandidateReference candidate : candidates) {
            if (covers(candidate, input)) {
                matches.put(candidate.identity, candidate);
            }
        }
        return new ArrayList<>(matches.values());
    }

    private boolean covers(
            CandidateReference candidate, String input) {
        if (input.equals(candidate.scriptRoot)) {
            return true;
        }
        return "VARIABLE".equals(candidate.refType)
                && input.startsWith(
                candidate.scriptRoot + ".");
    }

    private Long newestRevisionId(
            List<CandidateReference> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.revisionId)
                .filter(Objects::nonNull)
                .max(Long::compareTo).orElse(null);
    }

    private Long sourceRevisionId(
            Iterable<CandidateReference> selected) {
        Long selectedSource = null;
        for (CandidateReference candidate : selected) {
            if (candidate.revisionId != null
                    && (selectedSource == null
                    || candidate.revisionId
                    > selectedSource)) {
                selectedSource = candidate.revisionId;
            }
        }
        if (selectedSource != null) {
            return selectedSource;
        }
        return null;
    }

    private String repairedModelJson(
            JSONObject currentModel,
            Iterable<CandidateReference> selected) {
        JSONObject repaired =
                JSON.parseObject(currentModel.toJSONString());
        JSONArray refs = new JSONArray();
        List<CandidateReference> ordered =
                new ArrayList<>();
        selected.forEach(ordered::add);
        ordered.sort(Comparator.comparing(
                candidate -> candidate.identity));
        for (CandidateReference candidate : ordered) {
            JSONObject ref = new JSONObject(true);
            ref.put("varId", candidate.refId);
            ref.put("refType", candidate.refType);
            ref.put("refCode", candidate.scriptRoot);
            refs.add(ref);
        }
        repaired.put("scriptVarRefs", refs);
        return repaired.toJSONString();
    }

    private boolean projectionDrift(
            String currentModelJson, Long sourceRevisionId,
            List<RuleRevision> revisions,
            List<RuleDefinitionInputField> inputFields,
            List<RuleDefinitionOutputField> outputFields,
            QLScriptAnalysis analysis,
            Map<String, CandidateReference> selected) {
        RuleRevision source = revisions.stream()
                .filter(revision -> revision != null
                        && Objects.equals(sourceRevisionId,
                        revision.getId()))
                .findFirst().orElse(null);
        if (source != null && !Objects.equals(
                modelDigest(currentModelJson),
                modelDigest(source.getModelJson()))) {
            return true;
        }
        List<String> expectedInputs =
                new ArrayList<>();
        for (String input : analysis.getDirectInputs()) {
            List<CandidateReference> matches =
                    matching(selected.values(), input);
            CandidateReference match = matches.size() == 1
                    ? matches.get(0) : null;
            expectedInputs.add(input + "|"
                    + (match == null ? ""
                    : match.identity));
        }
        Collections.sort(expectedInputs);
        List<String> actualInputs =
                safeInputFields(inputFields).stream()
                        .map(field -> trimToEmpty(
                                firstNonBlank(field.getScriptName(),
                                        field.getFieldName()))
                                + "|" + referenceKey(
                                field.getRefType(),
                                field.getVarId()))
                        .sorted()
                        .collect(Collectors.toList());
        if (!expectedInputs.equals(actualInputs)) {
            return true;
        }
        List<String> expectedOutputs =
                analysis.getPublicOutputs().stream()
                        .map(QLScriptAnalysis.OutputField::getName)
                        .sorted()
                        .collect(Collectors.toList());
        List<String> actualOutputs =
                safeOutputFields(outputFields).stream()
                        .map(field -> trimToEmpty(
                                firstNonBlank(field.getScriptName(),
                                        field.getFieldName())))
                        .sorted()
                        .collect(Collectors.toList());
        return !expectedOutputs.equals(actualOutputs);
    }

    private String previewDigest(
            Long definitionId, String currentModelJson,
            List<RuleRevision> revisions,
            List<String> recoverableReferenceKeys,
            List<String> unresolvedInputs,
            boolean willCreateDraft) {
        Map<String, Object> source =
                new LinkedHashMap<>();
        source.put("definitionId", definitionId);
        source.put("currentModelDigest",
                modelDigest(currentModelJson));
        List<Map<String, Object>> revisionDigests =
                revisions.stream()
                        .filter(Objects::nonNull)
                        .filter(revision ->
                                revision.getId() != null)
                        .sorted(Comparator.comparing(
                                RuleRevision::getId))
                        .map(revision -> {
                            Map<String, Object> item =
                                    new LinkedHashMap<>();
                            item.put("revisionId",
                                    revision.getId());
                            item.put("contentDigest",
                                    firstNonBlank(
                                            revision.getContentDigest(),
                                            modelDigest(
                                                    revision.getModelJson())));
                            return item;
                        })
                        .collect(Collectors.toList());
        source.put("revisionDigests", revisionDigests);
        source.put("recoverableReferences",
                recoverableReferenceKeys);
        source.put("unresolvedInputs",
                unresolvedInputs);
        source.put("willCreateDraft",
                willCreateDraft);
        return Sha256Digests.bytes(
                CanonicalJson.writeBytes(source));
    }

    private RuleLifecycleEvent repairEvent(
            RuleRevision repaired, RepairPreview preview,
            List<CandidateReference> recoveredReferences) {
        RuleLifecycleEvent event =
                new RuleLifecycleEvent();
        event.setDefinitionId(repaired.getDefinitionId());
        event.setRevisionId(repaired.getId());
        event.setAction("REPAIR_DRAFT");
        event.setFromState("DRAFT");
        event.setToState("DRAFT");
        event.setActor(actor());
        event.setContentDigest(repaired.getContentDigest());
        event.setValidationReportDigest(
                repaired.getValidationReportDigest());
        event.setRequestSource("CONSOLE");
        event.setCreateTime(LocalDateTime.now());
        Map<String, Object> details =
                new LinkedHashMap<>();
        details.put("sourceRevisionId",
                preview.getSourceRevisionId());
        details.put("targetRevisionId", repaired.getId());
        details.put("previewDigest",
                preview.getPreviewDigest());
        List<Map<String, Object>> references =
                new ArrayList<>();
        for (CandidateReference candidate
                : recoveredReferences) {
            Map<String, Object> reference =
                    new LinkedHashMap<>();
            reference.put("referenceId",
                    candidate.refId);
            reference.put("refType",
                    candidate.refType);
            references.add(reference);
        }
        details.put("referenceSummary", references);
        event.setDetailsJson(JSON.toJSONString(details));
        return event;
    }

    private RuleValidationIssue conflictIssue(
            String input, Long revisionId) {
        return RuleValidationIssue.error(
                        "SCRIPT_REFERENCE_CONFLICT",
                        "$.script." + input,
                        "同一脚本输入在历史中绑定了多个稳定 ID")
                .withRevisionId(revisionId)
                .withSafeDetail("fieldPath", input);
    }

    private RuleValidationIssue missingInputIssue(
            String input, Long revisionId) {
        return RuleValidationIssue.error(
                        "SCRIPT_INPUT_REF_MISSING",
                        "$.script." + input,
                        "脚本外部输入缺少可恢复的稳定 ID")
                .withRevisionId(revisionId)
                .withSafeDetail("fieldPath", input);
    }

    private boolean isRepairBlocking(RuleValidationIssue issue) {
        if (!"ERROR".equals(issue.getSeverity())) {
            return false;
        }
        return "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())
                || "SCRIPT_REFERENCE_CONFLICT".equals(issue.getCode())
                || "QL_PARSE_ERROR".equals(issue.getCode())
                || "SCRIPT_EMPTY".equals(issue.getCode());
    }

    private String referenceKey(
            String refType, Long refId) {
        String type = normalize(refType);
        return type == null || refId == null
                ? "" : type + ":" + refId;
    }

    private Long exactPositiveLong(Object rawId) {
        if (!(rawId instanceof Number)) {
            return null;
        }
        try {
            long value;
            if (rawId instanceof BigInteger) {
                value = ((BigInteger) rawId).longValueExact();
            } else if (rawId instanceof BigDecimal) {
                value = ((BigDecimal) rawId).longValueExact();
            } else if (rawId instanceof Byte
                    || rawId instanceof Short
                    || rawId instanceof Integer
                    || rawId instanceof Long) {
                value = ((Number) rawId).longValue();
            } else {
                value = new BigDecimal(rawId.toString())
                        .longValueExact();
            }
            return value > 0 ? value : null;
        } catch (ArithmeticException
                 | NumberFormatException ignored) {
            return null;
        }
    }

    private String modelDigest(String modelJson) {
        String normalized = trimToEmpty(modelJson);
        try {
            Object parsed = JSON.parse(normalized);
            if (parsed != null) {
                return Sha256Digests.bytes(
                        CanonicalJson.writeBytes(parsed));
            }
        } catch (RuntimeException ignored) {
            // 解析错误由预览诊断负责；此处仍需为损坏模型生成稳定摘要。
        }
        return Sha256Digests.text(normalized);
    }

    private String normalize(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private List<RuleRevision> safeRevisions(
            List<RuleRevision> revisions) {
        return revisions == null
                ? Collections.emptyList()
                : new ArrayList<>(revisions);
    }

    private List<RuleDefinitionInputField> safeInputFields(
            List<RuleDefinitionInputField> fields) {
        return fields == null
                ? Collections.emptyList() : fields;
    }

    private List<RuleDefinitionOutputField> safeOutputFields(
            List<RuleDefinitionOutputField> fields) {
        return fields == null
                ? Collections.emptyList() : fields;
    }

    private RuleGovernanceException governance(
            int status, String code, String message,
            List<RuleValidationIssue> issues) {
        return new RuleGovernanceException(
                status, code, message, issues);
    }

    protected RuleDefinition loadDefinition(
            Long definitionId) {
        return definitionMapper.selectById(definitionId);
    }

    protected RuleDefinition lockDefinition(
            Long definitionId) {
        return definitionMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getId,
                                definitionId)
                        .last("FOR UPDATE"));
    }

    protected RuleDefinitionContent loadContent(
            Long definitionId) {
        return contentMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinitionContent>()
                        .eq(RuleDefinitionContent::getDefinitionId,
                                definitionId));
    }

    protected List<RuleRevision> loadRevisions(
            Long definitionId) {
        return revisionMapper.selectList(
                new LambdaQueryWrapper<RuleRevision>()
                        .eq(RuleRevision::getDefinitionId,
                                definitionId)
                        .orderByDesc(
                                RuleRevision::getRevisionNo)
                        .orderByDesc(RuleRevision::getId));
    }

    protected List<RuleDefinitionInputField> loadInputFields(
            Long definitionId) {
        return inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId,
                                definitionId)
                        .orderByAsc(
                                RuleDefinitionInputField::getSortOrder));
    }

    protected List<RuleDefinitionOutputField> loadOutputFields(
            Long definitionId) {
        return outputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionOutputField>()
                        .eq(RuleDefinitionOutputField::getDefinitionId,
                                definitionId)
                        .orderByAsc(
                                RuleDefinitionOutputField::getSortOrder));
    }

    protected QLScriptFieldResolver.ValidatedScriptReference
    validateReference(Long projectId, Long refId,
                      String refType) {
        return fieldResolver.validateReference(
                projectId, refId, refType);
    }

    protected RuleValidationIssue invalidReferenceIssue(
            Long projectId, Long refId, String refType,
            String historicalRefCode, Long revisionId) {
        JSONObject probe = new JSONObject(true);
        probe.put("script", "_result = "
                + historicalRefCode);
        JSONArray refs = new JSONArray();
        JSONObject ref = new JSONObject(true);
        ref.put("varId", refId);
        ref.put("refType", refType);
        ref.put("refCode", historicalRefCode);
        refs.add(ref);
        probe.put("scriptVarRefs", refs);
        RuleValidationIssue issue = fieldResolver.resolve(
                        probe.toJSONString(), projectId)
                .getDiagnostics().stream()
                .filter(item ->
                        "REFERENCE_NOT_FOUND".equals(
                                item.getCode())
                                || "REFERENCE_TYPE_MISMATCH".equals(
                                item.getCode()))
                .findFirst().orElse(null);
        if (issue == null) {
            issue = RuleValidationIssue.error(
                            "REFERENCE_TYPE_MISMATCH",
                            "$.script." + historicalRefCode,
                            "历史稳定引用不可用")
                    .withReference(refType, refId);
        }
        issue.setRevisionId(revisionId);
        return issue;
    }

    protected RuleRevision currentDraft(
            Long definitionId) {
        return lifecycleService.currentDraft(definitionId);
    }

    protected RuleRevision createDraft(
            Long definitionId, Long baseRevisionId) {
        return lifecycleService.createDraft(
                definitionId, baseRevisionId);
    }

    protected RuleDraftSaveResponse saveDraft(
            RuleDraftSaveRequest request) {
        return draftService.save(request);
    }

    protected void insertRepairEvent(
            RuleLifecycleEvent event) {
        if (eventMapper.insert(event) != 1) {
            throw new IllegalStateException(
                    "规则修复生命周期事件写入失败");
        }
    }

    protected String actor() {
        return operatorResolver.resolve();
    }

    public static final class RepairPreview {
        private final Long sourceRevisionId;
        private final List<String> recoverableReferenceKeys;
        private final List<String> unresolvedInputs;
        private final List<RuleValidationIssue> issues;
        private final boolean projectionDrift;
        private final boolean willCreateDraft;
        private final String previewDigest;

        public RepairPreview(
                Long sourceRevisionId,
                List<String> recoverableReferenceKeys,
                List<String> unresolvedInputs,
                List<RuleValidationIssue> issues,
                boolean projectionDrift,
                boolean willCreateDraft,
                String previewDigest) {
            this.sourceRevisionId = sourceRevisionId;
            this.recoverableReferenceKeys =
                    List.copyOf(recoverableReferenceKeys);
            this.unresolvedInputs =
                    List.copyOf(unresolvedInputs);
            this.issues = List.copyOf(issues);
            this.projectionDrift = projectionDrift;
            this.willCreateDraft = willCreateDraft;
            this.previewDigest = previewDigest;
        }

        public Long getSourceRevisionId() {
            return sourceRevisionId;
        }

        public List<String> getRecoverableReferenceKeys() {
            return recoverableReferenceKeys;
        }

        public List<String> getUnresolvedInputs() {
            return unresolvedInputs;
        }

        public List<RuleValidationIssue> getIssues() {
            return issues;
        }

        public boolean isProjectionDrift() {
            return projectionDrift;
        }

        public boolean isWillCreateDraft() {
            return willCreateDraft;
        }

        public String getPreviewDigest() {
            return previewDigest;
        }
    }

    public static final class RepairPreviewChangedException
            extends IllegalStateException {
        public RepairPreviewChangedException(String message) {
            super(message);
        }
    }

    private static final class RepairPlan {
        private final RepairPreview preview;
        private final String repairedModelJson;
        private final List<CandidateReference>
                recoveredReferences;
        private final RuleRevision draftSnapshot;

        private RepairPlan(
                RepairPreview preview,
                String repairedModelJson,
                List<CandidateReference> recoveredReferences,
                RuleRevision draftSnapshot) {
            this.preview = preview;
            this.repairedModelJson = repairedModelJson;
            this.recoveredReferences =
                    recoveredReferences;
            this.draftSnapshot = draftSnapshot;
        }
    }

    private static final class CandidateReference {
        private final String identity;
        private final Long refId;
        private final String refType;
        private final String scriptRoot;
        private final Long revisionId;

        private CandidateReference(
                String identity, Long refId,
                String refType, String scriptRoot,
                Long revisionId) {
            this.identity = identity;
            this.refId = refId;
            this.refType = refType;
            this.scriptRoot = scriptRoot;
            this.revisionId = revisionId;
        }
    }
}
