package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 使用现有项目凭证鉴权，只同步公开结果码配置，不同步邮件或账户配置。 */
@RestController
public class SystemCodeMappingSyncController {
    private final SystemResponseMapping mapping;
    public SystemCodeMappingSyncController(SystemResponseMapping mapping) { this.mapping = mapping; }
    @GetMapping("/api/rule/sync/system-code-mapping")
    public ApiResponse<ExternalCodeMapping> get() { return ApiResponse.ok(mapping.current()); }
}
