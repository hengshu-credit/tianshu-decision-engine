package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.hengshucredit.rule.server.mapper.RuleListRecordLogMapper;
import com.hengshucredit.rule.server.mapper.RuleListRecordMapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuleListServiceTest {

    @BeforeClass
    public static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleListRecordLog.class);
    }

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

    @Test
    public void pageLogsAddsEffectivePeriodChangeContent() throws Exception {
        RuleListService service = new RuleListService();
        RuleListRecordLog previous = new RuleListRecordLog();
        previous.setId(1L);
        previous.setListId(9L);
        previous.setRecordId(99L);
        previous.setItemType("MOBILE");
        previous.setItemContent("13800138000");
        previous.setEffectiveTime(LocalDateTime.of(2026, 6, 1, 0, 0));
        previous.setExpireTime(LocalDateTime.of(2026, 6, 30, 23, 59, 59));
        previous.setOperation("ADD");

        RuleListRecordLog current = new RuleListRecordLog();
        current.setId(2L);
        current.setListId(9L);
        current.setRecordId(99L);
        current.setItemType("MOBILE");
        current.setItemContent("13800138000");
        current.setEffectiveTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        current.setExpireTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        current.setOperation("UPDATE");

        FakeLogMapper logMapper = new FakeLogMapper();
        logMapper.pageRecord = current;
        logMapper.previous = previous;
        setField(service, "logMapper", logMapper.proxy());

        IPage<RuleListRecordLog> page = service.pageLogs(9L, 1, 10, null, null, null);

        String content = page.getRecords().get(0).getChangeContent();
        assertTrue(content.contains("2026-06-01 00:00:00 至 2026-06-30 23:59:59"));
        assertTrue(content.contains("2026-07-01 00:00:00 至 2026-12-31 23:59:59"));
    }

    @Test
    public void pageLogsFiltersByExactItemTypeAndContentBeforePaging() throws Exception {
        RuleListService service = new RuleListService();
        FakeLogMapper logMapper = new FakeLogMapper();
        setField(service, "logMapper", logMapper.proxy());

        service.pageLogs(9L, 2, 30, null, "MOBILE", " 13800138000 ");

        assertEquals(2L, logMapper.selectedPage.getCurrent());
        assertEquals(30L, logMapper.selectedPage.getSize());
        String sqlSegment = logMapper.selectedWrapper.getSqlSegment();
        assertTrue(sqlSegment, sqlSegment.contains("listId"));
        assertTrue(sqlSegment, sqlSegment.contains("itemType"));
        assertTrue(sqlSegment, sqlSegment.contains("itemContent"));
        assertTrue(logMapper.selectedWrapper.getParamNameValuePairs().containsValue(9L));
        assertTrue(logMapper.selectedWrapper.getParamNameValuePairs().containsValue("MOBILE"));
        assertTrue(logMapper.selectedWrapper.getParamNameValuePairs().containsValue(" 13800138000 "));
    }

    @Test
    public void pageLogsKeepsLegacyRecordIdFilter() throws Exception {
        RuleListService service = new RuleListService();
        FakeLogMapper logMapper = new FakeLogMapper();
        setField(service, "logMapper", logMapper.proxy());

        service.pageLogs(9L, 1, 10, 77L, null, null);

        String sqlSegment = logMapper.selectedWrapper.getSqlSegment();
        assertTrue(sqlSegment, sqlSegment.contains("recordId"));
        assertTrue(logMapper.selectedWrapper.getParamNameValuePairs().containsValue(77L));
    }

    @Test
    public void updateRecordChangesContentByIdWithoutInsert() throws Exception {
        RuleListService service = new RuleListService();
        FakeRecordMapper recordMapper = new FakeRecordMapper();
        FakeLogMapper logMapper = new FakeLogMapper();
        RuleListRecord existing = new RuleListRecord();
        existing.setId(4L);
        existing.setListId(9L);
        existing.setItemType("MOBILE");
        existing.setItemContent("13800138000");
        existing.setStatus(1);
        recordMapper.selectedById = existing;
        setField(service, "recordMapper", recordMapper.proxy());
        setField(service, "logMapper", logMapper.proxy());

        RuleListRecord update = new RuleListRecord();
        update.setId(4L);
        update.setItemType("MOBILE");
        update.setItemContent("13900139000");
        update.setReason("换号");
        update.setRemark("人工确认");
        update.setStatus(1);

        RuleListRecord result = service.updateRecord(9L, update);

        assertEquals("13900139000", result.getItemContent());
        assertEquals("13900139000", recordMapper.updated.getItemContent());
        assertEquals("UPDATE", recordMapper.updated.getLastOperation());
        assertEquals(4L, logMapper.inserted.getRecordId().longValue());
        assertEquals("13900139000", logMapper.inserted.getItemContent());
        assertNull(recordMapper.inserted);
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
        private RuleListRecord updated;
        private RuleListRecord selectedById;
        private RuleListRecord duplicate;

        private RuleListRecordMapper proxy() {
            return (RuleListRecordMapper) Proxy.newProxyInstance(
                    RuleListRecordMapper.class.getClassLoader(),
                    new Class[]{RuleListRecordMapper.class},
                    (proxy, method, args) -> {
                        if ("selectById".equals(method.getName())) {
                            return selectedById;
                        }
                        if ("selectOne".equals(method.getName())) {
                            return duplicate;
                        }
                        if ("insert".equals(method.getName())) {
                            inserted = (RuleListRecord) args[0];
                            inserted.setId(99L);
                            return 1;
                        }
                        if ("updateById".equals(method.getName())) {
                            updated = (RuleListRecord) args[0];
                            return 1;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static class FakeLogMapper {
        private RuleListRecordLog inserted;
        private RuleListRecordLog pageRecord;
        private RuleListRecordLog previous;
        private Page<RuleListRecordLog> selectedPage;
        private LambdaQueryWrapper<RuleListRecordLog> selectedWrapper;

        private RuleListRecordLogMapper proxy() {
            return (RuleListRecordLogMapper) Proxy.newProxyInstance(
                    RuleListRecordLogMapper.class.getClassLoader(),
                    new Class[]{RuleListRecordLogMapper.class},
                    (proxy, method, args) -> {
                        if ("selectPage".equals(method.getName())) {
                            Page<RuleListRecordLog> page = (Page<RuleListRecordLog>) args[0];
                            selectedPage = page;
                            selectedWrapper = (LambdaQueryWrapper<RuleListRecordLog>) args[1];
                            page.setRecords(pageRecord == null ? Collections.emptyList() : Collections.singletonList(pageRecord));
                            page.setTotal(page.getRecords().size());
                            return page;
                        }
                        if ("selectOne".equals(method.getName())) {
                            return previous;
                        }
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
