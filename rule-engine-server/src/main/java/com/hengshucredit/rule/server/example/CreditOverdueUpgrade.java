package com.hengshucredit.rule.server.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerPublishRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import com.hengshucredit.rule.server.service.RuleGovernanceSubmissionService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** Upgrade the previously distributed V2 example using the same revision and approval gates as the designer. */
final class CreditOverdueUpgrade {
    private static final String IMPORTER = "TIANSHU_CREDIT_EXAMPLE";
    private static final String COMMENT = "样例升级：QN0001 使用项目数据库当前最大逾期天数，主流程重新冻结依赖";
    private record Published(long id, long projectId, int version, String creator, String model, String api) {}

    static void apply(ApplicationContext context, JdbcTemplate db) {
        Published child = load(db, "NBQZ");
        Published flow = load(db, "CREDIT_ADMISSION");
        if (child.version() >= 3 && flow.version() >= 3) return;
        for (Published item : List.of(child, flow)) {
            if (item.version() != 2 || !IMPORTER.equals(item.creator()))
                throw new IllegalStateException("样例策略已自行发布过，请从控制台核对 QN0001 并发布主流程");
            if (db.queryForObject("SELECT COUNT(*) FROM rule_revision WHERE definition_id=? AND state IN ('DRAFT','REVIEW','APPROVED')", Integer.class, item.id()) != 0)
                throw new IllegalStateException("样例有正在编辑或审批的修订，请先完成现有工作");
        }

        JSONObject design = JSON.parseObject(child.model());
        var rules = design.getJSONArray("rules");
        var matches = rules.stream().map(JSONObject.class::cast).filter(r -> "QN0001".equals(r.getString("ruleCode"))).toList();
        if (matches.size() != 1) throw new IllegalStateException("原样例缺少唯一的 QN0001");
        var operand = matches.get(0).getJSONObject("conditionRoot").getJSONArray("children").getJSONObject(0).getJSONObject("leftOperand");
        var input = db.queryForObject("SELECT id FROM rule_variable WHERE scope='GLOBAL' AND project_id=0 AND var_code='current_overdue_days' AND var_source='INPUT'", Long.class);
        if (!input.equals(operand.getLong("refId")) || !"VARIABLE".equals(operand.getString("refType")))
            throw new IllegalStateException("QN0001 引用已被修改，请从控制台核对");
        var database = db.queryForMap("SELECT id,var_label FROM rule_variable WHERE project_id=? AND scope='PROJECT' AND var_code='current_overdue_days' AND var_source='DB' AND status=1", child.projectId());
        operand.put("refId", database.get("id"));
        operand.put("label", database.get("var_label"));
        operand.put("varSource", "DB");
        publish(context, child, JSON.toJSONString(design));
        publish(context, flow, flow.model());
        if (load(db,"NBQZ").version()!=3 || load(db,"CREDIT_ADMISSION").version()!=3)
            throw new IllegalStateException("样例 V3 发布未完成");
    }

    private static Published load(JdbcTemplate db, String code) {
        return db.queryForObject("""
                SELECT d.id,d.project_id,r.version,a.create_by,r.model_json,r.open_api_config_json
                FROM rule_project p JOIN rule_definition d ON d.project_id=p.id
                JOIN rule_published r ON r.definition_id=d.id JOIN decision_artifact a ON a.id=r.artifact_id
                WHERE p.project_code='HENGSHUCREDIT' AND d.rule_code=? AND r.status=1 FOR UPDATE
                """, (rs,n)->new Published(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6)),code);
    }

    private static void publish(ApplicationContext context, Published source, String modelJson) {
        var lifecycle = context.getBean(RuleLifecycleService.class);
        var draft = lifecycle.ensureDraft(source.id());
        var save = new RuleDraftSaveRequest();
        save.setDefinitionId(source.id());
        save.setRevisionId(draft.getId());
        save.setLockVersion(draft.getLockVersion());
        save.setModelJson(modelJson);
        save.setOpenApiConfigJson(source.api());
        save.setUpdateOpenApiConfig(true);
        var saved = context.getBean(RuleDraftService.class).save(save);
        if (!saved.isCompileSuccess()) throw new IllegalStateException("样例规则编译失败：" + saved.getCompileMessage());
        var publish = new RuleDesignerPublishRequest();
        publish.setRevisionId(saved.getRevision().getId());
        publish.setLockVersion(saved.getRevision().getLockVersion());
        publish.setPublishMode("NEW");
        publish.setComment(COMMENT);
        var revision = context.getBean(RuleGovernanceSubmissionService.class).submitDesigner(source.id(),publish);
        var review = new GovernanceReviewRequest();
        review.setComment(COMMENT);
        context.getBean(GovernanceApprovalService.class).approve(revision.getGovernanceRequestId(),review,IMPORTER);
    }
}
