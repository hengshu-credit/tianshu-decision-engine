package com.hengshucredit.rule.server.governance;

import static com.hengshucredit.rule.server.governance.SnapshotValues.longValue;
import static com.hengshucredit.rule.server.governance.SnapshotValues.integerValue;
import static com.hengshucredit.rule.server.governance.SnapshotValues.stringValue;
import static com.hengshucredit.rule.server.governance.SnapshotValues.hasText;
import static com.hengshucredit.rule.server.governance.SnapshotValues.upper;
import static com.hengshucredit.rule.server.governance.SnapshotValues.defaultLong;
import static com.hengshucredit.rule.server.governance.SnapshotValues.defaultInteger;
import static com.hengshucredit.rule.server.governance.SnapshotValues.putLong;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RuleListLibraryGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private static final Set<String> LIST_TYPES = Set.of(
            "BLACK", "GREY", "WHITE", "OTHER");

    private final RuleListLibraryMapper listMapper;
    private final RuleProjectMapper projectMapper;

    public RuleListLibraryGovernedResourceAdapter(
            RuleListLibraryMapper listMapper,
            RuleProjectMapper projectMapper) {
        this.listMapper = listMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public String resourceType() {
        return GovernanceResourceTypes.LIST_LIBRARY;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        RuleListLibrary library = listMapper.selectById(resourceId);
        if (library == null) {
            throw new IllegalArgumentException(
                    "名单库不存在: " + resourceId);
        }
        return normalizeDraft(new ResourceSnapshot(
                CanonicalJson.write(library),
                effectiveStatus(library.getStatus()), null, null));
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        Map<String, Object> source = CanonicalJson.readMap(
                draft == null ? null : draft.snapshotJson());
        Map<String, Object> value = new LinkedHashMap<>();
        putLong(value, "id", source.get("id"));
        String scope = upper(source.get("scope"), "PROJECT");
        value.put("projectId", "GLOBAL".equals(scope)
                ? 0L : defaultLong(source.get("projectId"), 0L));
        value.put("scope", scope);
        put(value, "listCode", source.get("listCode"));
        put(value, "listName", source.get("listName"));
        value.put("listType", upper(source.get("listType"), "BLACK"));
        put(value, "description", source.get("description"));
        value.put("status", defaultInteger(source.get("status"), 1));
        String status = draft == null || draft.effectiveStatus() == null
                ? effectiveStatus(defaultInteger(source.get("status"), 1))
                : draft.effectiveStatus();
        return new ResourceSnapshot(CanonicalJson.write(value), status,
                null, null);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        Map<String, Object> value = CanonicalJson.readMap(
                draft.snapshotJson());
        Long id = longValue(value.get("id"));
        Long projectId = longValue(value.get("projectId"));
        String scope = stringValue(value.get("scope"));
        String code = stringValue(value.get("listCode"));
        String name = stringValue(value.get("listName"));
        String type = stringValue(value.get("listType"));
        Integer status = integerValue(value.get("status"));
        List<GovernanceIssue> issues = new ArrayList<>();
        if (!hasText(code)) {
            issues.add(error("LIST_CODE_REQUIRED", "名单编码不能为空",
                    id, "$.listCode"));
        }
        if (!hasText(name)) {
            issues.add(error("LIST_NAME_REQUIRED", "名单名称不能为空",
                    id, "$.listName"));
        }
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) {
            issues.add(error("LIST_SCOPE_INVALID",
                    "作用范围只能是全局或项目", id, "$.scope"));
        }
        if (!LIST_TYPES.contains(type)) {
            issues.add(error("LIST_TYPE_INVALID", "名单类型不受支持",
                    id, "$.listType"));
        }
        if (status == null || (status != 0 && status != 1)) {
            issues.add(error("LIST_STATUS_INVALID",
                    "名单状态只能是启用或停用", id, "$.status"));
        }
        if ("PROJECT".equals(scope)) {
            if (projectId == null || projectId <= 0) {
                issues.add(error("LIST_PROJECT_REQUIRED",
                        "项目级名单必须选择所属项目", id,
                        "$.projectId"));
            } else if (projectMapper.selectById(projectId) == null) {
                issues.add(error("LIST_PROJECT_NOT_FOUND",
                        "所属项目不存在", id, "$.projectId"));
            }
        }
        RuleListLibrary current = id == null
                ? null : listMapper.selectById(id);
        if (id != null && current == null) {
            issues.add(error("LIST_LIBRARY_NOT_FOUND",
                    "名单库已不存在，请刷新后重试", id, "$"));
        } else if (current != null && (!Objects.equals(
                current.getProjectId(), projectId)
                || !Objects.equals(current.getScope(), scope)
                || !Objects.equals(current.getListCode(), code))) {
            issues.add(error("LIST_IDENTITY_CHANGED",
                    "名单库编码和作用范围创建后不可修改", id, "$"));
        }
        if (hasText(code) && ("GLOBAL".equals(scope)
                || "PROJECT".equals(scope))) {
            LambdaQueryWrapper<RuleListLibrary> query =
                    new LambdaQueryWrapper<RuleListLibrary>()
                            .eq(RuleListLibrary::getScope, scope)
                            .eq(RuleListLibrary::getProjectId,
                                    "GLOBAL".equals(scope) ? 0L : projectId)
                            .eq(RuleListLibrary::getListCode, code);
            if (id != null) {
                query.ne(RuleListLibrary::getId, id);
            }
            Long count = listMapper.selectCount(query);
            if (count != null && count > 0) {
                issues.add(error("LIST_CODE_DUPLICATE",
                        "同一作用范围内名单编码已存在", id,
                        "$.listCode"));
            }
        }
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        Map<String, Object> value = CanonicalJson.readMap(
                draft.snapshotJson());
        Long projectId = longValue(value.get("projectId"));
        if (!"PROJECT".equals(value.get("scope"))
                || projectId == null || projectId <= 0) {
            return List.of();
        }
        return List.of(new ResourceDependencyRef(
                GovernanceResourceTypes.PROJECT, projectId,
                GovernanceResourceTypes.PROJECT, "$.projectId",
                "BELONGS_TO", true));
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public boolean supportsDraftWorkspace() { return true; }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        Map<String, Object> value = CanonicalJson.readMap(
                context.snapshot().snapshotJson());
        RuleListLibrary library = new RuleListLibrary();
        library.setProjectId(longValue(value.get("projectId")));
        library.setScope(stringValue(value.get("scope")));
        library.setListCode(stringValue(value.get("listCode")));
        library.setListName(stringValue(value.get("listName")));
        library.setListType(stringValue(value.get("listType")));
        library.setDescription(stringValue(value.get("description")));
        String action = upper(context.action(), "UPDATE");
        String status = switch (action) {
            case "DRAFT_CREATE", "DRAFT_UPDATE" -> "DRAFT";
            case "CREATE", "ENABLE", "RESTORE" -> "ACTIVE";
            case "DISABLE" -> "DISABLED";
            case "DELETE", "DRAFT_DELETE" -> "DELETED";
            default -> effectiveStatus(integerValue(value.get("status")));
        };
        library.setStatus("ACTIVE".equals(status) ? 1
                : "DELETED".equals(status) ? -1 : 0);
        if (context.insertRoot()) {
            if (listMapper.insert(library) != 1
                    || library.getId() == null) {
                throw new IllegalStateException("名单库创建失败");
            }
        } else {
            if (context.resourceId() == null) {
                throw new IllegalArgumentException(
                        "非创建操作必须指定名单库 ID");
            }
            library.setId(context.resourceId());
            if (listMapper.updateById(library) != 1) {
                throw new IllegalStateException(
                        "名单库不存在或已被并发修改");
            }
        }
        return new AppliedResource(library.getId(),
                context.nextVersionNo(), status, null);
    }

    private GovernanceIssue error(String code, String message,
                                  Long resourceId, String path) {
        return GovernanceIssue.error(code, message, resourceType(),
                resourceId, path);
    }

    private String effectiveStatus(Integer status) {
        if (Integer.valueOf(-1).equals(status)) return "DELETED";
        return Integer.valueOf(0).equals(status)
                ? "DISABLED" : "ACTIVE";
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }








}
