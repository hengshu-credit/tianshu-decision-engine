package com.hengshucredit.rule.core.compiler;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotEquals;

public class CompilerStateTest {

    @Test
    public void compilersDoNotKeepVarContextInInstanceFields() {
        assertNoVarContextField(DecisionTableCompiler.class);
        assertNoVarContextField(CrossTableCompiler.class);
        assertNoVarContextField(ScorecardCompiler.class);
        assertNoVarContextField(AdvancedCrossTableCompiler.class);
        assertNoVarContextField(AdvancedScorecardCompiler.class);
    }

    private void assertNoVarContextField(Class<?> compilerClass) {
        for (Field field : compilerClass.getDeclaredFields()) {
            assertNotEquals(compilerClass.getSimpleName() + " must not keep VarContext in instance state",
                    VarContext.class, field.getType());
        }
    }
}
