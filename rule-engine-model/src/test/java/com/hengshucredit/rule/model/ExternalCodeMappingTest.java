package com.hengshucredit.rule.model;

import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalCodeMappingTest {
    @Test public void mappingSnapshotRoundTripsThroughClientJson() {
        var mapping = new ExternalCodeMapping(3, true, Map.of("100001", new ExternalCodeMapping.Entry("E1", "参数错误")));
        var json = com.alibaba.fastjson.JSON.parseObject(com.alibaba.fastjson.JSON.toJSONString(mapping));
        assertEquals(mapping, ExternalCodeMapping.from(json.getLongValue("version"), json));
    }
    @Test public void sharedExternalCodeNeverChangesOriginalSemantics() {
        var mapping = new ExternalCodeMapping(3, true, Map.of(
                "000000", new ExternalCodeMapping.Entry("0000", "已处理"),
                "100001", new ExternalCodeMapping.Entry("0000", "参数错误"),
                "100002", new ExternalCodeMapping.Entry("0000", "参数错误")));
        var success = ApiResponse.ok().mapExternal(mapping);
        var failure = ApiResponse.fail(PlatformCode.PARAMETER_INVALID, "age 无效").mapExternal(mapping);
        assertEquals("0000", success.getCode()); assertTrue(success.isSuccess());
        assertEquals("0000", failure.getCode()); assertFalse(failure.isSuccess());
        assertEquals(400, failure.status().httpStatus()); assertEquals("参数错误", failure.getMessage());
        assertEquals("age 无效", failure.getDetail());
        var result = new RuleResult(); result.setSuccess(false); result.setPlatformCode("100002");
        ApiResponse.ok(result).mapExternal(mapping);
        assertEquals("0000", result.getCode()); assertEquals(PlatformCode.REQUIRED_FIELD_MISSING, result.platformStatus());
        var error = new PlatformException(PlatformCode.PARAMETER_INVALID, "age 无效").mapExternal(mapping);
        assertEquals("0000", error.getCode()); assertEquals("参数错误", error.getMessage());
        assertEquals(PlatformCode.PARAMETER_INVALID, error.platformStatus());
    }

    @Test public void blankOverridesAndDisabledMappingRetainDefaults() {
        var mapping = new ExternalCodeMapping(2, true, Map.of("000000", new ExternalCodeMapping.Entry("", "")));
        assertEquals("000000", mapping.code(PlatformCode.SUCCESS));
        assertEquals("成功", mapping.message(PlatformCode.SUCCESS));
        assertEquals("300001", mapping.code(PlatformCode.TOKEN_INVALID));
        assertEquals("000000", new ExternalCodeMapping(2, false, Map.of("000000", new ExternalCodeMapping.Entry("OK", "ok"))).code(PlatformCode.SUCCESS));
    }

    @Test public void invalidSourceCodesAndNonStringTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExternalCodeMapping(1, true, Map.of("UNKNOWN", new ExternalCodeMapping.Entry("E", "错误"))));
        assertThrows(IllegalArgumentException.class, () -> ExternalCodeMapping.from(1, Map.of("enabled", true, "mappings", Map.of("000000", Map.of("code", 0)))));
        assertThrows(IllegalArgumentException.class, () -> new ExternalCodeMapping.Entry(" E", "错误"));
    }
}
