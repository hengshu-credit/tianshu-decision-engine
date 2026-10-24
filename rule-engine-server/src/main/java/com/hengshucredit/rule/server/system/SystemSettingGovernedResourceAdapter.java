package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SystemSettingGovernedResourceAdapter implements GovernedResourceAdapter {
    private final SystemSettingsService settings;
    private final SystemSettingsAccess access;
    public SystemSettingGovernedResourceAdapter(SystemSettingsService settings, SystemSettingsAccess access) { this.settings = settings; this.access = access; }
    @Override public String resourceType() { return SystemSettingsService.RESOURCE_TYPE; }
    @Override public ResourceSnapshot loadEffective(Long id) { return settings.snapshot(id); }
    @Override public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) { access.requireWrite(); return settings.normalize(draft); }
    @Override public List<GovernanceIssue> validate(ResourceSnapshot draft) { return validate(draft, "UPDATE"); }
    @Override public List<GovernanceIssue> validate(ResourceSnapshot draft, String action) {
        Long id = ((Number) CanonicalJson.readMap(draft.snapshotJson()).get("id")).longValue();
        try {
            if (!"UPDATE".equals(action) || id == 1) throw new IllegalArgumentException("此系统配置仅支持修改审批");
            settings.validate(draft); return List.of();
        } catch (IllegalArgumentException error) { return List.of(GovernanceIssue.error("SYSTEM_SETTING_INVALID", error.getMessage(), resourceType(), id, "$")); }
    }
    @Override public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) { return List.of(); }
    @Override public ResourceDiff diff(ResourceSnapshot left, ResourceSnapshot right) { return JsonResourceDiff.compare(left, right); }
    @Override public AppliedResource apply(ApprovalApplyContext context) {
        access.requireWrite();
        if (!"UPDATE".equals(context.action()) || context.draftWrite() || context.insertRoot()) throw new IllegalArgumentException("系统配置仅支持修改审批");
        var value = CanonicalJson.readMap(context.snapshot().snapshotJson());
        if (!java.util.Objects.equals(context.resourceId(), ((Number) value.get("id")).longValue())) throw new IllegalArgumentException("系统配置身份不一致");
        settings.validate(context.snapshot()); settings.persist(context.snapshot(), context.actor());
        return new AppliedResource(context.resourceId(), context.nextVersionNo(), "ACTIVE", null);
    }
}
