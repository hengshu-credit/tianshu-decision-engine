package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 规则模型稳定引用审计与人工迁移服务。code/label 仅用于定位，绝不用于资源关联。 */
@Service
public class RuleReferenceIntegrityService {

    @Resource
    private RuleDefinitionMapper definitionMapper;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleFieldAnalyzer fieldAnalyzer;

    public AuditReport scan(Long definitionId) {
        RuleDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不存在，definitionId=" + definitionId);
        }
        RuleDefinitionContent content = contentMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinitionContent>()
                        .eq(RuleDefinitionContent::getDefinitionId, definitionId));
        return audit(definitionId, definition.getProjectId(),
                content == null ? null : content.getModelJson());
    }

    public List<AuditReport> scanAll() {
        List<RuleDefinition> definitions = definitionMapper.selectList(null);
        if (definitions == null || definitions.isEmpty()) return Collections.emptyList();
        List<AuditReport> reports = new ArrayList<>();
        for (RuleDefinition definition : definitions) {
            AuditReport report = scan(definition.getId());
            if (!report.isValid()) reports.add(report);
        }
        return reports;
    }

    public AuditReport audit(Long definitionId, Long projectId, String modelJson) {
        Map<String, String> validRefs = variableService.buildRefScriptNameMap(projectId);
        List<ReferenceIssue> issues = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (modelJson == null || modelJson.trim().isEmpty()) {
            addIssue(issues, seen, definitionId, "$", null, null, null,
                    null, null, "EMPTY_MODEL", "规则模型内容为空");
            return new AuditReport(definitionId, issues);
        }
        Object root;
        try {
            root = JSON.parse(modelJson);
        } catch (RuntimeException e) {
            addIssue(issues, seen, definitionId, "$", null, null, null,
                    null, null, "INVALID_JSON", "规则模型 JSON 无法解析");
            return new AuditReport(definitionId, issues);
        }
        inspect(root, "$", definitionId, validRefs, issues, seen);
        return new AuditReport(definitionId, issues);
    }

    public void assertValid(Long definitionId, Long projectId, String modelJson) {
        AuditReport report = audit(definitionId, projectId, modelJson);
        if (!report.isValid()) {
            ReferenceIssue first = report.getIssues().get(0);
            throw new IllegalArgumentException("规则引用完整性校验失败，共 " + report.getIssueCount()
                    + " 项；首项 " + first.getPath() + "：" + first.getMessage());
        }
    }

    @Transactional
    public MigrationResult migrate(MigrationRequest request) {
        if (request == null || request.getDefinitionId() == null) {
            throw new IllegalArgumentException("definitionId 不能为空");
        }
        RuleDefinition definition = definitionMapper.selectById(request.getDefinitionId());
        if (definition == null) throw new IllegalArgumentException("规则定义不存在");
        RuleDefinitionContent content = contentMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinitionContent>()
                        .eq(RuleDefinitionContent::getDefinitionId, request.getDefinitionId()));
        if (content == null || content.getModelJson() == null) {
            throw new IllegalArgumentException("规则内容不存在");
        }
        Object root = JSON.parse(content.getModelJson());
        int applied = 0;
        for (MigrationPatch patch : request.getPatches() == null
                ? Collections.<MigrationPatch>emptyList() : request.getPatches()) {
            if (applyPatch(root, patch)) applied++;
        }
        String migratedJson = JSON.toJSONString(root);
        AuditReport report = audit(definition.getId(), definition.getProjectId(), migratedJson);
        content.setModelJson(migratedJson);
        content.setCompileStatus(0);
        contentMapper.updateById(content);
        definition.setCurrentVersion((definition.getCurrentVersion() == null ? 0 : definition.getCurrentVersion()) + 1);
        definitionMapper.updateById(definition);
        fieldAnalyzer.analyzeAndPersist(definition.getId(), migratedJson,
                definition.getModelType(), definition.getProjectId());
        return new MigrationResult(applied, report);
    }

    private boolean applyPatch(Object root, MigrationPatch patch) {
        if (patch == null || patch.getRefId() == null || blank(patch.getRefType())
                || blank(patch.getPath()) || blank(patch.getIdField()) || blank(patch.getRefTypeField())) {
            return false;
        }
        Object node = resolvePath(root, patch.getPath());
        if (!(node instanceof JSONObject)) return false;
        JSONObject object = (JSONObject) node;
        object.put(patch.getIdField(), patch.getRefId());
        object.put(patch.getRefTypeField(), patch.getRefType().trim().toUpperCase());
        if ("refId".equals(patch.getIdField())) object.put("resolved", true);
        return true;
    }

    private Object resolvePath(Object root, String path) {
        if ("$".equals(path)) return root;
        if (path == null || !path.startsWith("$")) return null;
        Object current = root;
        int index = 1;
        while (index < path.length()) {
            if (path.charAt(index) == '.') {
                int nextDot = path.indexOf('.', index + 1);
                int nextArray = path.indexOf('[', index + 1);
                int end = minPositive(nextDot, nextArray, path.length());
                if (!(current instanceof JSONObject)) return null;
                current = ((JSONObject) current).get(path.substring(index + 1, end));
                index = end;
            } else if (path.charAt(index) == '[') {
                int end = path.indexOf(']', index + 1);
                if (end < 0 || !(current instanceof JSONArray)) return null;
                int arrayIndex;
                try {
                    arrayIndex = Integer.parseInt(path.substring(index + 1, end));
                } catch (NumberFormatException e) {
                    return null;
                }
                JSONArray array = (JSONArray) current;
                if (arrayIndex < 0 || arrayIndex >= array.size()) return null;
                current = array.get(arrayIndex);
                index = end + 1;
            } else {
                return null;
            }
        }
        return current;
    }

    private int minPositive(int first, int second, int fallback) {
        int result = fallback;
        if (first >= 0) result = Math.min(result, first);
        if (second >= 0) result = Math.min(result, second);
        return result;
    }

    private void inspect(Object node, String path, Long definitionId, Map<String, String> validRefs,
                         List<ReferenceIssue> issues, Set<String> seen) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            String kind = object.getString("kind");
            if (("PATH".equals(kind) || "REFERENCE".equals(kind))
                    && !blank(firstText(object.getString("code"), object.getString("value")))) {
                checkPair(object, path, definitionId, "refId", "refType",
                        firstText(object.getString("code"), object.getString("value")),
                        validRefs, issues, seen);
            }
            if (!blank(object.getString("varCode"))) {
                checkPair(object, path, definitionId, "_varId", "_refType", object.getString("varCode"),
                        validRefs, issues, seen);
            }
            if (!blank(object.getString("refCode"))) {
                checkPair(object, path, definitionId, "varId", "refType", object.getString("refCode"),
                        validRefs, issues, seen);
            }
            checkNamedField(object, path, definitionId, "leftVar", "leftVarId", "leftRefType",
                    validRefs, issues, seen);
            checkNamedField(object, path, definitionId, "rightVar", "rightVarId", "rightRefType",
                    validRefs, issues, seen);
            if (!isGraphEdge(object)) {
                checkNamedField(object, path, definitionId, "target", "_targetVarId", "_targetRefType",
                        validRefs, issues, seen);
            }
            checkNamedField(object, path, definitionId, "condVar", "_condVarId", "_condVarRefType",
                    validRefs, issues, seen);
            checkNamedField(object, path, definitionId, "matchVar", "_matchVarId", "_matchVarRefType",
                    validRefs, issues, seen);
            checkNamedField(object, path, definitionId, "checkVar", "_checkVarId", "_checkVarRefType",
                    validRefs, issues, seen);
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                inspect(entry.getValue(), path + "." + entry.getKey(), definitionId,
                        validRefs, issues, seen);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.size(); i++) {
                inspect(array.get(i), path + "[" + i + "]", definitionId, validRefs, issues, seen);
            }
        }
    }

    private void checkNamedField(JSONObject object, String path, Long definitionId,
                                 String codeField, String idField, String typeField,
                                 Map<String, String> validRefs, List<ReferenceIssue> issues, Set<String> seen) {
        String code = object.getString(codeField);
        if (blank(code)) return;
        checkPair(object, path, definitionId, idField, typeField, code, validRefs, issues, seen);
    }

    private boolean isGraphEdge(JSONObject object) {
        return object.containsKey("source") && object.containsKey("target");
    }

    private void checkPair(JSONObject object, String path, Long definitionId,
                           String idField, String typeField, String code,
                           Map<String, String> validRefs, List<ReferenceIssue> issues, Set<String> seen) {
        Long refId = object.getLong(idField);
        String refType = object.getString(typeField);
        if (refId == null || blank(refType)) {
            addIssue(issues, seen, definitionId, path, code, refId, refType,
                    idField, typeField, "MISSING_CONTRACT", "引用缺少 ID 或 ref_type，禁止通过 code/label 关联");
            return;
        }
        String key = refType.trim().toUpperCase() + ":" + refId;
        if (!validRefs.containsKey(key)) {
            addIssue(issues, seen, definitionId, path, code, refId, refType,
                    idField, typeField, "DANGLING_REFERENCE", "引用不存在或已停用：" + key);
        }
    }

    private void addIssue(List<ReferenceIssue> issues, Set<String> seen, Long definitionId,
                          String path, String code, Long refId, String refType,
                          String idField, String refTypeField, String reason, String message) {
        String signature = path + "|" + idField + "|" + refTypeField + "|" + reason;
        if (!seen.add(signature)) return;
        issues.add(new ReferenceIssue(definitionId, path, code, refId, refType,
                idField, refTypeField, reason, message));
    }

    private String firstText(String first, String second) {
        return blank(first) ? second : first;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class AuditReport {
        private final Long definitionId;
        private final List<ReferenceIssue> issues;

        public AuditReport(Long definitionId, List<ReferenceIssue> issues) {
            this.definitionId = definitionId;
            this.issues = issues == null ? Collections.<ReferenceIssue>emptyList() : issues;
        }

        public Long getDefinitionId() { return definitionId; }
        public boolean isValid() { return issues.isEmpty(); }
        public int getIssueCount() { return issues.size(); }
        public List<ReferenceIssue> getIssues() { return issues; }
    }

    public static class ReferenceIssue {
        private final Long definitionId;
        private final String path;
        private final String displayCode;
        private final Long refId;
        private final String refType;
        private final String idField;
        private final String refTypeField;
        private final String reason;
        private final String message;

        public ReferenceIssue(Long definitionId, String path, String displayCode, Long refId,
                              String refType, String idField, String refTypeField,
                              String reason, String message) {
            this.definitionId = definitionId;
            this.path = path;
            this.displayCode = displayCode;
            this.refId = refId;
            this.refType = refType;
            this.idField = idField;
            this.refTypeField = refTypeField;
            this.reason = reason;
            this.message = message;
        }

        public Long getDefinitionId() { return definitionId; }
        public String getPath() { return path; }
        public String getDisplayCode() { return displayCode; }
        public Long getRefId() { return refId; }
        public String getRefType() { return refType; }
        public String getIdField() { return idField; }
        public String getRefTypeField() { return refTypeField; }
        public String getReason() { return reason; }
        public String getMessage() { return message; }
    }

    public static class MigrationRequest {
        private Long definitionId;
        private List<MigrationPatch> patches;
        public Long getDefinitionId() { return definitionId; }
        public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
        public List<MigrationPatch> getPatches() { return patches; }
        public void setPatches(List<MigrationPatch> patches) { this.patches = patches; }
    }

    public static class MigrationPatch {
        private String path;
        private String idField;
        private String refTypeField;
        private Long refId;
        private String refType;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getIdField() { return idField; }
        public void setIdField(String idField) { this.idField = idField; }
        public String getRefTypeField() { return refTypeField; }
        public void setRefTypeField(String refTypeField) { this.refTypeField = refTypeField; }
        public Long getRefId() { return refId; }
        public void setRefId(Long refId) { this.refId = refId; }
        public String getRefType() { return refType; }
        public void setRefType(String refType) { this.refType = refType; }
    }

    public static class MigrationResult {
        private final int appliedCount;
        private final AuditReport audit;
        public MigrationResult(int appliedCount, AuditReport audit) {
            this.appliedCount = appliedCount;
            this.audit = audit;
        }
        public int getAppliedCount() { return appliedCount; }
        public AuditReport getAudit() { return audit; }
    }
}
