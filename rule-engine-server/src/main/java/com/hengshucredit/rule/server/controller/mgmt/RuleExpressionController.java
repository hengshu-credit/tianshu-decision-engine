package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExpressionRequest;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.server.service.RuleCompileService;
import com.hengshucredit.rule.server.service.RuleExpressionTestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/api/rule/expression")
public class RuleExpressionController {

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExpressionTestService expressionTestService;

    @PostMapping("/compile")
    public ApiResponse<CompileResult> compile(@RequestBody(required = false) RuleExpressionRequest request) {
        if (request == null || request.getRuleId() == null) {
            return ApiResponse.ok(CompileResult.fail("规则 ID 不能为空"));
        }
        return ApiResponse.ok(compileService.compileExpression(request.getRuleId(), request.getOperand()));
    }

    @PostMapping("/schema")
    public ApiResponse<RuleTestSchema> schema(@RequestBody(required = false) RuleExpressionRequest request) {
        try {
            return ApiResponse.ok(expressionTestService.buildSchema(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/test")
    public ApiResponse<RuleExecuteResult> test(@RequestBody(required = false) RuleExpressionRequest request) {
        return ApiResponse.ok(expressionTestService.execute(request));
    }
}
