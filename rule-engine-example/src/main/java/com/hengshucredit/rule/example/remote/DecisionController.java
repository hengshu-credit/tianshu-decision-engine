package com.hengshucredit.rule.example.remote;

import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 业务端只请求本服务，项目凭据留在服务端配置中。 */
@RestController
@RequestMapping("/api/example")
public class DecisionController {
    private final DecisionService service;

    public DecisionController(DecisionService service) { this.service = service; }

    @GetMapping("/rules")
    public List<String> rules() { return service.allowedRules(); }

    @PostMapping("/execute")
    public ResponseEntity<RuleResult> execute(@RequestBody ExecuteRequest request) {
        if (request == null || request.ruleCode() == null || request.ruleCode().isBlank() || request.params() == null) {
            throw new IllegalArgumentException("必须提供 ruleCode 和对象类型的 params");
        }
        RuleResult result = service.execute(request.ruleCode(), request.params());
        // 成功不等于业务放行；拒绝/人工复核等均由 result 中的业务字段决定。
        return ResponseEntity.status(result.isSuccess() ? 200 : 422).body(result);
    }

    public record ExecuteRequest(String ruleCode, Map<String, Object> params) { }
}
