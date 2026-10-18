package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.RuleListChangeBatchResult;
import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleListChangeBatch;
import com.hengshucredit.rule.model.entity.RuleListChangeItem;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceIssue;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.mapper.RuleListChangeBatchMapper;
import com.hengshucredit.rule.server.mapper.RuleListChangeItemMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleListRecordLogMapper;
import com.hengshucredit.rule.server.mapper.RuleListRecordMapper;
import jakarta.annotation.Resource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RuleListChangeBatchService {

    private static final String STAGED = "STAGED";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String VALID = "VALID";
    private static final String DUPLICATE = "DUPLICATE";
    private static final String INVALID = "INVALID";
    private static final Set<String> OPERATIONS = Set.of(
            "ADD", "UPDATE", "DELETE");
    private static final Set<String> ITEM_TYPES = Set.of(
            "MOBILE", "ID_CARD", "ADDRESS", "IP", "DEVICE",
            "NAME", "GPS", "EMAIL", "BANK_CARD", "OTHER");
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int ERROR_LIMIT = 20;
    private static final int SAMPLE_LIMIT = 5;
    private static final int MAX_IMPORT_ROWS = 100_000;

    @Resource
    private RuleListChangeBatchMapper batchMapper;
    @Resource
    private RuleListChangeItemMapper itemMapper;
    @Resource
    private RuleListLibraryMapper libraryMapper;
    @Resource
    private RuleListRecordMapper recordMapper;
    @Resource
    private RuleListRecordLogMapper logMapper;
    @Resource
    @Lazy
    private GovernanceApprovalService approvalService;

    @Transactional
    public RuleListChangeBatchResult stageSingle(
            Long listId, RuleListRecordChangeRequest request,
            String actor) {
        if (request == null || request.getRecord() == null) {
            throw new IllegalArgumentException("名单变更内容不能为空");
        }
        String operation = hasText(request.getOperation())
                ? request.getOperation()
                : request.getRecord().getLastOperation();
        return stage(listId, "SINGLE", List.of(new Candidate(
                1, operation, copyRecord(request.getRecord()), null)),
                actor);
    }

    @Transactional
    public RuleListChangeBatchResult stageImport(
            Long listId, MultipartFile file, String actor) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        return stage(listId, "IMPORT", parseWorkbook(file), actor);
    }

    public ResourceSnapshot loadBatchSnapshot(Long batchId) {
        RuleListChangeBatch batch = requireBatch(loadBatch(batchId), batchId);
        RuleListLibrary library = loadLibrary(batch.getListId());
        if (library == null) {
            throw new IllegalArgumentException(
                    "名单变更批次所属名单库不存在");
        }
        List<RuleListChangeItem> items = loadBatchItems(batchId);
        return new ResourceSnapshot(CanonicalJson.write(
                batchSnapshot(batch, library, items)),
                "ACTIVE", null, null);
    }

    public List<GovernanceIssue> validateBatch(
            Long batchId, String expectedDigest) {
        RuleListChangeBatch batch = loadBatch(batchId);
        if (batch == null) {
            return List.of(issue("LIST_BATCH_NOT_FOUND",
                    "名单变更批次不存在", batchId, "$"));
        }
        return validateBatchState(batch, loadBatchItems(batchId),
                expectedDigest, false);
    }

    @Transactional
    public Long applyBatch(Long batchId, String expectedDigest,
                           String actor) {
        String operator = requireActor(actor);
        RuleListChangeBatch batch = requireBatch(
                loadBatchForUpdate(batchId), batchId);
        List<RuleListChangeItem> items = loadBatchItems(batchId);
        List<GovernanceIssue> issues = validateBatchState(
                batch, items, expectedDigest, true);
        if (!issues.isEmpty()) {
            throw new IllegalStateException(
                    "list batch baseline validation failed: "
                            + issues.get(0).message());
        }
        try {
            for (RuleListChangeItem item : items) {
                if (VALID.equals(item.getValidationStatus())) {
                    applyItem(batch.getListId(), item, operator);
                }
            }
        } catch (DuplicateKeyException collision) {
            throw new IllegalStateException(
                    "名单记录已被并发修改，本批次未生效，请重新发起审批",
                    collision);
        }
        batch.setStatus("APPLIED");
        batch.setAppliedBy(operator);
        batch.setAppliedTime(LocalDateTime.now());
        batch.setTerminalMessage(null);
        updateBatch(batch);
        return batch.getId();
    }

    @Transactional
    public void terminateBatch(Long batchId, String terminalStatus,
                               String message) {
        RuleListChangeBatch batch = requireBatch(
                loadBatchForUpdate(batchId), batchId);
        if (!STAGED.equals(batch.getStatus())) {
            return;
        }
        batch.setStatus(required(terminalStatus,
                "批次终态不能为空").toUpperCase(Locale.ROOT));
        batch.setTerminalMessage(trimToNull(message));
        updateBatch(batch);
    }

    private List<GovernanceIssue> validateBatchState(
            RuleListChangeBatch batch,
            List<RuleListChangeItem> items,
            String expectedDigest,
            boolean lockRecords) {
        List<GovernanceIssue> issues = new ArrayList<>();
        Long batchId = batch.getId();
        if (!STAGED.equals(batch.getStatus())) {
            issues.add(issue("LIST_BATCH_NOT_STAGED",
                    "名单变更批次已结束，不能重复处理", batchId, "$"));
            return issues;
        }
        String actualDigest = digest(items);
        if (!Objects.equals(batch.getContentDigest(), expectedDigest)
                || !Objects.equals(batch.getContentDigest(), actualDigest)) {
            issues.add(issue("LIST_BATCH_CONTENT_CHANGED",
                    "名单变更内容已变化，请重新发起审批", batchId,
                    "$.contentDigest"));
        }
        BatchCounts actualCounts = counts(items);
        if (!sameCounts(batch, actualCounts)) {
            issues.add(issue("LIST_BATCH_COUNTS_CHANGED",
                    "名单变更统计与明细不一致，请重新发起审批", batchId,
                    "$.totalCount"));
        }
        try {
            requireLibrary(batch.getListId());
        } catch (IllegalArgumentException missingLibrary) {
            issues.add(issue("LIST_LIBRARY_NOT_FOUND",
                    missingLibrary.getMessage(), batchId, "$.listId"));
        }
        for (int index = 0; index < items.size(); index++) {
            RuleListChangeItem item = items.get(index);
            if (!VALID.equals(item.getValidationStatus())) continue;
            String path = "$.items[" + index + "]";
            RuleListRecord target = item.getTargetRecordId() == null
                    ? null : loadRecord(item.getTargetRecordId(), lockRecords);
            if ("ADD".equals(item.getOperation())) {
                RuleListRecord existing = findRecord(batch.getListId(),
                        item.getItemType(), item.getItemContent(), lockRecords);
                if (existing != null) {
                    issues.add(issue("LIST_BATCH_TARGET_EXISTS",
                            "待新增的名单内容已存在", batchId, path));
                }
                continue;
            }
            if (target == null
                    || !batch.getListId().equals(target.getListId())) {
                issues.add(issue("LIST_BATCH_TARGET_MISSING",
                        "待变更的名单记录已不存在", batchId, path));
                continue;
            }
            if (!Objects.equals(item.getBaselineDigest(),
                    recordDigest(target))) {
                issues.add(issue("LIST_BATCH_BASELINE_CHANGED",
                        "名单记录基线已变化，请重新发起审批",
                        batchId, path));
                continue;
            }
            if ("UPDATE".equals(item.getOperation())) {
                RuleListRecord duplicate = findRecord(batch.getListId(),
                        item.getItemType(), item.getItemContent(), lockRecords);
                if (duplicate != null
                        && !duplicate.getId().equals(target.getId())) {
                    issues.add(issue("LIST_BATCH_TARGET_EXISTS",
                            "修改后的名单内容已存在", batchId, path));
                }
            }
        }
        return issues;
    }

    private void applyItem(Long listId, RuleListChangeItem item,
                           String actor) {
        RuleListRecord record;
        if ("ADD".equals(item.getOperation())) {
            record = new RuleListRecord();
            record.setListId(listId);
        } else {
            record = loadRecordForUpdate(item.getTargetRecordId());
            if (record == null) {
                throw new IllegalStateException(
                        "list record disappeared during batch apply");
            }
        }
        if ("DELETE".equals(item.getOperation())) {
            record.setStatus(0);
        } else {
            record.setItemType(item.getItemType());
            record.setItemContent(item.getItemContent());
            record.setEffectiveTime(item.getEffectiveTime());
            record.setExpireTime(item.getExpireTime());
            record.setReason(item.getReason());
            record.setRemark(item.getRemark());
            record.setStatus(item.getTargetStatus());
        }
        record.setLastOperation(item.getOperation());
        persistRecord(record);
        RuleListRecordLog log = new RuleListRecordLog();
        log.setListId(record.getListId());
        log.setRecordId(record.getId());
        log.setItemType(record.getItemType());
        log.setItemContent(record.getItemContent());
        log.setEffectiveTime(record.getEffectiveTime());
        log.setExpireTime(record.getExpireTime());
        log.setReason(record.getReason());
        log.setRemark(record.getRemark());
        log.setOperation(item.getOperation());
        log.setOperator(actor);
        persistLog(log);
    }

    private RuleListChangeBatchResult stage(
            Long listId, String sourceType,
            List<Candidate> candidates, String actor) {
        RuleListLibrary library = requireLibrary(listId);
        String operator = requireActor(actor);
        List<RuleListChangeItem> items = validateCandidates(
                listId, candidates);
        BatchCounts counts = counts(items);
        String digest = digest(items);
        boolean submittable = counts.invalidCount == 0
                && counts.validCount() > 0;

        RuleListChangeBatch batch = new RuleListChangeBatch();
        batch.setListId(listId);
        batch.setSourceType(sourceType);
        batch.setStatus(submittable ? STAGED : VALIDATION_FAILED);
        batch.setTotalCount(counts.totalCount);
        batch.setAddCount(counts.addCount);
        batch.setUpdateCount(counts.updateCount);
        batch.setDeleteCount(counts.deleteCount);
        batch.setDuplicateCount(counts.duplicateCount);
        batch.setInvalidCount(counts.invalidCount);
        batch.setContentDigest(digest);
        batch.setCreatedBy(operator);
        if (!submittable && items.isEmpty()) {
            batch.setTerminalMessage("文件中没有可处理的名单数据");
        }
        insertBatch(batch);
        for (RuleListChangeItem item : items) {
            item.setBatchId(batch.getId());
            insertItem(item);
        }

        Long approvalRequestId = null;
        if (submittable) {
            GovernanceApprovalRequest approval = createApproval(
                    approvalDraft(batch, library, items), operator);
            approvalRequestId = approval.getId();
            batch.setApprovalRequestId(approvalRequestId);
            updateBatch(batch);
        }
        return result(batch, items, submittable, approvalRequestId);
    }

    private List<RuleListChangeItem> validateCandidates(
            Long listId, List<Candidate> candidates) {
        List<RuleListChangeItem> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Set<Long> seenTargets = new HashSet<>();
        for (Candidate candidate : candidates) {
            RuleListChangeItem item = toItem(candidate);
            if (candidate.error != null) {
                invalidate(item, candidate.error);
                result.add(item);
                continue;
            }
            try {
                normalize(item);
                String key = item.getItemType() + "\u0000"
                        + item.getItemContent();
                RuleListRecord target = resolveTarget(listId, item);
                if ("ADD".equals(item.getOperation())) {
                    if (target != null) {
                        duplicate(item, "名单内容已存在");
                    }
                } else if (target == null) {
                    invalidate(item, "名单记录不存在");
                } else {
                    item.setTargetRecordId(target.getId());
                    item.setBaselineDigest(recordDigest(target));
                    if ("DELETE".equals(item.getOperation())) {
                        copyTargetContent(item, target);
                        key = item.getItemType() + "\u0000"
                                + item.getItemContent();
                    } else {
                        RuleListRecord duplicate = findRecord(listId,
                                item.getItemType(), item.getItemContent());
                        if (duplicate != null
                                && !duplicate.getId().equals(target.getId())) {
                            duplicate(item, "修改后的名单内容已存在");
                        }
                    }
                }
                if (VALID.equals(item.getValidationStatus())) {
                    if (!seenKeys.add(key)
                            || item.getTargetRecordId() != null
                            && !seenTargets.add(item.getTargetRecordId())) {
                        duplicate(item, "同一批次存在重复变更");
                    }
                }
            } catch (IllegalArgumentException invalid) {
                invalidate(item, invalid.getMessage());
            }
            result.add(item);
        }
        return result;
    }

    private RuleListChangeItem toItem(Candidate candidate) {
        RuleListRecord record = candidate.record == null
                ? new RuleListRecord() : candidate.record;
        RuleListChangeItem item = new RuleListChangeItem();
        item.setRowNumber(candidate.rowNumber);
        item.setOperation(candidate.operation);
        item.setTargetRecordId(record.getId());
        item.setItemType(record.getItemType());
        item.setItemContent(record.getItemContent());
        item.setEffectiveTime(record.getEffectiveTime());
        item.setExpireTime(record.getExpireTime());
        item.setReason(record.getReason());
        item.setRemark(record.getRemark());
        item.setTargetStatus(record.getStatus());
        item.setValidationStatus(VALID);
        return item;
    }

    private void normalize(RuleListChangeItem item) {
        String operation = normalizeOperation(item.getOperation());
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("执行操作只能是新增、修改或删除");
        }
        item.setOperation(operation);
        if ("DELETE".equals(operation)
                && item.getTargetRecordId() != null
                && (!hasText(item.getItemType())
                || !hasText(item.getItemContent()))) {
            item.setTargetStatus(0);
            return;
        }
        item.setItemContent(required(item.getItemContent(),
                "名单内容不能为空"));
        item.setItemType(normalizeItemType(item.getItemType()));
        if (item.getEffectiveTime() != null
                && item.getExpireTime() != null
                && item.getExpireTime().isBefore(item.getEffectiveTime())) {
            throw new IllegalArgumentException("失效时间不能早于生效时间");
        }
        if (item.getTargetStatus() == null) item.setTargetStatus(1);
        if (item.getTargetStatus() != 0 && item.getTargetStatus() != 1) {
            throw new IllegalArgumentException("名单状态只能是启用或停用");
        }
    }

    private RuleListRecord resolveTarget(
            Long listId, RuleListChangeItem item) {
        if (item.getTargetRecordId() != null) {
            RuleListRecord target = loadRecord(item.getTargetRecordId());
            return target != null && listId.equals(target.getListId())
                    ? target : null;
        }
        return findRecord(listId, item.getItemType(),
                item.getItemContent());
    }

    private void copyTargetContent(RuleListChangeItem item,
                                   RuleListRecord target) {
        item.setItemType(target.getItemType());
        item.setItemContent(target.getItemContent());
        item.setEffectiveTime(target.getEffectiveTime());
        item.setExpireTime(target.getExpireTime());
        item.setReason(target.getReason());
        item.setRemark(target.getRemark());
        item.setTargetStatus(0);
    }

    private GovernanceDraftRequest approvalDraft(
            RuleListChangeBatch batch, RuleListLibrary library,
            List<RuleListChangeItem> items) {
        Map<String, Object> snapshot = batchSnapshot(
                batch, library, items);
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(GovernanceResourceTypes.LIST_RECORD_BATCH);
        draft.setProjectId("GLOBAL".equals(library.getScope())
                ? 0L : library.getProjectId());
        draft.setAction("CREATE");
        draft.setSnapshotJson(CanonicalJson.write(snapshot));
        draft.setChangeSummary("名单变更批次：新增 "
                + batch.getAddCount() + "，修改 "
                + batch.getUpdateCount() + "，删除 "
                + batch.getDeleteCount());
        return draft;
    }

    private List<Map<String, Object>> samples(
            List<RuleListChangeItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RuleListChangeItem item : items) {
            if (!VALID.equals(item.getValidationStatus())) continue;
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("operation", item.getOperation());
            sample.put("itemType", item.getItemType());
            sample.put("itemContent", mask(item.getItemContent()));
            result.add(sample);
            if (result.size() == SAMPLE_LIMIT) break;
        }
        return result;
    }

    private Map<String, Object> batchSnapshot(
            RuleListChangeBatch batch, RuleListLibrary library,
            List<RuleListChangeItem> items) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("batchId", batch.getId());
        snapshot.put("listId", library.getId());
        snapshot.put("listCode", library.getListCode());
        snapshot.put("listName", library.getListName());
        snapshot.put("sourceType", batch.getSourceType());
        snapshot.put("totalCount", batch.getTotalCount());
        snapshot.put("addCount", batch.getAddCount());
        snapshot.put("updateCount", batch.getUpdateCount());
        snapshot.put("deleteCount", batch.getDeleteCount());
        snapshot.put("duplicateCount", batch.getDuplicateCount());
        snapshot.put("invalidCount", batch.getInvalidCount());
        snapshot.put("contentDigest", batch.getContentDigest());
        snapshot.put("samples", samples(items));
        return snapshot;
    }

    private RuleListChangeBatchResult result(
            RuleListChangeBatch batch, List<RuleListChangeItem> items,
            boolean submittable, Long approvalRequestId) {
        RuleListChangeBatchResult result = new RuleListChangeBatchResult();
        result.setBatchId(batch.getId());
        result.setApprovalRequestId(approvalRequestId);
        result.setSubmittable(submittable);
        result.setTotalCount(batch.getTotalCount());
        result.setAddCount(batch.getAddCount());
        result.setUpdateCount(batch.getUpdateCount());
        result.setDeleteCount(batch.getDeleteCount());
        result.setDuplicateCount(batch.getDuplicateCount());
        result.setInvalidCount(batch.getInvalidCount());
        result.setContentDigest(batch.getContentDigest());
        List<String> errors = new ArrayList<>();
        for (RuleListChangeItem item : items) {
            if (VALID.equals(item.getValidationStatus())) continue;
            errors.add("第 " + item.getRowNumber() + " 行："
                    + item.getValidationMessage());
            if (errors.size() == ERROR_LIMIT) break;
        }
        if (items.isEmpty()) errors.add("文件中没有可处理的名单数据");
        result.setErrors(errors);
        return result;
    }

    private BatchCounts counts(List<RuleListChangeItem> items) {
        BatchCounts result = new BatchCounts();
        result.totalCount = items.size();
        for (RuleListChangeItem item : items) {
            if (DUPLICATE.equals(item.getValidationStatus())) {
                result.duplicateCount++;
            } else if (INVALID.equals(item.getValidationStatus())) {
                result.invalidCount++;
            } else if ("ADD".equals(item.getOperation())) {
                result.addCount++;
            } else if ("UPDATE".equals(item.getOperation())) {
                result.updateCount++;
            } else if ("DELETE".equals(item.getOperation())) {
                result.deleteCount++;
            }
        }
        return result;
    }

    private String digest(List<RuleListChangeItem> items) {
        List<Map<String, Object>> value = new ArrayList<>();
        for (RuleListChangeItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowNumber", item.getRowNumber());
            row.put("operation", item.getOperation());
            row.put("targetRecordId", item.getTargetRecordId());
            row.put("itemType", item.getItemType());
            row.put("itemContent", item.getItemContent());
            row.put("effectiveTime", item.getEffectiveTime());
            row.put("expireTime", item.getExpireTime());
            row.put("reason", item.getReason());
            row.put("remark", item.getRemark());
            row.put("targetStatus", item.getTargetStatus());
            row.put("validationStatus", item.getValidationStatus());
            row.put("validationMessage", item.getValidationMessage());
            row.put("baselineDigest", item.getBaselineDigest());
            value.add(row);
        }
        return Sha256Digests.text(CanonicalJson.write(value));
    }

    public String recordDigest(RuleListRecord record) {
        if (record == null) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", record.getId());
        value.put("listId", record.getListId());
        value.put("itemType", record.getItemType());
        value.put("itemContent", record.getItemContent());
        value.put("effectiveTime", record.getEffectiveTime());
        value.put("expireTime", record.getExpireTime());
        value.put("reason", record.getReason());
        value.put("remark", record.getRemark());
        value.put("status", record.getStatus());
        return Sha256Digests.text(CanonicalJson.write(value));
    }

    private List<Candidate> parseWorkbook(MultipartFile file)
            throws Exception {
        List<Candidate> result = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        try (InputStream input = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_IMPORT_ROWS) {
                throw new IllegalArgumentException("名单导入最多支持 " + MAX_IMPORT_ROWS + " 行");
            }
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlankRow(row, formatter)) continue;
                RuleListRecord record = new RuleListRecord();
                String operation = null;
                String error = null;
                try {
                    record.setItemContent(cellText(row, 0, formatter));
                    record.setItemType(cellText(row, 1, formatter));
                    record.setEffectiveTime(cellDateTime(
                            row.getCell(2), formatter));
                    record.setExpireTime(cellDateTime(
                            row.getCell(3), formatter));
                    record.setReason(cellText(row, 4, formatter));
                    record.setRemark(cellText(row, 5, formatter));
                    operation = cellText(row, 6, formatter);
                    record.setId(cellLong(row.getCell(8), formatter));
                    record.setStatus(1);
                } catch (RuntimeException invalid) {
                    error = invalid.getMessage();
                }
                result.add(new Candidate(index + 1, operation,
                        record, error));
            }
        }
        return result;
    }

    protected RuleListChangeBatch loadBatch(Long batchId) {
        return batchId == null ? null : batchMapper.selectById(batchId);
    }

    protected RuleListChangeBatch loadBatchForUpdate(Long batchId) {
        return batchId == null ? null : batchMapper.selectOne(
                new LambdaQueryWrapper<RuleListChangeBatch>()
                        .eq(RuleListChangeBatch::getId, batchId)
                        .last("FOR UPDATE"));
    }

    protected List<RuleListChangeItem> loadBatchItems(Long batchId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<RuleListChangeItem>()
                        .eq(RuleListChangeItem::getBatchId, batchId)
                        .orderByAsc(RuleListChangeItem::getRowNumber)
                        .orderByAsc(RuleListChangeItem::getId));
    }

    protected RuleListLibrary loadLibrary(Long listId) {
        return libraryMapper.selectById(listId);
    }

    protected RuleListRecord loadRecord(Long recordId) {
        return recordMapper.selectById(recordId);
    }

    protected RuleListRecord loadRecordForUpdate(Long recordId) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<RuleListRecord>()
                        .eq(RuleListRecord::getId, recordId)
                        .last("FOR UPDATE"));
    }

    protected RuleListRecord findRecord(
            Long listId, String itemType, String itemContent) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<RuleListRecord>()
                        .eq(RuleListRecord::getListId, listId)
                        .eq(RuleListRecord::getItemType, itemType)
                        .eq(RuleListRecord::getItemContent, itemContent)
                        .last("LIMIT 1"));
    }

    protected RuleListRecord findRecordForUpdate(
            Long listId, String itemType, String itemContent) {
        return recordMapper.selectOne(
                new LambdaQueryWrapper<RuleListRecord>()
                        .eq(RuleListRecord::getListId, listId)
                        .eq(RuleListRecord::getItemType, itemType)
                        .eq(RuleListRecord::getItemContent, itemContent)
                        .last("LIMIT 1 FOR UPDATE"));
    }

    protected void persistRecord(RuleListRecord record) {
        int affected = record.getId() == null
                ? recordMapper.insert(record)
                : recordMapper.updateById(record);
        if (affected != 1 || record.getId() == null) {
            throw new IllegalStateException(
                    "名单记录生效失败");
        }
    }

    protected void persistLog(RuleListRecordLog log) {
        if (logMapper.insert(log) != 1) {
            throw new IllegalStateException(
                    "名单变更日志写入失败");
        }
    }

    protected RuleListChangeBatch insertBatch(
            RuleListChangeBatch batch) {
        if (batchMapper.insert(batch) != 1 || batch.getId() == null) {
            throw new IllegalStateException("名单变更批次暂存失败");
        }
        return batch;
    }

    protected RuleListChangeItem insertItem(RuleListChangeItem item) {
        if (itemMapper.insert(item) != 1 || item.getId() == null) {
            throw new IllegalStateException("名单变更明细暂存失败");
        }
        return item;
    }

    protected void updateBatch(RuleListChangeBatch batch) {
        if (batchMapper.updateById(batch) != 1) {
            throw new IllegalStateException("名单变更批次更新失败");
        }
    }

    protected GovernanceApprovalRequest createApproval(
            GovernanceDraftRequest request, String actor) {
        return approvalService.createDraft(request, actor);
    }

    private RuleListRecord loadRecord(Long recordId,
                                      boolean forUpdate) {
        return forUpdate ? loadRecordForUpdate(recordId)
                : loadRecord(recordId);
    }

    private RuleListRecord findRecord(
            Long listId, String itemType, String itemContent,
            boolean forUpdate) {
        return forUpdate
                ? findRecordForUpdate(listId, itemType, itemContent)
                : findRecord(listId, itemType, itemContent);
    }

    private RuleListChangeBatch requireBatch(
            RuleListChangeBatch batch, Long batchId) {
        if (batch == null) {
            throw new IllegalArgumentException(
                    "名单变更批次不存在: " + batchId);
        }
        return batch;
    }

    private boolean sameCounts(RuleListChangeBatch batch,
                               BatchCounts counts) {
        return Objects.equals(batch.getTotalCount(), counts.totalCount)
                && Objects.equals(batch.getAddCount(), counts.addCount)
                && Objects.equals(batch.getUpdateCount(),
                counts.updateCount)
                && Objects.equals(batch.getDeleteCount(),
                counts.deleteCount)
                && Objects.equals(batch.getDuplicateCount(),
                counts.duplicateCount)
                && Objects.equals(batch.getInvalidCount(),
                counts.invalidCount);
    }

    private GovernanceIssue issue(String code, String message,
                                  Long batchId, String path) {
        return GovernanceIssue.error(code, message,
                GovernanceResourceTypes.LIST_RECORD_BATCH,
                batchId, path);
    }

    private RuleListLibrary requireLibrary(Long listId) {
        RuleListLibrary library = listId == null
                ? null : loadLibrary(listId);
        if (library == null || Integer.valueOf(-1)
                .equals(library.getStatus())) {
            throw new IllegalArgumentException("名单库不存在");
        }
        return library;
    }

    private String requireActor(String actor) {
        if (!hasText(actor)) throw new IllegalArgumentException("操作人不能为空");
        return actor;
    }

    private void invalidate(RuleListChangeItem item, String message) {
        item.setValidationStatus(INVALID);
        item.setValidationMessage(message);
    }

    private void duplicate(RuleListChangeItem item, String message) {
        item.setValidationStatus(DUPLICATE);
        item.setValidationMessage(message);
    }

    private String normalizeOperation(String operation) {
        String value = required(operation, "执行操作不能为空");
        Map<String, String> aliases = new HashMap<>();
        aliases.put("新增", "ADD");
        aliases.put("添加", "ADD");
        aliases.put("修改", "UPDATE");
        aliases.put("更新", "UPDATE");
        aliases.put("删除", "DELETE");
        aliases.put("停用", "DELETE");
        return aliases.getOrDefault(value,
                value.toUpperCase(Locale.ROOT));
    }

    private String normalizeItemType(String itemType) {
        String value = required(itemType, "内容类型不能为空");
        Map<String, String> aliases = new HashMap<>();
        aliases.put("手机号", "MOBILE");
        aliases.put("手机", "MOBILE");
        aliases.put("身份证", "ID_CARD");
        aliases.put("地址", "ADDRESS");
        aliases.put("设备号", "DEVICE");
        aliases.put("设备", "DEVICE");
        aliases.put("姓名", "NAME");
        aliases.put("银行卡", "BANK_CARD");
        aliases.put("邮箱", "EMAIL");
        aliases.put("邮件", "EMAIL");
        aliases.put("其他", "OTHER");
        String normalized = aliases.getOrDefault(value,
                value.toUpperCase(Locale.ROOT));
        if (!ITEM_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的内容类型: " + value);
        }
        return normalized;
    }

    private String mask(String content) {
        if (content == null || content.isEmpty()) return "***";
        if (content.length() > 7) {
            return content.substring(0, 3) + "****"
                    + content.substring(content.length() - 4);
        }
        if (content.length() == 1) return "*";
        return content.substring(0, 1) + "***"
                + content.substring(content.length() - 1);
    }

    private RuleListRecord copyRecord(RuleListRecord source) {
        RuleListRecord target = new RuleListRecord();
        target.setId(source.getId());
        target.setListId(source.getListId());
        target.setItemType(source.getItemType());
        target.setItemContent(source.getItemContent());
        target.setEffectiveTime(source.getEffectiveTime());
        target.setExpireTime(source.getExpireTime());
        target.setReason(source.getReason());
        target.setRemark(source.getRemark());
        target.setLastOperation(source.getLastOperation());
        target.setStatus(source.getStatus());
        return target;
    }

    private LocalDateTime cellDateTime(
            Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return LocalDateTime.ofInstant(
                    cell.getDateCellValue().toInstant(),
                    ZoneId.systemDefault());
        }
        String text = trimToNull(formatter.formatCellValue(cell));
        if (text == null) return null;
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMAT);
        } catch (RuntimeException notDateTime) {
            try {
                return LocalDate.parse(text, DATE_FORMAT)
                        .atTime(LocalTime.MIN);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException(
                        "日期格式应为 yyyy-MM-dd HH:mm:ss", invalid);
            }
        }
    }

    private String cellText(Row row, int index,
                            DataFormatter formatter) {
        return trimToNull(formatter.formatCellValue(row.getCell(index)));
    }

    private Long cellLong(Cell cell, DataFormatter formatter) {
        String value = cell == null ? null : trimToNull(formatter.formatCellValue(cell));
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("记录ID必须是整数");
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int index = 0; index < 7; index++) {
            if (hasText(formatter.formatCellValue(row.getCell(index)))) {
                return false;
            }
        }
        return true;
    }

    private String required(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record Candidate(Integer rowNumber, String operation,
                             RuleListRecord record, String error) {
    }

    private static class BatchCounts {
        private int totalCount;
        private int addCount;
        private int updateCount;
        private int deleteCount;
        private int duplicateCount;
        private int invalidCount;

        private int validCount() {
            return addCount + updateCount + deleteCount;
        }
    }
}
