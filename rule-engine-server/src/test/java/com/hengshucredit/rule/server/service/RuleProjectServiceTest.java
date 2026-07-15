package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleProjectServiceTest {

    @Test
    public void getModelTypeLabelSupportsCurrentAndLegacyEnums() throws Exception {
        RuleProjectService service = new RuleProjectService();
        Method method = RuleProjectService.class.getDeclaredMethod("getModelTypeLabel", String.class);
        method.setAccessible(true);

        assertEquals("交叉表", method.invoke(service, "CROSS"));
        assertEquals("交叉表", method.invoke(service, "CROSS_TABLE"));
        assertEquals("评分卡", method.invoke(service, "SCORE"));
        assertEquals("评分卡", method.invoke(service, "SCORE_CARD"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_ADV"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_TABLE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_CARD_ADV"));
        assertEquals("规则集", method.invoke(service, "RULE_SET"));
    }
    @Test
    public void exportApiDocProjectScopeIncludesLinkedGlobalRules() throws Exception {
        RuleProjectService service = new RuleProjectService();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinition.class);
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        Method method = RuleProjectService.class.getDeclaredMethod("appendProjectRuleScope",
                LambdaQueryWrapper.class, Long.class);
        method.setAccessible(true);

        method.invoke(service, wrapper, 12L);

        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql.contains("rule_definition_ref"));
        assertTrue(sql.contains("rdr.definition_id = rule_definition.id"));
        assertTrue(sql.contains("rdr.project_id = 12"));
    }

    @Test
    public void projectCreationPersistsFixedTraceScopeCodeAfterIdAllocation() {
        RecordingProjectService service = new RecordingProjectService();
        ReflectionTestUtils.setField(service, "projectAuthService", new ProjectAuthService(null) {
            @Override
            public RuleProjectAuth saveLegacyToken(RuleProject project, String token) {
                return null;
            }
        });
        RuleProject project = new RuleProject();
        project.setProjectCode("PROJECT_A");

        service.createProjectWithToken(project);

        assertEquals("00A7", project.getTraceScopeCode());
        assertTrue(service.updated);
    }

    private static class RecordingProjectService extends RuleProjectService {
        private boolean updated;

        @Override
        public boolean save(RuleProject entity) {
            entity.setId(367L);
            return true;
        }

        @Override
        public boolean updateById(RuleProject entity) {
            updated = true;
            return true;
        }
    }
}
