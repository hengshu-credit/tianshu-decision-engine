package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ResourceVersionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/rule")
public class FieldPickerVersionController {
    @Resource private ResourceVersionService versions;

    @GetMapping("/variable/picker-versions/{id}") @RequirePermission("field:view")
    public ApiResponse<List<Map<String, Object>>> variables(@PathVariable Long id) { return ApiResponse.ok(versions.catalog("VARIABLE", id)); }
    @GetMapping("/dataobject/picker-versions/{id}") @RequirePermission("field:view")
    public ApiResponse<List<Map<String, Object>>> objects(@PathVariable Long id) { return ApiResponse.ok(versions.catalog("DATA_OBJECT", id)); }
    @GetMapping("/model/picker-versions/{id}") @RequirePermission("model:view")
    public ApiResponse<List<Map<String, Object>>> models(@PathVariable Long id) { return ApiResponse.ok(versions.catalog("MODEL", id)); }
    @GetMapping("/function/picker-versions/{id}") @RequirePermission("function:view")
    public ApiResponse<List<Map<String, Object>>> functions(@PathVariable Long id) { return ApiResponse.ok(versions.catalog("FUNCTION", id)); }
    @GetMapping("/datasource/picker-versions/{id}") @RequirePermission("datasource:view")
    public ApiResponse<List<Map<String, Object>>> apis(@PathVariable Long id) { return ApiResponse.ok(versions.catalog("EXTERNAL_API", id)); }

    @GetMapping("/variable/picker-version/{versionId}") @RequirePermission("field:view")
    public ApiResponse<Map<String, Object>> variable(@PathVariable Long versionId, @RequestParam Long refId, @RequestParam String refType, @RequestParam(required = false) String versionSource) {
        if (!Set.of("VARIABLE", "CONSTANT").contains(refType)) throw new IllegalArgumentException("字段类型不匹配");
        return ApiResponse.ok(versions.field(refType, refId, versionId, versionSource));
    }
    @GetMapping("/model/picker-version-fields/{versionId}") @RequirePermission("model:view")
    public ApiResponse<Map<String, Object>> modelFields(@PathVariable Long versionId, @RequestParam Long ownerId, @RequestParam(required = false) String versionSource) { return ApiResponse.ok(versions.fields("MODEL", ownerId, versionId, versionSource)); }
    @GetMapping("/dataobject/picker-version-fields/{versionId}") @RequirePermission("field:view")
    public ApiResponse<Map<String, Object>> objectFields(@PathVariable Long versionId, @RequestParam Long ownerId, @RequestParam(required = false) String versionSource) { return ApiResponse.ok(versions.fields("DATA_OBJECT", ownerId, versionId, versionSource)); }
    @GetMapping("/dataobject/picker-version/{versionId}") @RequirePermission("field:view")
    public ApiResponse<Map<String, Object>> object(@PathVariable Long versionId, @RequestParam Long refId, @RequestParam(required = false) String versionSource) { return ApiResponse.ok(versions.field("DATA_OBJECT", refId, versionId, versionSource)); }
    @GetMapping("/model/picker-version/{versionId}") @RequirePermission("model:view")
    public ApiResponse<Map<String, Object>> model(@PathVariable Long versionId, @RequestParam Long refId, @RequestParam String refType, @RequestParam(required = false) String versionSource) {
        if (!Set.of("MODEL", "MODEL_OUTPUT").contains(refType)) throw new IllegalArgumentException("字段类型不匹配");
        return ApiResponse.ok(versions.field(refType, refId, versionId, versionSource));
    }
    @GetMapping("/function/picker-version/{versionId}") @RequirePermission("function:view")
    public ApiResponse<Map<String, Object>> function(@PathVariable Long versionId, @RequestParam Long refId, @RequestParam(required = false) String versionSource) { return ApiResponse.ok(versions.field("FUNCTION", refId, versionId, versionSource)); }
    @GetMapping("/datasource/picker-version/{versionId}") @RequirePermission("datasource:view")
    public ApiResponse<Map<String, Object>> api(@PathVariable Long versionId, @RequestParam Long refId, @RequestParam(required = false) String versionSource) { return ApiResponse.ok(versions.field("EXTERNAL_API", refId, versionId, versionSource)); }
}
