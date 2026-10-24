package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ListRecordNormalizationTest {
    private final RuleListService legacy = new RuleListService();
    private final RuleListChangeBatchService batches = new RuleListChangeBatchService();

    @Test
    public void bothWritePathsAcceptTheSameBusinessTypeAliases() {
        String[][] cases = {
                {"手机号", "MOBILE"}, {"手机", "MOBILE"}, {"身份证", "ID_CARD"},
                {"地址", "ADDRESS"}, {"设备号", "DEVICE"}, {"设备", "DEVICE"},
                {"姓名", "NAME"}, {"银行卡", "BANK_CARD"}, {"邮箱", "EMAIL"},
                {"邮件", "EMAIL"}, {"其他", "OTHER"}, {" mobile ", "MOBILE"},
                {"IP", "IP"}, {"GPS", "GPS"}, {"BANK_CARD", "BANK_CARD"},
        };
        for (String[] value : cases) {
            assertEquals(value[0], value[1], call(legacy, "normalizeItemType", value[0]));
            assertEquals(value[0], value[1], call(batches, "normalizeItemType", value[0]));
        }
    }

    @Test
    public void queryingAnyAndWritingInvalidTypesKeepTheirDifferentContracts() {
        assertEquals("ANY", call(legacy, "normalizeItemTypeOrNull", " 任意 "));
        for (String value : new String[]{null, "", "  ", "ANY", "unsupported"}) {
            assertNull(call(legacy, "normalizeItemTypeOrNull", value));
        }
        for (String value : new String[]{null, "", "任意", "ANY", "unsupported"}) {
            assertEquals("内容类型不能为空", assertThrows(IllegalArgumentException.class,
                    () -> call(legacy, "normalizeItemType", value)).getMessage());
        }
        assertEquals("内容类型不能为空", assertThrows(IllegalArgumentException.class,
                () -> call(batches, "normalizeItemType", "  ")).getMessage());
        assertEquals("不支持的内容类型: 任意", assertThrows(IllegalArgumentException.class,
                () -> call(batches, "normalizeItemType", " 任意 ")).getMessage());
    }

    @Test
    public void operationAliasesKeepTheLegacyDefaultAndBatchRequiredValidation() {
        String[][] cases = {{"新增", "ADD"}, {"添加", "ADD"}, {"修改", "UPDATE"},
                {"更新", "UPDATE"}, {"删除", "DELETE"}, {"停用", "DELETE"},
                {" add ", "ADD"}, {"unknown", "UNKNOWN"}};
        for (String[] value : cases) {
            assertEquals(value[1], call(legacy, "normalizeOperation", value[0]));
            assertEquals(value[1], call(batches, "normalizeOperation", value[0]));
        }
        assertEquals("ADD", call(legacy, "normalizeOperation", null));
        assertEquals("ADD", call(legacy, "normalizeOperation", "  "));
        assertEquals("执行操作不能为空", assertThrows(IllegalArgumentException.class,
                () -> call(batches, "normalizeOperation", null)).getMessage());
    }

    @Test
    public void extractingAliasesDoesNotChangeEachCallersLocalePolicy() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertNull(call(legacy, "normalizeItemTypeOrNull", "ip"));
            assertEquals("IP", call(batches, "normalizeItemType", "ip"));
            assertEquals("MOBILE", call(legacy, "normalizeItemType", "手机"));
            assertEquals("MOBILE", call(batches, "normalizeItemType", "手机"));
        } finally {
            Locale.setDefault(original);
        }
    }

    private String call(Object service, String method, String value) {
        return ReflectionTestUtils.invokeMethod(service, method, new Object[]{value});
    }
}
