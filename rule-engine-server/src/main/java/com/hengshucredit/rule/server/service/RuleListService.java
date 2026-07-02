package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleListRecord;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleListRecordLogMapper;
import com.hengshucredit.rule.server.mapper.RuleListRecordMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RuleListService extends ServiceImpl<RuleListLibraryMapper, RuleListLibrary> {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";

    private static final String[] EXCEL_HEADERS = {
            "名单内容", "内容类型", "生效时间", "失效时间", "插入原因", "插入备注", "执行操作", "插入时间"
    };
    private static final Map<String, String> ITEM_TYPE_LABELS = new LinkedHashMap<>();
    private static final String MATCH_IN_LIST = "IN_LIST";
    private static final String MATCH_NOT_IN_LIST = "NOT_IN_LIST";
    private static final String MATCH_CONTAINED_IN_LIST = "CONTAINED_IN_LIST";
    private static final String MATCH_NOT_CONTAINED_IN_LIST = "NOT_CONTAINED_IN_LIST";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static {
        ITEM_TYPE_LABELS.put("MOBILE", "手机号");
        ITEM_TYPE_LABELS.put("ID_CARD", "身份证");
        ITEM_TYPE_LABELS.put("ADDRESS", "地址");
        ITEM_TYPE_LABELS.put("IP", "IP");
        ITEM_TYPE_LABELS.put("DEVICE", "设备号");
        ITEM_TYPE_LABELS.put("NAME", "姓名");
        ITEM_TYPE_LABELS.put("GPS", "GPS");
        ITEM_TYPE_LABELS.put("EMAIL", "邮箱");
        ITEM_TYPE_LABELS.put("BANK_CARD", "银行卡");
        ITEM_TYPE_LABELS.put("OTHER", "其他");
    }

    @Resource
    private RuleListRecordMapper recordMapper;

    @Resource
    private RuleListRecordLogMapper logMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    public IPage<RuleListLibrary> pageLibraries(int pageNum, int pageSize, Long projectId, String scope,
                                                String listType, Integer status, String keyword) {
        LambdaQueryWrapper<RuleListLibrary> wrapper = new LambdaQueryWrapper<>();
        if (hasText(scope)) {
            wrapper.eq(RuleListLibrary::getScope, scope);
        }
        if (projectId != null && projectId > 0) {
            if (!hasText(scope)) {
                wrapper.and(w -> w.eq(RuleListLibrary::getScope, SCOPE_GLOBAL)
                        .or()
                        .eq(RuleListLibrary::getScope, SCOPE_PROJECT)
                        .eq(RuleListLibrary::getProjectId, projectId));
            } else if (SCOPE_PROJECT.equals(scope)) {
                wrapper.eq(RuleListLibrary::getProjectId, projectId);
            }
        }
        if (hasText(listType)) {
            wrapper.eq(RuleListLibrary::getListType, normalizeListType(listType));
        }
        if (status != null) {
            wrapper.eq(RuleListLibrary::getStatus, status);
        }
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(RuleListLibrary::getListCode, keyword)
                    .or()
                    .like(RuleListLibrary::getListName, keyword));
        }
        wrapper.orderByDesc(RuleListLibrary::getCreateTime);
        IPage<RuleListLibrary> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillLibraryExtras(page.getRecords());
        return page;
    }

    public IPage<RuleListRecord> pageRecords(Long listId, int pageNum, int pageSize, String itemType,
                                             Integer status, String keyword, Boolean effectiveOnly) {
        LambdaQueryWrapper<RuleListRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleListRecord::getListId, listId);
        if (hasText(itemType) && !"ANY".equalsIgnoreCase(itemType)) {
            wrapper.eq(RuleListRecord::getItemType, normalizeItemType(itemType));
        }
        if (status != null) {
            wrapper.eq(RuleListRecord::getStatus, status);
        }
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(RuleListRecord::getItemContent, keyword)
                    .or()
                    .like(RuleListRecord::getReason, keyword)
                    .or()
                    .like(RuleListRecord::getRemark, keyword));
        }
        if (Boolean.TRUE.equals(effectiveOnly)) {
            appendEffectiveCondition(wrapper, LocalDateTime.now());
        }
        wrapper.orderByDesc(RuleListRecord::getCreateTime);
        return recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public IPage<RuleListRecordLog> pageLogs(Long listId, int pageNum, int pageSize, Long recordId) {
        LambdaQueryWrapper<RuleListRecordLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleListRecordLog::getListId, listId);
        if (recordId != null) {
            wrapper.eq(RuleListRecordLog::getRecordId, recordId);
        }
        wrapper.orderByDesc(RuleListRecordLog::getCreateTime);
        IPage<RuleListRecordLog> page = logMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillLogChangeContent(page.getRecords());
        return page;
    }

    @Transactional
    public RuleListLibrary saveLibrary(RuleListLibrary library) {
        normalizeLibrary(library);
        save(library);
        return library;
    }

    @Transactional
    public void updateLibrary(RuleListLibrary library) {
        normalizeLibrary(library);
        updateById(library);
    }

    @Transactional
    public RuleListRecord saveRecord(Long listId, RuleListRecord record) {
        record.setListId(listId);
        return upsertRecord(record, normalizeOperation(record.getLastOperation()), null);
    }

    @Transactional
    public RuleListRecord updateRecord(Long listId, RuleListRecord record) {
        if (record == null || record.getId() == null) {
            throw new IllegalArgumentException("名单记录ID不能为空");
        }
        RuleListRecord existing = recordMapper.selectById(record.getId());
        if (existing == null || !listId.equals(existing.getListId())) {
            throw new IllegalArgumentException("名单记录不存在");
        }
        record.setListId(listId);
        normalizeRecord(record);
        RuleListRecord duplicate = recordMapper.selectOne(new LambdaQueryWrapper<RuleListRecord>()
                .eq(RuleListRecord::getListId, record.getListId())
                .eq(RuleListRecord::getItemType, record.getItemType())
                .eq(RuleListRecord::getItemContent, record.getItemContent())
                .ne(RuleListRecord::getId, record.getId())
                .last("LIMIT 1"));
        if (duplicate != null) {
            throw new IllegalArgumentException("名单内容已存在");
        }
        existing.setItemType(record.getItemType());
        existing.setItemContent(record.getItemContent());
        existing.setEffectiveTime(record.getEffectiveTime());
        existing.setExpireTime(record.getExpireTime());
        existing.setReason(record.getReason());
        existing.setRemark(record.getRemark());
        existing.setStatus(record.getStatus() == null ? existing.getStatus() : record.getStatus());
        existing.setLastOperation("UPDATE");
        recordMapper.updateById(existing);
        writeLog(existing, "UPDATE", null);
        return existing;
    }

    @Transactional
    public void deleteRecord(Long listId, Long recordId) {
        RuleListRecord existing = recordMapper.selectById(recordId);
        if (existing == null || !listId.equals(existing.getListId())) {
            throw new IllegalArgumentException("名单记录不存在");
        }
        existing.setStatus(0);
        existing.setLastOperation("DELETE");
        recordMapper.updateById(existing);
        writeLog(existing, "DELETE", null);
    }

    public boolean hit(Long listId, Object content, List<String> itemTypes) {
        return match(listId, content, itemTypes, MATCH_IN_LIST);
    }

    public boolean match(Long listId, Object content, List<String> itemTypes, String matchMode) {
        return matchAt(listId, content, itemTypes, matchMode, LocalDateTime.now());
    }

    public boolean matchAt(Long listId, Object content, List<String> itemTypes, String matchMode, LocalDateTime matchTime) {
        String itemContent = content == null ? null : String.valueOf(content).trim();
        if (!hasText(itemContent) || listId == null) {
            return false;
        }
        RuleListLibrary library = getById(listId);
        if (library == null || library.getStatus() == null || library.getStatus() != 1) {
            return false;
        }
        String normalizedMatchMode = normalizeMatchMode(matchMode);
        LambdaQueryWrapper<RuleListRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleListRecord::getListId, listId)
                .eq(RuleListRecord::getStatus, 1);
        if (MATCH_CONTAINED_IN_LIST.equals(normalizedMatchMode) || MATCH_NOT_CONTAINED_IN_LIST.equals(normalizedMatchMode)) {
            wrapper.like(RuleListRecord::getItemContent, itemContent);
        } else {
            wrapper.eq(RuleListRecord::getItemContent, itemContent);
        }
        List<String> normalizedTypes = normalizeItemTypes(itemTypes);
        if (!normalizedTypes.isEmpty()) {
            wrapper.in(RuleListRecord::getItemType, normalizedTypes);
        }
        appendEffectiveCondition(wrapper, matchTime == null ? LocalDateTime.now() : matchTime);
        boolean matched = recordMapper.selectCount(wrapper) > 0;
        return MATCH_NOT_IN_LIST.equals(normalizedMatchMode) || MATCH_NOT_CONTAINED_IN_LIST.equals(normalizedMatchMode)
                ? !matched
                : matched;
    }

    public Workbook createTemplateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("名单内容");
        writeHeader(workbook, sheet);
        addItemTypeValidation(sheet);
        Row sample = sheet.createRow(1);
        sample.createCell(0).setCellValue("13800138000");
        sample.createCell(1).setCellValue("手机号");
        sample.createCell(2).setCellValue("2026-01-01 00:00:00");
        sample.createCell(3).setCellValue("2026-12-31 23:59:59");
        sample.createCell(4).setCellValue("测试样例");
        sample.createCell(5).setCellValue("导入时可删除样例行");
        sample.createCell(6).setCellValue("ADD");
        sample.createCell(7).setCellValue("");
        autosize(sheet);
        return workbook;
    }

    public Workbook exportRecords(Long listId) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("名单内容");
        writeHeader(workbook, sheet);
        addItemTypeValidation(sheet);
        List<RuleListRecord> records = recordMapper.selectList(new LambdaQueryWrapper<RuleListRecord>()
                .eq(RuleListRecord::getListId, listId)
                .orderByDesc(RuleListRecord::getCreateTime));
        int rowIndex = 1;
        CellStyle dateStyle = dateCellStyle(workbook);
        for (RuleListRecord record : records) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(nullToEmpty(record.getItemContent()));
            row.createCell(1).setCellValue(itemTypeLabel(record.getItemType()));
            writeDateCell(row.createCell(2), record.getEffectiveTime(), dateStyle);
            writeDateCell(row.createCell(3), record.getExpireTime(), dateStyle);
            row.createCell(4).setCellValue(nullToEmpty(record.getReason()));
            row.createCell(5).setCellValue(nullToEmpty(record.getRemark()));
            row.createCell(6).setCellValue(nullToEmpty(record.getLastOperation()));
            writeDateCell(row.createCell(7), record.getCreateTime(), dateStyle);
        }
        autosize(sheet);
        return workbook;
    }

    @Transactional
    public Map<String, Object> importRecords(Long listId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                try {
                    RuleListRecord record = new RuleListRecord();
                    record.setListId(listId);
                    record.setItemContent(cellText(row, 0, formatter));
                    record.setItemType(cellText(row, 1, formatter));
                    record.setEffectiveTime(cellDateTime(row.getCell(2), formatter));
                    record.setExpireTime(cellDateTime(row.getCell(3), formatter));
                    record.setReason(cellText(row, 4, formatter));
                    record.setRemark(cellText(row, 5, formatter));
                    String operation = cellText(row, 6, formatter);
                    upsertRecord(record, normalizeOperation(operation), null);
                    success++;
                } catch (Exception e) {
                    errors.add("第 " + (i + 1) + " 行：" + e.getMessage());
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", success);
        result.put("errorCount", errors.size());
        result.put("errors", errors);
        return result;
    }

    private RuleListRecord upsertRecord(RuleListRecord record, String operation, String operator) {
        normalizeRecord(record);
        if (!"ADD".equals(operation) && !"UPDATE".equals(operation) && !"DELETE".equals(operation)) {
            operation = "ADD";
        }
        RuleListRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<RuleListRecord>()
                .eq(RuleListRecord::getListId, record.getListId())
                .eq(RuleListRecord::getItemType, record.getItemType())
                .eq(RuleListRecord::getItemContent, record.getItemContent()));
        RuleListRecord target = existing != null ? existing : record;
        if (existing != null) {
            target.setEffectiveTime(record.getEffectiveTime());
            target.setExpireTime(record.getExpireTime());
            target.setReason(record.getReason());
            target.setRemark(record.getRemark());
        }
        target.setLastOperation(operation);
        target.setStatus("DELETE".equals(operation) ? 0 : 1);
        if (existing == null) {
            target.setId(null);
            recordMapper.insert(target);
        } else {
            recordMapper.updateById(target);
        }
        writeLog(target, operation, operator);
        return target;
    }

    private void writeLog(RuleListRecord record, String operation, String operator) {
        RuleListRecordLog log = new RuleListRecordLog();
        log.setListId(record.getListId());
        log.setRecordId(record.getId());
        log.setItemType(record.getItemType());
        log.setItemContent(record.getItemContent());
        log.setEffectiveTime(record.getEffectiveTime());
        log.setExpireTime(record.getExpireTime());
        log.setReason(record.getReason());
        log.setRemark(record.getRemark());
        log.setOperation(operation);
        log.setOperator(operator);
        logMapper.insert(log);
    }

    private void fillLogChangeContent(List<RuleListRecordLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (RuleListRecordLog log : logs) {
            RuleListRecordLog previous = findPreviousLog(log);
            log.setChangeContent(buildLogChangeContent(log, previous));
        }
    }

    private RuleListRecordLog findPreviousLog(RuleListRecordLog log) {
        if (log == null || log.getId() == null || log.getRecordId() == null) {
            return null;
        }
        return logMapper.selectOne(new LambdaQueryWrapper<RuleListRecordLog>()
                .eq(RuleListRecordLog::getListId, log.getListId())
                .eq(RuleListRecordLog::getRecordId, log.getRecordId())
                .lt(RuleListRecordLog::getId, log.getId())
                .orderByDesc(RuleListRecordLog::getId)
                .last("LIMIT 1"));
    }

    private String buildLogChangeContent(RuleListRecordLog current, RuleListRecordLog previous) {
        if (current == null) {
            return "";
        }
        String operation = current.getOperation();
        if ("ADD".equals(operation)) {
            return "新增：" + snapshotText(current);
        }
        if ("DELETE".equals(operation)) {
            return "删除：" + snapshotText(current);
        }
        if (previous == null) {
            return snapshotText(current);
        }
        List<String> changes = new ArrayList<>();
        appendChange(changes, "内容类型", itemTypeLabel(previous.getItemType()), itemTypeLabel(current.getItemType()));
        appendChange(changes, "名单内容", previous.getItemContent(), current.getItemContent());
        appendChange(changes, "有效期", effectivePeriod(previous), effectivePeriod(current));
        appendChange(changes, "原因", previous.getReason(), current.getReason());
        appendChange(changes, "备注", previous.getRemark(), current.getRemark());
        return changes.isEmpty() ? "无字段变化" : "修改：" + String.join("；", changes);
    }

    private String snapshotText(RuleListRecordLog log) {
        return "内容类型=" + itemTypeLabel(log.getItemType())
                + "；名单内容=" + nullToDisplay(log.getItemContent())
                + "；有效期=" + effectivePeriod(log)
                + "；原因=" + nullToDisplay(log.getReason())
                + "；备注=" + nullToDisplay(log.getRemark());
    }

    private void appendChange(List<String> changes, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + "：" + nullToDisplay(before) + " -> " + nullToDisplay(after));
        }
    }

    private String effectivePeriod(RuleListRecordLog log) {
        if (log == null) {
            return "立即 至 长期";
        }
        return formatEffectiveStart(log.getEffectiveTime()) + " 至 " + formatEffectiveEnd(log.getExpireTime());
    }

    private String formatEffectiveStart(LocalDateTime value) {
        return value == null ? "立即" : DATE_TIME_FORMAT.format(value);
    }

    private String formatEffectiveEnd(LocalDateTime value) {
        return value == null ? "长期" : DATE_TIME_FORMAT.format(value);
    }

    private String nullToDisplay(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty() ? "空" : String.valueOf(value);
    }

    private void fillLibraryExtras(List<RuleListLibrary> libraries) {
        if (libraries == null || libraries.isEmpty()) {
            return;
        }
        List<Long> projectIds = libraries.stream()
                .filter(v -> v.getProjectId() != null && v.getProjectId() > 0)
                .map(RuleListLibrary::getProjectId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> projectNames = new HashMap<>();
        if (!projectIds.isEmpty()) {
            projectNames = projectMapper.selectBatchIds(projectIds).stream()
                    .collect(Collectors.toMap(RuleProject::getId, RuleProject::getProjectName, (a, b) -> a));
        }
        for (RuleListLibrary library : libraries) {
            if (library.getProjectId() != null && library.getProjectId() > 0) {
                library.setProjectName(projectNames.get(library.getProjectId()));
            }
            Long count = recordMapper.selectCount(new LambdaQueryWrapper<RuleListRecord>()
                    .eq(RuleListRecord::getListId, library.getId())
                    .eq(RuleListRecord::getStatus, 1));
            library.setRecordCount(count);
        }
    }

    private void appendEffectiveCondition(LambdaQueryWrapper<RuleListRecord> wrapper, LocalDateTime now) {
        wrapper.and(w -> w.isNull(RuleListRecord::getEffectiveTime).or().le(RuleListRecord::getEffectiveTime, now));
        wrapper.and(w -> w.isNull(RuleListRecord::getExpireTime).or().ge(RuleListRecord::getExpireTime, now));
    }

    private void normalizeLibrary(RuleListLibrary library) {
        if (library == null) {
            throw new IllegalArgumentException("名单库不能为空");
        }
        library.setListCode(required(library.getListCode(), "名单库编码不能为空"));
        library.setListName(required(library.getListName(), "名单库名称不能为空"));
        library.setListType(normalizeListType(library.getListType()));
        if (!hasText(library.getScope())) {
            library.setScope(SCOPE_PROJECT);
        }
        if (SCOPE_GLOBAL.equals(library.getScope())) {
            library.setProjectId(0L);
        }
        if (library.getProjectId() == null) {
            library.setProjectId(0L);
        }
        if (library.getStatus() == null) {
            library.setStatus(1);
        }
    }

    private void normalizeRecord(RuleListRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("名单记录不能为空");
        }
        if (record.getListId() == null) {
            throw new IllegalArgumentException("名单库ID不能为空");
        }
        record.setItemContent(required(record.getItemContent(), "名单内容不能为空"));
        record.setItemType(normalizeItemType(record.getItemType()));
    }

    private List<String> normalizeItemTypes(List<String> itemTypes) {
        List<String> result = new ArrayList<>();
        if (itemTypes == null) {
            return result;
        }
        for (String itemType : itemTypes) {
            String normalized = normalizeItemTypeOrNull(itemType);
            if (normalized != null && !"ANY".equals(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeListType(String type) {
        String value = trimToNull(type);
        return value == null ? "BLACK" : value.toUpperCase();
    }

    private String normalizeItemType(String type) {
        String normalized = normalizeItemTypeOrNull(type);
        if (normalized == null || "ANY".equals(normalized)) {
            throw new IllegalArgumentException("内容类型不能为空");
        }
        return normalized;
    }

    private String normalizeItemTypeOrNull(String type) {
        String value = trimToNull(type);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (ITEM_TYPE_LABELS.containsKey(upper)) {
            return upper;
        }
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
        aliases.put("任意", "ANY");
        String alias = aliases.get(value);
        return alias != null ? alias : upper;
    }

    private String normalizeMatchMode(String matchMode) {
        String value = trimToNull(matchMode);
        if (value == null) {
            return MATCH_IN_LIST;
        }
        String upper = value.toUpperCase();
        if ("HIT".equals(upper) || "EXACT".equals(upper) || "IN".equals(upper)) {
            return MATCH_IN_LIST;
        }
        if ("MISS".equals(upper) || "NOT_IN".equals(upper)) {
            return MATCH_NOT_IN_LIST;
        }
        if ("CONTAINS".equals(upper) || "CONTAINED".equals(upper)) {
            return MATCH_CONTAINED_IN_LIST;
        }
        if ("NOT_CONTAINS".equals(upper) || "NOT_CONTAINED".equals(upper)) {
            return MATCH_NOT_CONTAINED_IN_LIST;
        }
        if (MATCH_NOT_IN_LIST.equals(upper) || MATCH_CONTAINED_IN_LIST.equals(upper) || MATCH_NOT_CONTAINED_IN_LIST.equals(upper)) {
            return upper;
        }
        return MATCH_IN_LIST;
    }

    private String itemTypeLabel(String itemType) {
        String normalized = normalizeItemTypeOrNull(itemType);
        return normalized == null ? "" : ITEM_TYPE_LABELS.getOrDefault(normalized, normalized);
    }

    private String normalizeOperation(String operation) {
        String value = trimToNull(operation);
        if (value == null) {
            return "ADD";
        }
        Map<String, String> aliases = new HashMap<>();
        aliases.put("新增", "ADD");
        aliases.put("添加", "ADD");
        aliases.put("修改", "UPDATE");
        aliases.put("更新", "UPDATE");
        aliases.put("删除", "DELETE");
        aliases.put("停用", "DELETE");
        String alias = aliases.get(value);
        return alias != null ? alias : value.toUpperCase();
    }

    private void writeHeader(Workbook workbook, Sheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < EXCEL_HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(EXCEL_HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void addItemTypeValidation(Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        String[] labels = ITEM_TYPE_LABELS.values().toArray(new String[0]);
        DataValidationConstraint constraint = helper.createExplicitListConstraint(labels);
        CellRangeAddressList range = new CellRangeAddressList(1, 1000, 1, 1);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("内容类型错误", "请从下拉列表中选择内容类型");
        sheet.addValidationData(validation);
    }

    private CellStyle dateCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper helper = workbook.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private void writeDateCell(Cell cell, LocalDateTime value, CellStyle style) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        cell.setCellValue(java.util.Date.from(value.atZone(ZoneId.systemDefault()).toInstant()));
        cell.setCellStyle(style);
    }

    private LocalDateTime cellDateTime(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return LocalDateTime.ofInstant(cell.getDateCellValue().toInstant(), ZoneId.systemDefault());
        }
        String text = trimToNull(formatter.formatCellValue(cell));
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMAT);
        } catch (Exception ignored) {
            return LocalDate.parse(text, DATE_FORMAT).atTime(LocalTime.MIN);
        }
    }

    private String cellText(Row row, int index, DataFormatter formatter) {
        return trimToNull(formatter.formatCellValue(row.getCell(index)));
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (int i = 0; i < 7; i++) {
            if (hasText(formatter.formatCellValue(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private void autosize(Sheet sheet) {
        for (int i = 0; i < EXCEL_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1024, 10000));
        }
    }

    private String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
