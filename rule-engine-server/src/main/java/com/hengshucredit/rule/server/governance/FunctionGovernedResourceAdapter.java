package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.function.FunctionParameterContract;
import com.hengshucredit.rule.core.function.FunctionScriptContract;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;
import com.hengshucredit.rule.server.service.FunctionMethodCatalogService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FunctionGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private final SimpleEntityGovernedResourceAdapter<RuleFunction> delegate;
    private final RuleFunctionPushService functionPushService;
    private final FunctionMethodCatalogService methodCatalogService;
    @jakarta.annotation.Resource @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.service.RuleFunctionService functionService;

    public FunctionGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleFunction>
                    store,
            GovernanceSecretCodec secretCodec,
            RuleFunctionPushService functionPushService,
            FunctionMethodCatalogService methodCatalogService) {
        this.delegate = new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.FUNCTION,
                RuleFunction.class,
                store,
                RuleFunction::getId,
                RuleFunction::setId,
                RuleFunction::getStatus,
                RuleFunction::setStatus,
                Set.of("funcCode", "funcName", "implType"),
                Set.of(), secretCodec);
        this.functionPushService = functionPushService;
        this.methodCatalogService = methodCatalogService;
    }

    @Override
    public String resourceType() {
        return delegate.resourceType();
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        return delegate.loadEffective(resourceId);
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        RuleFunction function = function(draft);
        if (methodCatalogService != null) methodCatalogService.requireNativePermission(function);
        functionPushService.prepare(function, "FUNC_UPDATE");
        return delegate.normalizeDraft(snapshot(draft, function));
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        List<GovernanceIssue> issues = new ArrayList<>(delegate.validate(draft));
        issues.addAll(bindingIssues(function(draft)));
        return issues;
    }

    private List<GovernanceIssue> bindingIssues(RuleFunction function) {
        try {
            FunctionParameterContract.parse(function.getParamsJson());
            if ("SCRIPT".equals(function.getImplType())) {
                FunctionScriptContract.validate(function.getFuncCode(), function.getImplScript(), function.getParamsJson());
                return List.of();
            }
            if (!"JAVA".equals(function.getImplType()) && !"BEAN".equals(function.getImplType())) {
                throw new IllegalArgumentException("函数实现方式必须是 SCRIPT、JAVA 或 BEAN");
            }
            var catalog = methodCatalogService.inspect(function);
            if ("UNAVAILABLE".equals(catalog.status())) {
                return List.of(new GovernanceIssue("WARNING", "FUNCTION_TARGET_UNVERIFIED", catalog.message(),
                        resourceType(), function.getId(), "$.implMethod", null,
                        "目标运行端方法尚未验证", "在目标运行端部署相同实现并验证精确签名后再使用"));
            }
            if (!catalog.valid()) throw new IllegalArgumentException(catalog.message());
            var selected = catalog.methods().stream()
                    .filter(method -> java.util.Objects.equals(method.signature(), catalog.signature()))
                    .findFirst().orElse(null);
            if (selected != null && function.getReturnType() != null && !function.getReturnType().isBlank()) {
                String declared = function.getReturnType().trim().toUpperCase(java.util.Locale.ROOT);
                String actual = methodCatalogService.suggestedBusinessType(selected.returnJavaType());
                boolean numeric = ("NUMBER".equals(declared) || "DOUBLE".equals(declared)) && "NUMBER".equals(actual);
                if (!"OBJECT".equals(declared) && !numeric && !declared.equals(actual)) {
                    throw new IllegalArgumentException("函数返回类型 " + declared + " 与实际方法返回类型 " + selected.returnJavaType() + " 不匹配");
                }
            }
            if (catalog.legacySignature()) {
                return List.of(new GovernanceIssue("WARNING", "FUNCTION_LEGACY_SIGNATURE",
                        "当前按旧类型映射绑定 " + catalog.signature() + "；选择具体方法后可保存精确签名",
                        resourceType(), function.getId(), "$.paramsJson", null,
                        "沿用旧方法参数映射", "核对实际签名，不会自动变更历史绑定"));
            }
            return List.of();
        } catch (IllegalArgumentException error) {
            String code = "SCRIPT".equals(function.getImplType())
                    ? "FUNCTION_DEFINITION_INVALID" : "FUNCTION_METHOD_SIGNATURE_INVALID";
            return List.of(GovernanceIssue.error(code, error.getMessage(),
                    resourceType(), function.getId(), "SCRIPT".equals(function.getImplType()) ? "$.implScript" : "$.paramsJson"));
        }
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        return delegate.collectDependencies(draft);
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return delegate.diff(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        if (context.draftWrite() || context.projectionOnly()) return delegate.apply(context);
        RuleFunction function = function(context.snapshot());
        if (java.util.Set.of("CREATE", "ENABLE", "RESTORE").contains(context.action())) function.setStatus(1);
        else if ("DISABLE".equals(context.action())) function.setStatus(0);
        else if ("DELETE".equals(context.action())) function.setStatus(-1);
        if (!"DELETE".equalsIgnoreCase(context.action()) && !"DISABLE".equalsIgnoreCase(context.action())) {
            var error = bindingIssues(function).stream().filter(GovernanceIssue::isError).findFirst();
            if (error.isPresent()) throw new IllegalArgumentException(error.get().message());
        }
        String action = Set.of("DELETE", "DISABLE").contains(context.action())
                ? "FUNC_DELETE" : "FUNC_UPDATE";
        RulePushMessage message = functionPushService.prepare(function,
                action);
        ResourceSnapshot normalized = delegate.normalizeDraft(
                snapshot(context.snapshot(), function));
        AppliedResource applied = delegate.apply(new ApprovalApplyContext(
                context.requestId(), context.resourceId(),
                context.nextVersionNo(), context.action(), normalized,
                context.actor(), context.sourceVersionId()));
        if (!context.isolatedPublication()) functionPushService.push(message);
        if (functionService != null) {
            int number = functionService.recordAppliedVersion(applied.resourceId(), context.reuseVersion() ? context.nextVersionNo() : null,
                    "审批 " + context.requestId(), context.actor());
            return new AppliedResource(applied.resourceId(), number, applied.effectiveStatus(), applied.artifactId());
        }
        return applied;
    }

    @Override
    public boolean supportsDraftWorkspace() { return true; }

    private RuleFunction function(ResourceSnapshot snapshot) {
        RuleFunction function = JSON.parseObject(snapshot.snapshotJson(),
                RuleFunction.class);
        if (function == null) {
            throw new IllegalArgumentException("函数审批快照无效");
        }
        return function;
    }

    private ResourceSnapshot snapshot(ResourceSnapshot original,
                                      RuleFunction function) {
        return new ResourceSnapshot(JSON.toJSONString(function),
                original.effectiveStatus(),
                original.secretPayloadCiphertext(),
                original.secretDigest());
    }
}
