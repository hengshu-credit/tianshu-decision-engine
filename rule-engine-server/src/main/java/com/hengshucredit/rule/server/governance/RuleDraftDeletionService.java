package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** Only a rule that has never been approved or published may be discarded directly. */
@Service
public class RuleDraftDeletionService {
    @Resource private RuleDefinitionMapper definitionMapper;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private RulePublishedMapper publishedMapper;
    @Resource private RuleDefinitionVersionMapper versionMapper;
    @Resource private GovernanceApprovalRequestMapper requestMapper;
    @Resource private GovernedResourceVersionMapper governedVersionMapper;
    @Resource private RuleLifecycleEventMapper eventMapper;

    // Caller holds a transaction. Approval, submission and deletion use the same root lock.
    public RuleDefinition lock(Long id) {
        RuleDefinition definition = definitionMapper.selectOne(new LambdaQueryWrapper<RuleDefinition>()
                .eq(RuleDefinition::getId, id).last("FOR UPDATE"));
        if (definition == null || Integer.valueOf(-1).equals(definition.getStatus())) {
            throw new IllegalArgumentException("规则不存在或已删除，请刷新列表");
        }
        return definition;
    }

    public boolean isUnapproved(RuleDefinition definition) {
        Long id = definition.getId();
        if (!Integer.valueOf(0).equals(definition.getStatus())
                || definition.getPublishedVersion() != null && definition.getPublishedVersion() > 0) return false;
        if (requestMapper.selectCount(new LambdaQueryWrapper<GovernanceApprovalRequest>()
                .eq(GovernanceApprovalRequest::getResourceType, "RULE")
                .eq(GovernanceApprovalRequest::getResourceId, id)
                .eq(GovernanceApprovalRequest::getStatus, "APPROVED")) > 0) return false;
        if (governedVersionMapper.selectCount(new LambdaQueryWrapper<GovernedResourceVersion>()
                .eq(GovernedResourceVersion::getResourceType, "RULE")
                .eq(GovernedResourceVersion::getResourceId, id)
                .isNotNull(GovernedResourceVersion::getApprovalRequestId)) > 0) return false;
        if (revisionMapper.selectCount(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, id)
                .and(q -> q.in(RuleRevision::getState, "APPROVED", "PUBLISHED", "OFFLINE")
                        .or().isNotNull(RuleRevision::getArtifactId)
                        .or().isNotNull(RuleRevision::getApproveTime)
                        .or().isNotNull(RuleRevision::getPublishTime))) > 0) return false;
        // Include offline and legacy publications; status=0 is not proof of never having shipped.
        return publishedMapper.selectCount(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, id)) == 0
                && versionMapper.selectCount(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .eq(RuleDefinitionVersion::getDefinitionId, id)) == 0;
    }

    public void discard(RuleDefinition definition, String actor) {
        for (RuleRevision revision : revisionMapper.selectList(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definition.getId())
                .in(RuleRevision::getState, "DRAFT", "REVIEW"))) {
            String fromState = revision.getState();
            revision.setState("DELETED");
            revision.setUpdateBy(actor);
            if (revisionMapper.updateById(revision) != 1) throw new IllegalStateException("草稿已变化，请刷新后重试");
            RuleLifecycleEvent event = new RuleLifecycleEvent();
            event.setDefinitionId(definition.getId());
            event.setRevisionId(revision.getId());
            event.setAction("DELETE_UNAPPROVED");
            event.setFromState(fromState);
            event.setToState("DELETED");
            event.setActor(actor);
            event.setComment("删除从未审批通过、从未发布的新增规则");
            if (eventMapper.insert(event) != 1) throw new IllegalStateException("删除草稿审计记录写入失败");
        }
        definition.setStatus(-1);
        definition.setUpdateBy(actor);
        if (definitionMapper.updateById(definition) != 1) throw new IllegalStateException("规则已变化，请刷新后重试");
    }
}
