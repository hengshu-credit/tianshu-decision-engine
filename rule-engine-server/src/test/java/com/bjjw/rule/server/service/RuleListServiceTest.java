package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleListRecord;
import com.bjjw.rule.model.entity.RuleListRecordLog;
import com.bjjw.rule.server.mapper.RuleListRecordLogMapper;
import com.bjjw.rule.server.mapper.RuleListRecordMapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleListServiceTest {

    @Test
    public void templateUsesUiItemTypeLabelsAndDropdown() {
        RuleListService service = new RuleListService();

        Workbook workbook = service.createTemplateWorkbook();
        Sheet sheet = workbook.getSheetAt(0);
        List<? extends DataValidation> validations = sheet.getDataValidations();

        assertEquals("手机号", sheet.getRow(1).getCell(1).getStringCellValue());
        assertTrue(validations.size() > 0);
        assertEquals("手机号", validations.get(0).getValidationConstraint().getExplicitListValues()[0]);
    }

    @Test
    public void importTrimsContentAndParsesUiItemTypeLabel() throws Exception {
        RuleListService service = new RuleListService();
        FakeRecordMapper recordMapper = new FakeRecordMapper();
        FakeLogMapper logMapper = new FakeLogMapper();
        setField(service, "recordMapper", recordMapper.proxy());
        setField(service, "logMapper", logMapper.proxy());

        byte[] bytes = workbookBytes();
        MockMultipartFile file = new MockMultipartFile("file", "list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        service.importRecords(9L, file);

        assertEquals("138 001 38000", recordMapper.inserted.getItemContent());
        assertEquals("MOBILE", recordMapper.inserted.getItemType());
        assertEquals("测试原因", recordMapper.inserted.getReason());
        assertEquals("测试备注", recordMapper.inserted.getRemark());
        assertEquals("MOBILE", logMapper.inserted.getItemType());
    }

    private byte[] workbookBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("名单内容");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("名单内容");
            header.createCell(1).setCellValue("内容类型");
            header.createCell(2).setCellValue("生效时间");
            header.createCell(3).setCellValue("失效时间");
            header.createCell(4).setCellValue("插入原因");
            header.createCell(5).setCellValue("插入备注");
            header.createCell(6).setCellValue("执行操作");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("  138 001 38000 \n");
            row.createCell(1).setCellValue(" 手机号 ");
            row.createCell(4).setCellValue(" 测试原因 ");
            row.createCell(5).setCellValue(" 测试备注 ");
            row.createCell(6).setCellValue(" 新增 ");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeRecordMapper {
        private RuleListRecord inserted;

        private RuleListRecordMapper proxy() {
            return (RuleListRecordMapper) Proxy.newProxyInstance(
                    RuleListRecordMapper.class.getClassLoader(),
                    new Class[]{RuleListRecordMapper.class},
                    (proxy, method, args) -> {
                        if ("selectOne".equals(method.getName())) {
                            return null;
                        }
                        if ("insert".equals(method.getName())) {
                            inserted = (RuleListRecord) args[0];
                            inserted.setId(99L);
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static class FakeLogMapper {
        private RuleListRecordLog inserted;

        private RuleListRecordLogMapper proxy() {
            return (RuleListRecordLogMapper) Proxy.newProxyInstance(
                    RuleListRecordLogMapper.class.getClassLoader(),
                    new Class[]{RuleListRecordLogMapper.class},
                    (proxy, method, args) -> {
                        if ("insert".equals(method.getName())) {
                            inserted = (RuleListRecordLog) args[0];
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
