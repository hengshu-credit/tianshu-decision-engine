package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.sql.Date;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IdentityStringBuiltinFunctionsTest {

    private final IdentityStringBuiltinFunctions functions = new IdentityStringBuiltinFunctions();

    @Test
    public void shouldExtractGenderAndBirthDateFromIdCard() {
        assertEquals(Integer.valueOf(1), functions.idCardGenderValue("110105200007070031"));
        assertEquals(Integer.valueOf(0), functions.idCardGenderValue("11010519491231002X"));
        assertEquals(Date.valueOf("2000-07-07"), functions.idCardBirthDateValue("110105200007070031"));
        assertEquals(Date.valueOf("1999-07-07"), functions.idCardBirthDateValue("110105990707003"));

        assertEquals(Integer.valueOf(-1), functions.idCardGenderValue("110105202302300031"));
        assertNull(functions.idCardBirthDateValue("110105202302300031"));
    }

    @Test
    public void shouldCalculateAgeWithBothModes() {
        String idCard = "110105200007070031";

        assertEquals(Integer.valueOf(26), functions.idCardAgeValue(idCard, "2026-07-06", "YEAR"));
        assertEquals(Integer.valueOf(25), functions.idCardAgeValue(idCard, "2026-07-06", "YMD"));
        assertEquals(Integer.valueOf(25), functions.idCardAgeValue(idCard, 1783267200000L, null));
        assertEquals(Integer.valueOf(-1), functions.idCardAgeValue("110105202302300031", "2026-07-06", "YMD"));
    }

    @Test
    public void shouldHandleStringAndRegexFunctions() {
        assertEquals("abc", functions.leftStringValue("abc", 10));
        assertEquals("ab", functions.leftStringValue("abc", 2));
        assertEquals("abc", functions.rightStringValue("abc", 10));
        assertEquals("bc", functions.rightStringValue("abc", 2));
        assertEquals("", functions.rightStringValue("abc", 0));
        assertNull(functions.leftStringValue(null, 2));

        assertEquals(Integer.valueOf(1), functions.regexMatchValue("AB12", "^[A-Z]{2}\\d{2}$"));
        assertEquals(Integer.valueOf(0), functions.regexMatchValue("AB123", "^[A-Z]{2}\\d{2}$"));
        assertEquals(Integer.valueOf(0), functions.regexMatchValue("AB12", "["));
    }

    @Test
    public void shouldExposeHelpersToQlExpressScript() {
        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(
                "idCardAgeValue('110105200007070031', '2026-07-06', 'YMD')",
                Collections.<String, Object>emptyMap());

        assertTrue(result.isSuccess());
        assertEquals(25, ((Number) result.getResult()).intValue());
    }
}
