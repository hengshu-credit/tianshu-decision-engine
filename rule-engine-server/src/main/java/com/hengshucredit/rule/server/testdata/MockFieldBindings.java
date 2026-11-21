package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MockFieldBindings {
    enum Target {
        VARIABLE("rule_variable","variable_id","VARIABLE","var_code","var_type"),
        DATA_OBJECT_ROOT("rule_data_object","object_id","DATA_OBJECT_ROOT","object_code",null),
        DATA_OBJECT("rule_data_object_field","field_id","DATA_OBJECT_FIELD","var_code","var_type");
        final String table, column, permissionType, code, type;
        Target(String table,String column,String permissionType,String code,String type) {
            this.table=table; this.column=column; this.permissionType=permissionType; this.code=code; this.type=type;
        }
    }
    private final JdbcTemplate jdbc;
    private final ConsoleResourceAccess access;
    private final ConsoleDataPermissionHandler policy;
    private final TestDataCatalog catalog;

    private String from(Target t) {
        return " FROM rule_mock_field_binding b JOIN "+t.table+" r ON r.id=b."+t.column+" WHERE ("+policy.predicate(t.table,"r")+")";
    }
    List<Map<String,Object>> list(long method) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (var t : Target.values()) result.addAll(jdbc.queryForList("SELECT '"+t+"' AS refType,r.id AS refId,r."+t.code+" AS code"
                +from(t)+" AND b.rule_id=? ORDER BY r.id", method));
        return result;
    }
    Map<Long,Long> matches(Target t, Collection<Long> ids) {
        Map<Long,Long> result = new HashMap<>();
        if (!ids.isEmpty()) jdbc.query("SELECT r.id,b.rule_id"+from(t)+" AND r.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",
                rs -> {result.put(rs.getLong(1),rs.getLong(2));},ids.toArray());
        return result;
    }
    @Transactional
    public void bind(long method, Target t, long id, boolean remove) {
        access.require(t.permissionType,id,"edit");
        if (remove) {
            jdbc.update("DELETE FROM rule_mock_field_binding WHERE rule_id=? AND "+t.column+"=?",method,id);
            return;
        }
        var rows=jdbc.queryForList("SELECT "+(t.type==null ? "'OBJECT'" : t.type)+" FROM "+t.table
                +" WHERE id=? AND status=1"+(t==Target.VARIABLE ? " AND var_source='INPUT'" : "")+" FOR UPDATE",String.class,id);
        if (rows.isEmpty() || !catalog.compatible(method,rows.get(0))) throw new IllegalArgumentException("请选择与生成方法类型相同的有效输入字段");
        var existing=jdbc.queryForList("SELECT rule_id FROM rule_mock_field_binding WHERE "+t.column+"=?",Long.class,id);
        if (existing.contains(method)) return;
        if (!existing.isEmpty()) throw new IllegalArgumentException("该字段已关联其他方法，请先解除原关联");
        jdbc.update("INSERT INTO rule_mock_field_binding (rule_id,"+t.column+") VALUES (?,?)",method,id);
    }
}
