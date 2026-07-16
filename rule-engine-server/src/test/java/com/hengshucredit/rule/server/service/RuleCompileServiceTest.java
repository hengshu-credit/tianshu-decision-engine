package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.Assert.assertFalse;

public class RuleCompileServiceTest {

    @Test
    public void previewCompilationRejectsBlankModelJson() {
        RuleCompileService service = new RuleCompileService();
        ReflectionTestUtils.setField(service, "definitionService", new RuleDefinitionService() {
            @Override
            public RuleDefinition getById(Serializable id) {
                RuleDefinition definition = new RuleDefinition();
                definition.setId(10L);
                definition.setProjectId(1L);
                definition.setModelType("SCRIPT");
                return definition;
            }
        });

        CompileResult result = service.compilePreview(10L, "  ", "SCRIPT");

        assertFalse(result.isSuccess());
    }
}
