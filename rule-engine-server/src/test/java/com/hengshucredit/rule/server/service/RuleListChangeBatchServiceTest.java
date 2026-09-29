package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.RuleListChangeBatchResult;
import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleListChangeBatch;
import com.hengshucredit.rule.model.entity.RuleListChangeItem;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.hengshucredit.rule.server.governance.GovernanceIssue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleListChangeBatchServiceTest {

    @Test
    public void singleAddStagesOneItemWithoutChangingEffectiveRecords() {
        TestService service = new TestService();
        RuleListRecord record = record(null, "MOBILE", "13800138000");

        RuleListChangeBatchResult result = service.stageSingle(
                9L, change("ADD", record), "owner");

        Assert.assertTrue(result.isSubmittable());
        Assert.assertEquals(Integer.valueOf(1), result.getAddCount());
        Assert.assertEquals(Integer.valueOf(0), result.getInvalidCount());
        Assert.assertEquals(Long.valueOf(81L), result.getApprovalRequestId());
        Assert.assertEquals(1, service.items.size());
        Assert.assertEquals("VALID",
                service.items.get(0).getValidationStatus());
        Assert.assertEquals(0, service.effectiveWrites);
        Assert.assertEquals(0, service.logWrites);

        Map<String, Object> snapshot = JSON.parseObject(
                service.draft.getSnapshotJson());
        Assert.assertEquals(9L,
                ((Number) snapshot.get("listId")).longValue());
        Assert.assertEquals("mobile_black", snapshot.get("listCode"));
        String snapshotJson = service.draft.getSnapshotJson();
        Assert.assertFalse(snapshotJson.contains("13800138000"));
        Assert.assertTrue(snapshotJson.contains("138****8000"));
    }

    @Test
    public void missingUpdateTargetIsStoredAsInvalidWithoutApproval() {
        TestService service = new TestService();
        RuleListRecord record = record(404L, "MOBILE", "13900139000");

        RuleListChangeBatchResult result = service.stageSingle(
                9L, change("UPDATE", record), "owner");

        Assert.assertFalse(result.isSubmittable());
        Assert.assertEquals(Integer.valueOf(1), result.getInvalidCount());
        Assert.assertNull(result.getApprovalRequestId());
        Assert.assertNull(service.draft);
        Assert.assertEquals("VALIDATION_FAILED", service.batch.getStatus());
        Assert.assertTrue(result.getErrors().get(0).contains("名单记录不存在"));
    }

    @Test
    public void importCountsValidDuplicateAndInvalidRowsBeforeApproval()
            throws Exception {
        TestService service = new TestService();

        RuleListChangeBatchResult result = service.stageImport(
                9L, workbookWithMixedRows(), "owner");

        Assert.assertFalse(result.isSubmittable());
        Assert.assertEquals(Integer.valueOf(3), result.getTotalCount());
        Assert.assertEquals(Integer.valueOf(1), result.getAddCount());
        Assert.assertEquals(Integer.valueOf(1), result.getDuplicateCount());
        Assert.assertEquals(Integer.valueOf(1), result.getInvalidCount());
        Assert.assertEquals(List.of("VALID", "DUPLICATE", "INVALID"),
                service.items.stream()
                        .map(RuleListChangeItem::getValidationStatus)
                        .toList());
        Assert.assertNull(service.draft);
        Assert.assertEquals(0, service.effectiveWrites);
    }

    @Test
    public void normalizedBatchDigestIsStableAcrossGeneratedIds() {
        TestService first = new TestService();
        TestService second = new TestService();
        second.nextBatchId = 501L;
        RuleListRecord record = record(null, "MOBILE", "13800138000");

        RuleListChangeBatchResult left = first.stageSingle(
                9L, change("ADD", record), "owner");
        RuleListChangeBatchResult right = second.stageSingle(
                9L, change("ADD", record), "owner");

        Assert.assertEquals(left.getContentDigest(),
                right.getContentDigest());
    }

    @Test
    public void approvalAppliesStagedBatchAndWritesAuditLog() {
        TestService service = new TestService();
        RuleListChangeBatchResult staged = service.stageSingle(
                9L, change("ADD",
                        record(null, "MOBILE", "13800138000")),
                "owner");

        Long appliedId = service.applyBatch(staged.getBatchId(),
                staged.getContentDigest(), "reviewer");

        Assert.assertEquals(staged.getBatchId(), appliedId);
        Assert.assertEquals("APPLIED", service.batch.getStatus());
        Assert.assertEquals("reviewer", service.batch.getAppliedBy());
        Assert.assertEquals(1, service.effectiveWrites);
        Assert.assertEquals(1, service.logWrites);
        Assert.assertEquals("ADD", service.logs.get(0).getOperation());
        Assert.assertEquals("reviewer", service.logs.get(0).getOperator());
    }

    @Test
    public void deleteByStableIdDoesNotRequireBrowserToRepeatContent() {
        TestService service = new TestService();
        RuleListRecord existing = record(
                23L, "MOBILE", "13700137000");
        service.putRecord(existing);
        RuleListRecord byIdOnly = new RuleListRecord();
        byIdOnly.setId(23L);

        RuleListChangeBatchResult result = service.stageSingle(
                9L, change("DELETE", byIdOnly), "owner");

        Assert.assertTrue(result.isSubmittable());
        Assert.assertEquals("13700137000",
                service.items.get(0).getItemContent());
        Assert.assertNotNull(service.items.get(0).getBaselineDigest());
    }

    @Test
    public void changedSecondBaselineBlocksEntireBatchBeforeAnyWrite()
            throws Exception {
        TestService service = new TestService();
        RuleListRecord first = record(21L, "MOBILE", "13800138000");
        RuleListRecord second = record(22L, "MOBILE", "13900139000");
        service.putRecord(first);
        service.putRecord(second);
        RuleListChangeBatchResult staged = service.stageImport(
                9L, workbookWithUpdates(), "owner");
        Assert.assertEquals(List.of(21L, 22L), service.items.stream()
                .map(RuleListChangeItem::getTargetRecordId).toList());
        Assert.assertSame(second, service.loadRecord(22L));
        second.setRemark("changed after submit");

        List<GovernanceIssue> issues = service.validateBatch(
                staged.getBatchId(), staged.getContentDigest());

        Assert.assertTrue(issues.toString(), issues.stream().anyMatch(issue ->
                "LIST_BATCH_BASELINE_CHANGED".equals(issue.code())));
        try {
            service.applyBatch(staged.getBatchId(),
                    staged.getContentDigest(), "reviewer");
            Assert.fail("conflicted batch must not apply");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("baseline"));
        }
        Assert.assertEquals(0, service.effectiveWrites);
        Assert.assertEquals(0, service.logWrites);
        Assert.assertEquals("STAGED", service.batch.getStatus());
    }

    private RuleListRecordChangeRequest change(
            String operation, RuleListRecord record) {
        RuleListRecordChangeRequest request =
                new RuleListRecordChangeRequest();
        request.setOperation(operation);
        request.setRecord(record);
        return request;
    }

    private RuleListRecord record(
            Long id, String itemType, String itemContent) {
        RuleListRecord record = new RuleListRecord();
        record.setId(id);
        record.setItemType(itemType);
        record.setItemContent(itemContent);
        record.setStatus(1);
        return record;
    }

    private MockMultipartFile workbookWithMixedRows() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("名单内容");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("名单内容");
            header.createCell(1).setCellValue("内容类型");
            header.createCell(2).setCellValue("生效时间");
            header.createCell(3).setCellValue("失效时间");
            header.createCell(6).setCellValue("执行操作");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("13800138000");
            first.createCell(1).setCellValue("手机号");
            first.createCell(6).setCellValue("新增");
            Row duplicate = sheet.createRow(2);
            duplicate.createCell(0).setCellValue("13800138000");
            duplicate.createCell(1).setCellValue("手机号");
            duplicate.createCell(6).setCellValue("新增");
            Row invalid = sheet.createRow(3);
            invalid.createCell(0).setCellValue("13900139000");
            invalid.createCell(1).setCellValue("手机号");
            invalid.createCell(2).setCellValue("2026-12-31 00:00:00");
            invalid.createCell(3).setCellValue("2026-01-01 00:00:00");
            invalid.createCell(6).setCellValue("新增");
            workbook.write(out);
            return new MockMultipartFile("file", "list.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private MockMultipartFile workbookWithUpdates() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("records");
            sheet.createRow(0);
            String[] contents = {"13800138000", "13900139000"};
            for (int index = 0; index < contents.length; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(contents[index]);
                row.createCell(1).setCellValue("MOBILE");
                row.createCell(4).setCellValue("reviewed");
                row.createCell(6).setCellValue("UPDATE");
            }
            workbook.write(out);
            return new MockMultipartFile("file", "updates.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private static class TestService extends RuleListChangeBatchService {
        private final RuleListLibrary library = new RuleListLibrary();
        private final Map<Long, RuleListRecord> recordsById =
                new LinkedHashMap<>();
        private final Map<String, RuleListRecord> recordsByKey =
                new LinkedHashMap<>();
        private final List<RuleListChangeItem> items = new ArrayList<>();
        private final List<RuleListRecordLog> logs = new ArrayList<>();
        private RuleListChangeBatch batch;
        private GovernanceDraftRequest draft;
        private long nextBatchId = 101L;
        private int effectiveWrites;
        private int logWrites;

        private void putRecord(RuleListRecord record) {
            if (record.getListId() == null) record.setListId(9L);
            recordsById.put(record.getId(), record);
            recordsByKey.put(record.getItemType() + "\u0000"
                    + record.getItemContent(), record);
        }

        private TestService() {
            library.setId(9L);
            library.setProjectId(7L);
            library.setScope("PROJECT");
            library.setListCode("mobile_black");
            library.setListName("手机号黑名单");
            library.setStatus(1);
        }

        @Override
        protected RuleListLibrary loadLibrary(Long listId) {
            return Long.valueOf(9L).equals(listId) ? library : null;
        }

        @Override
        protected RuleListRecord loadRecord(Long recordId) {
            return recordsById.get(recordId);
        }

        @Override
        protected RuleListRecord findRecord(Long listId,
                                            String itemType,
                                            String itemContent) {
            return recordsByKey.get(itemType + "\u0000" + itemContent);
        }

        @Override
        protected RuleListChangeBatch insertBatch(
                RuleListChangeBatch value) {
            value.setId(nextBatchId++);
            batch = value;
            return value;
        }

        @Override
        protected RuleListChangeItem insertItem(
                RuleListChangeItem value) {
            value.setId((long) items.size() + 1L);
            items.add(value);
            return value;
        }

        @Override
        protected RuleListChangeBatch loadBatch(Long batchId) {
            return batch != null && batch.getId().equals(batchId)
                    ? batch : null;
        }

        @Override
        protected RuleListChangeBatch loadBatchForUpdate(Long batchId) {
            return loadBatch(batchId);
        }

        @Override
        protected List<RuleListChangeItem> loadBatchItems(Long batchId) {
            return items.stream()
                    .filter(item -> batchId.equals(item.getBatchId()))
                    .toList();
        }

        @Override
        protected RuleListRecord loadRecordForUpdate(Long recordId) {
            return loadRecord(recordId);
        }

        @Override
        protected RuleListRecord findRecordForUpdate(
                Long listId, String itemType, String itemContent) {
            return findRecord(listId, itemType, itemContent);
        }

        @Override
        protected void persistRecord(RuleListRecord record) {
            if (record.getId() == null) {
                record.setId(1000L + effectiveWrites);
            }
            putRecord(record);
            effectiveWrites++;
        }

        @Override
        protected void persistLog(RuleListRecordLog log) {
            logs.add(log);
            logWrites++;
        }

        @Override
        protected void updateBatch(RuleListChangeBatch value) {
            batch = value;
        }

        @Override
        protected GovernanceApprovalRequest createApproval(
                GovernanceDraftRequest request, String actor) {
            draft = request;
            GovernanceApprovalRequest approval =
                    new GovernanceApprovalRequest();
            approval.setId(81L);
            return approval;
        }
    }
}
