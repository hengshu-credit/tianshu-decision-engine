package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentGroup;
import com.hengshucredit.rule.model.entity.RuleExperimentVersion;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleFunctionVersion;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.service.RuleExperimentService;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleModelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将旧模块版本表中的历史快照转换为统一生命周期恢复审批。
 */
@Service
public class LegacyGovernanceRestoreService {

    @Resource
    private GovernanceApprovalService approvalService;
    @Resource
    private RuleModelService modelService;
    @Resource
    private RuleFunctionService functionService;
    @Resource
    private RuleExperimentService experimentService;

    public GovernanceApprovalRequest restoreModel(Long modelId,
                                                  Integer version,
                                                  String actor) {
        return restoreModel(modelId, version, "NEW", actor);
    }

    public GovernanceApprovalRequest restoreModel(Long modelId, Integer version, String mode, String actor) {
        RuleModel snapshot = modelService.buildRestoreSnapshot(
                modelId, version, true);
        var source = modelService.getVersion(modelId, version);
        return approvalService.createLegacyRestoreDraft(GovernanceResourceTypes.MODEL, modelId, "MODEL_VERSION", source.getId(),
                source.getDisplayVersion(), ResourceSnapshot.ofJson(JSON.toJSONString(snapshot)), mode, actor);
    }

    public GovernanceApprovalRequest restoreFunction(Long functionId,
                                                     Integer version,
                                                     String actor) {
        return restoreFunction(functionId, version, "NEW", actor);
    }

    public GovernanceApprovalRequest restoreFunction(Long functionId, Integer version, String mode, String actor) {
        RuleFunctionVersion source = functionService.getVersion(
                functionId, version);
        if (source == null) {
            throw new IllegalArgumentException("Version not found");
        }
        RuleFunction snapshot = JSON.parseObject(
                source.getFunctionJson(), RuleFunction.class);
        if (snapshot == null) {
            throw new IllegalArgumentException("历史函数快照无效");
        }
        snapshot.setId(functionId);
        return approvalService.createLegacyRestoreDraft(GovernanceResourceTypes.FUNCTION, functionId, "FUNCTION_VERSION", source.getId(),
                source.getDisplayVersion(), ResourceSnapshot.ofJson(JSON.toJSONString(snapshot)), mode, actor);
    }

    public GovernanceApprovalRequest restoreExperiment(
            Long experimentId, Integer version, String actor) {
        return restoreExperiment(experimentId, version, "NEW", actor);
    }

    public GovernanceApprovalRequest restoreExperiment(Long experimentId, Integer version, String mode, String actor) {
        RuleExperimentVersion source = experimentService.getVersion(
                experimentId, version);
        if (source == null) {
            throw new IllegalArgumentException("Version not found");
        }
        RuleExperiment snapshot = JSON.parseObject(
                source.getExperimentJson(), RuleExperiment.class);
        if (snapshot == null) {
            throw new IllegalArgumentException("历史分流实验快照无效");
        }
        List<RuleExperimentGroup> groups = JSON.parseArray(
                source.getGroupsJson(), RuleExperimentGroup.class);
        snapshot.setId(experimentId);
        snapshot.setGroups(groups == null ? List.of() : groups);
        return approvalService.createLegacyRestoreDraft(GovernanceResourceTypes.EXPERIMENT, experimentId, "EXPERIMENT_VERSION", source.getId(),
                source.getDisplayVersion(), ResourceSnapshot.ofJson(JSON.toJSONString(snapshot)), mode, actor);
    }
}
