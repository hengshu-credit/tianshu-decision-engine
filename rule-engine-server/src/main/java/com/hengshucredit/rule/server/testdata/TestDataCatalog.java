package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TestDataCatalog {
    private final JdbcTemplate jdbc;
    private final ConsoleDataPermissionHandler policy;
    private final ConsoleResourceAccess access;
    private final ConsoleProjectAccess projects;

    public boolean compatible(Long ruleId,String type,Long projectId) {
        return ruleId == null || jdbc.queryForList("SELECT value_type FROM rule_mock_rule WHERE id=? AND (project_id IS NULL OR project_id=?)",String.class,ruleId,projectId)
                .contains(MockRule.group(type));
    }

    public boolean validExample(String mode, Long ruleId, String type, Long projectId) {
        if (mode == null) return compatible(ruleId,type,projectId);
        return switch (mode) {
            case "RULE" -> ruleId != null && compatible(ruleId,type,projectId);
            case "FIXED","AUTO","NONE" -> ruleId == null;
            default -> false;
        };
    }

    List<MockRule> rules() {
        var scope = projects.current();
        return jdbc.query("SELECT m.*,p.project_name FROM rule_mock_rule m LEFT JOIN rule_project p ON p.id=m.project_id "
                +"WHERE m.project_id IS NULL OR p.status>=0 ORDER BY m.project_id IS NULL, m.seed_key IS NULL DESC,m.id DESC", (rs,i) ->
                new MockRule(rs.getLong("id"),rs.getString("name"),rs.getString("value_type"),rs.getString("match_names"),
                        rs.getString("generator"),rs.getString("config_json"),rs.getString("seed_key") != null,
                        rs.getObject("project_id",Long.class),rs.getString("project_name"))).stream()
                .filter(rule -> scope == null || scope.can("field:view",rule.projectId()) || scope.can("rule:view",rule.projectId())).toList();
    }
    List<MockRule> rulesForProject(Long projectId) {
        return rules().stream().filter(rule -> rule.projectId() == null || rule.projectId().equals(projectId)).toList();
    }
    @Transactional
    public void saveRule(Long id, MockRule rule) {
        Long projectId = rule.projectId();
        if (projectId != null && (projectId <= 0 || jdbc.queryForList("SELECT id FROM rule_project WHERE id=? AND status>=0",Long.class,projectId).isEmpty()))
            throw new IllegalArgumentException("请选择有效的所属项目");
        projects.requirePermission("field:edit",projectId);
        rule.validate();
        if (id == null) {
            jdbc.update("INSERT INTO rule_mock_rule (name,value_type,match_names,generator,config_json,project_id) VALUES (?,?,?,?,?,?)",
                    rule.name(),rule.valueType(),rule.matchNames(),rule.generator(),rule.configJson(),projectId);
        } else {
            var types = jdbc.queryForList("SELECT value_type FROM rule_mock_rule WHERE id=? AND project_id <=> ? FOR UPDATE",String.class,id,projectId);
            if (types.isEmpty()) throw new IllegalArgumentException("生成规则已删除");
            if (!types.get(0).equals(rule.valueType())) throw new IllegalArgumentException("已有规则的字段类型不可变，请新增规则");
            jdbc.update("UPDATE rule_mock_rule SET name=?,match_names=?,generator=?,config_json=? WHERE id=?",
                    rule.name(),rule.matchNames(),rule.generator(),rule.configJson(),id);
        }
    }
    @Transactional
    public void removeRule(long id) {
        var owners = jdbc.queryForList("SELECT project_id FROM rule_mock_rule WHERE id=? FOR UPDATE",Long.class,id);
        if (owners.isEmpty()) throw new IllegalArgumentException("规则已删除");
        projects.requirePermission("field:edit",owners.get(0));
        try {
            if (jdbc.update("DELETE FROM rule_mock_rule WHERE id=? AND seed_key IS NULL",id) == 0)
                throw new IllegalArgumentException("内置规则需保留，或该规则已删除");
        } catch (DataIntegrityViolationException used) { throw new IllegalArgumentException("请先解除该规则的字段绑定"); }
    }
    private String visible(TestResource kind) {
        return "(" + policy.predicate(kind.table,"r") + ")";
    }
    Map<String,Object> page(TestResource kind, boolean cases, String keyword, int page) {
        String owner = kind == TestResource.API ? "ds" : "r";
        String from = " FROM " + kind.table + " r" + (kind == TestResource.API ? " JOIN rule_external_datasource ds ON ds.id=r.datasource_id" : "")
                + " LEFT JOIN rule_project p ON p.id="+owner+".project_id";
        String name = kind == TestResource.RULE ? "c.scenario_name" : "c.case_name";
        String columns = "r.id AS ownerId,r."+kind.label+" AS resourceName,r."+kind.code+" AS code,"+owner+".scope AS scope,"+owner+".project_id AS projectId,p.project_name AS projectName";
        if (cases) {
            from += " JOIN "+kind.cases+" c ON c."+kind.owner+"=r.id";
            columns += ",c.id,"+name+" AS name";
        }
        String where = " WHERE "+visible(kind)+" AND r.status >= 0"
                +" AND (r."+kind.code+" LIKE ? OR r."+kind.label+" LIKE ?"+(cases ? " OR "+name+" LIKE ?" : "")+")";
        String filter = "%"+keyword+"%";
        List<Object> args = new ArrayList<>(List.of(filter,filter));
        if (cases) args.add(filter);
        Long total = jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());
        args.add((long)(Math.max(page,1)-1)*20);
        return Map.of("total",Objects.requireNonNullElse(total,0L),"records",jdbc.queryForList("SELECT "+columns+from+where
                +" ORDER BY "+(cases ? "c.id" : "r.id")+" DESC LIMIT 20 OFFSET ?",args.toArray()));
    }
    public record ResourceCase(String name,String code,String paramsJson,String expectedJson,Long contextDefinitionId) { }
    private void caseAccess(TestResource kind,long owner,String action) {
        if (kind == TestResource.RULE) throw new IllegalArgumentException("请选择变量、模型或外数接口");
        access.require(kind.accessType,owner,action);
    }
    ResourceCase detail(TestResource kind,long owner,long id) {
        caseAccess(kind,owner,"view");
        var rows = jdbc.query("SELECT case_name,case_code,params_json,expected_json,"+(kind == TestResource.VARIABLE ? "context_definition_id" : "NULL")
                +" FROM "+kind.cases+" WHERE "+kind.owner+"=? AND id=?", (rs,i) -> new ResourceCase(rs.getString(1),rs.getString(2),
                rs.getString(3),rs.getString(4),rs.getObject(5,Long.class)),owner,id);
        if (rows.isEmpty()) throw new IllegalArgumentException("用例已删除或不属于该资源");
        return rows.get(0);
    }
    @Transactional
    public void saveCase(TestResource kind,long owner,Long id,ResourceCase row) {
        caseAccess(kind,owner,"edit");
        if (row.name()==null || row.name().isBlank() || row.name().length()>128 || row.code()==null || row.code().isBlank() || row.code().length()>64)
            throw new IllegalArgumentException("请填写用例名称和编码");
        if (!MockRule.parse(row.paramsJson()).isObject()) throw new IllegalArgumentException("输入参数必须是 JSON 对象");
        var checks = MockRule.parse(row.expectedJson());
        if (!checks.isArray() || checks.isEmpty()) throw new IllegalArgumentException("请至少配置一个预期断言");
        for (var check : checks) {
            var path = check.path("path");
            var value = check.path("value");
            String op = check.path("op").asText("");
            if (!path.isArray() || path.isEmpty() || !path.valueStream().allMatch(k -> k.isTextual() || k.isIntegralNumber())
                    || !Set.of("eq","between","reportPath").contains(op) || "eq".equals(op) && value.isMissingNode()
                    || "between".equals(op) && (!value.isArray() || value.size()!=2 || !value.get(0).isNumber()
                    || !value.get(1).isNumber() || value.get(0).decimalValue().compareTo(value.get(1).decimalValue())>0))
                throw new IllegalArgumentException("请核对断言路径、操作和预期值");
        }
        List<Object> args = new ArrayList<>(List.of(row.name(),row.code(),row.paramsJson(),row.expectedJson()));
        String fields = "case_name=?,case_code=?,params_json=?,expected_json=?";
        if (kind == TestResource.VARIABLE) {
            if (row.contextDefinitionId()!=null) access.require("RULE",row.contextDefinitionId(),"view");
            fields += ",context_definition_id=?";
            args.add(row.contextDefinitionId());
        }
        args.add(owner);
        if (id == null) {
            jdbc.update("INSERT INTO "+kind.cases+" SET "+fields+","+kind.owner+"=?",args.toArray());
        } else {
            args.add(id);
            if (jdbc.update("UPDATE "+kind.cases+" SET "+fields+" WHERE "+kind.owner+"=? AND id=?",args.toArray())==0)
                throw new IllegalArgumentException("用例已删除");
        }
    }
    @Transactional
    public void saveCaseInput(TestResource kind,long owner,long id,String params,String original) {
        caseAccess(kind,owner,"edit");
        if (!MockRule.parse(params).isObject()) throw new IllegalArgumentException("输入参数必须是 JSON 对象");
        if (jdbc.update("UPDATE "+kind.cases+" SET params_json=? WHERE "+kind.owner+"=? AND id=? AND params_json=CAST(? AS JSON)",
                params,owner,id,original)==0) throw new IllegalArgumentException("用例已修改或删除，请刷新后再保存");
    }
    void removeCase(TestResource kind,long owner,long id) {
        caseAccess(kind,owner,"edit");
        jdbc.update("DELETE FROM "+kind.cases+" WHERE "+kind.owner+"=? AND id=?",owner,id);
    }
}
