package com.hengshucredit.rule.server.controller.mgmt;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import com.hengshucredit.rule.server.service.RuleSftpDatasourceService;
import com.hengshucredit.rule.server.service.RuntimeTraceService;
import com.hengshucredit.rule.server.service.SftpConnectPools;
import com.hengshucredit.rule.server.service.SftpFileCache;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/sftp")
public class SftpDatasourceController {
    @Resource private RuleSftpDatasourceService datasourceService;
    @Resource private SftpConnectPools sftpConnectPools;
    @Resource private SftpFileCache sftpFileCache;
    @Resource private RuleRuntimeCallLogService runtimeCallLogService;
    @Resource private RuntimeTraceService runtimeTraceService;

    @GetMapping("/list")
    public R<IPage<RuleSftpDatasource>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String datasourceCode,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) Integer status) {
        return R.ok(datasourceService.pageList(pageNum, pageSize, scope, projectId, projectCode, projectName,
                datasourceCode, datasourceName, status));
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleSftpDatasource> get(@PathVariable Long id) { return R.ok(datasourceService.getDetail(id)); }

    @PostMapping
    @GovernedProjectionMutation
    public R<Void> create(@RequestBody RuleSftpDatasource datasource) { datasourceService.saveWithDefaults(datasource); return R.ok(); }

    @PutMapping
    @GovernedProjectionMutation
    public R<Void> update(@RequestBody RuleSftpDatasource datasource) { datasourceService.updateWithDefaults(datasource); return R.ok(); }

    @DeleteMapping("/{id:\\d+}")
    @GovernedProjectionMutation
    public R<Void> delete(@PathVariable Long id) { datasourceService.deleteDatasource(id); return R.ok(); }

    @PostMapping("/{id:\\d+}/test")
    public R<String> testSaved(@PathVariable Long id) {
        RuleSftpDatasource datasource = datasourceService.getById(id);
        return test(datasource, "TEST_CONNECTION");
    }

    @PostMapping("/test")
    public R<String> testDraft(@RequestBody RuleSftpDatasource datasource) { return test(datasource, "TEST_CONNECTION_DRAFT"); }

    @PostMapping("/{id:\\d+}/read")
    public R<Map<String, Object>> read(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        RuleSftpDatasource datasource = datasourceService.getById(id);
        String path = body == null || body.get("remotePath") == null ? null : String.valueOf(body.get("remotePath"));
        return readFile(datasource, path, "QUERY");
    }

    /** 按查询日志重新读取文件，页面预览使用；凭据不会进入响应。 */
    @GetMapping("/logs/{logId:\\d+}/content")
    public ResponseEntity<byte[]> preview(@PathVariable Long logId, HttpServletRequest request) {
        RuleRuntimeCallLog log = runtimeCallLogService.getById(logId);
        if (log == null || !"SFTP".equals(log.getModuleType())) return ResponseEntity.notFound().build();
        Map<String, Object> payload = parseMap(log.getRequestBody());
        Object remotePath = payload.get("remotePath");
        if (log.getTargetRefId() == null || remotePath == null) return ResponseEntity.badRequest().build();
        try {
            byte[] content = sftpConnectPools.readFile(log.getTargetRefId(), String.valueOf(remotePath));
            String contentType = contentType(String.valueOf(remotePath));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(content.length);
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(fileName(String.valueOf(remotePath)), StandardCharsets.UTF_8).build());
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (Exception e) {
            return ResponseEntity.status(502).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(("SFTP文件读取失败：" + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/pool/snapshot")
    public R<Map<String, Object>> poolSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>(sftpConnectPools.snapshot());
        snapshot.put("fileCache", sftpFileCache.snapshot());
        return R.ok(snapshot);
    }

    private R<String> test(RuleSftpDatasource datasource, String action) {
        long start = System.currentTimeMillis();
        RuntimeTraceService.ModuleTrace trace = runtimeTraceService.startModule("SFTP",
                datasource == null ? null : datasource.getProjectId(), datasource == null ? null : datasource.getId(),
                datasource == null ? null : datasource.getDatasourceCode());
        try {
            if (datasource == null) throw new IllegalArgumentException("SFTP数据源不存在");
            datasourceService.testConnection(datasource);
            runtimeTraceService.completeModule(trace, true, null, System.currentTimeMillis() - start);
            log(datasource, action, Map.of("operation", "pwd"), Map.of("status", "SUCCESS"), null, start, trace);
            return R.ok("SFTP连接成功");
        } catch (Exception e) {
            runtimeTraceService.completeModule(trace, false, e.getMessage(), System.currentTimeMillis() - start);
            log(datasource, action, safeConfig(datasource), Map.of("status", "FAILED"), e.getMessage(), start, trace);
            return R.fail("SFTP连接失败：" + e.getMessage());
        }
    }

    private R<Map<String, Object>> readFile(RuleSftpDatasource datasource, String path, String action) {
        long start = System.currentTimeMillis();
        RuntimeTraceService.ModuleTrace trace = runtimeTraceService.startModule("SFTP",
                datasource == null ? null : datasource.getProjectId(), datasource == null ? null : datasource.getId(),
                datasource == null ? null : datasource.getDatasourceCode());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("remotePath", path);
        try {
            if (datasource == null) throw new IllegalArgumentException("SFTP数据源不存在");
            byte[] content = sftpConnectPools.readFile(datasource.getId(), path);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("remotePath", path);
            result.put("size", content.length);
            result.put("contentType", contentType(path));
            runtimeTraceService.completeModule(trace, true, null, System.currentTimeMillis() - start);
            log(datasource, action, request, result, null, start, trace);
            return R.ok(result);
        } catch (Exception e) {
            runtimeTraceService.completeModule(trace, false, e.getMessage(), System.currentTimeMillis() - start);
            log(datasource, action, request, Map.of("status", "FAILED"), e.getMessage(), start, trace);
            return R.fail("SFTP文件读取失败：" + e.getMessage());
        }
    }

    private void log(RuleSftpDatasource datasource, String action, Object request, Object response,
                     String error, long start, RuntimeTraceService.ModuleTrace trace) {
        RuleRuntimeCallLog log = new RuleRuntimeCallLog();
        if (trace != null) {
            log.setTraceId(trace.getTraceId());
            log.setRuleTraceId(trace.getRuleTraceId());
        }
        log.setModuleType("SFTP");
        log.setActionType(action);
        if (datasource != null) {
            log.setProjectId(datasource.getProjectId());
            log.setTargetRefId(datasource.getId());
            log.setTargetCode(datasource.getDatasourceCode());
            log.setTargetName(datasource.getDatasourceName());
            log.setRequestUrl(sftpUrl(datasource, request));
        }
        log.setRequestMethod("SFTP");
        log.setRequestBody(runtimeCallLogService.toJson(request));
        log.setResponseBody(runtimeCallLogService.toJson(response));
        log.setResponseStatus(error == null ? 200 : 502);
        log.setSuccess(error == null ? 1 : 0);
        log.setErrorMessage(error);
        log.setCostTimeMs(System.currentTimeMillis() - start);
        runtimeCallLogService.safeSave(log);
    }

    private Map<String, Object> safeConfig(RuleSftpDatasource datasource) {
        if (datasource == null) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", datasource.getHost());
        result.put("port", datasource.getPort());
        result.put("username", datasource.getUsername());
        result.put("datasourceCode", datasource.getDatasourceCode());
        return result;
    }

    private String sftpUrl(RuleSftpDatasource datasource, Object request) {
        Map<String, Object> map = request instanceof Map<?, ?> raw ? castMap(raw) : Collections.emptyMap();
        String path = map.get("remotePath") == null ? "" : String.valueOf(map.get("remotePath"));
        return "sftp://" + datasource.getHost() + ":" + datasource.getPort() + (path.startsWith("/") ? path : "/" + path);
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) return Collections.emptyMap();
        try { return castMap(JSON.parseObject(value)); } catch (RuntimeException ignored) { return Collections.emptyMap(); }
    }

    private Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw != null) raw.forEach((key, value) -> { if (key != null) result.put(String.valueOf(key), value); });
        return result;
    }

    private String fileName(String path) { int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')); return slash >= 0 ? path.substring(slash + 1) : path; }

    private String contentType(String path) {
        String value = path == null ? "" : path.toLowerCase();
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".gif")) return "image/gif";
        if (value.endsWith(".webp")) return "image/webp";
        if (value.endsWith(".pdf")) return "application/pdf";
        if (value.endsWith(".json")) return "application/json";
        if (value.endsWith(".txt") || value.endsWith(".csv") || value.endsWith(".xml")) return "text/plain;charset=UTF-8";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
