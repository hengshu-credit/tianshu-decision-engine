package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

@Repository
public class MybatisDashboardMappingRepository
        implements DashboardMappingRepository {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler consoleDataScope;

    private final RuleVariableMapper variableMapper;
    private final RuleDataObjectFieldMapper dataObjectFieldMapper;
    private final RuleDataObjectMapper dataObjectMapper;
    private final JdbcTemplate jdbc;

    public MybatisDashboardMappingRepository(
            RuleVariableMapper variableMapper,
            RuleDataObjectFieldMapper dataObjectFieldMapper,
            RuleDataObjectMapper dataObjectMapper, JdbcTemplate jdbc) {
        this.variableMapper = variableMapper;
        this.dataObjectFieldMapper = dataObjectFieldMapper;
        this.dataObjectMapper = dataObjectMapper;
        this.jdbc = jdbc;
    }

    @Override
    public SystemDefaults findSystemDefaults() {
        String sql = "SELECT mappings_json, version FROM rule_dashboard_default WHERE id = 1";
        var rows = jdbc.query(consoleDataScope == null ? sql : consoleDataScope.filterSql(sql),
                (rs, rowNum) -> new SystemDefaults(rs.getString(1), rs.getLong(2)));
        return rows.isEmpty() ? new SystemDefaults("{}", 0L) : rows.get(0);
    }

    @Override
    public void saveSystemDefaults(String mappingsJson, long version, String operator) {
        int updated;
        try {
            updated = version == 0L
                    ? jdbc.update("INSERT INTO rule_dashboard_default (id, mappings_json, version, update_by, update_time) VALUES (1, ?, 1, ?, CURRENT_TIMESTAMP)", mappingsJson, operator)
                    : jdbc.update("UPDATE rule_dashboard_default SET mappings_json = ?, version = version + 1, update_by = ?, update_time = CURRENT_TIMESTAMP WHERE id = 1 AND version = ?", mappingsJson, operator, version);
        } catch (DuplicateKeyException error) {
            throw new IllegalArgumentException("公共默认已被更新，请重新加载后再保存", error);
        }
        if (updated != 1) throw new IllegalArgumentException("公共默认已被更新，请重新加载后再保存");
    }

    @Override
    public RuleVariable findVariable(Long id) {
        return variableMapper.selectById(id);
    }

    @Override
    public RuleDataObjectField findDataObjectField(Long id) {
        return dataObjectFieldMapper.selectById(id);
    }

    @Override
    public RuleDataObject findDataObject(Long id) {
        return dataObjectMapper.selectById(id);
    }

    @Override
    public List<RuleVariable> listVariables(Long projectId) {
        LambdaQueryWrapper<RuleVariable> query =
                new LambdaQueryWrapper<RuleVariable>()
                        .eq(RuleVariable::getStatus, 1)
                        .orderByAsc(RuleVariable::getScope,
                                RuleVariable::getSortOrder,
                                RuleVariable::getId);
        if (projectId == null) {
            query.and(item -> item.eq(RuleVariable::getScope, "GLOBAL")
                    .or().eq(RuleVariable::getProjectId, 0L));
        } else {
            query.and(item -> item.eq(RuleVariable::getScope, "GLOBAL")
                    .or().eq(RuleVariable::getProjectId, 0L)
                    .or().eq(RuleVariable::getProjectId, projectId));
        }
        return variableMapper.selectList(query);
    }

    @Override
    public List<RuleDataObjectField> listDataObjectFields(Long projectId) {
        LambdaQueryWrapper<RuleDataObjectField> query =
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getStatus, 1)
                        .orderByAsc(RuleDataObjectField::getObjectId,
                                RuleDataObjectField::getSortOrder,
                                RuleDataObjectField::getId);
        if (projectId == null) {
            query.and(item -> item.eq(RuleDataObjectField::getScope, "GLOBAL")
                    .or().eq(RuleDataObjectField::getProjectId, 0L));
        } else {
            query.and(item -> item.eq(RuleDataObjectField::getScope, "GLOBAL")
                    .or().eq(RuleDataObjectField::getProjectId, 0L)
                    .or().eq(RuleDataObjectField::getProjectId, projectId));
        }
        return dataObjectFieldMapper.selectList(query);
    }
}
