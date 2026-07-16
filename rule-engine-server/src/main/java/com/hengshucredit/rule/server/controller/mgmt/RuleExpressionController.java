package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.core.compiler.CompileResult;
import com.hengshucredit.rule.model.dto.RuleExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExpressionRequest;
import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleCompileService;
import com.hengshucredit.rule.server.service.RuleExpressionTestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/rule/expression")
public class RuleExpressionController {

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExpressionTestService expressionTestService;

    @PostMapping("/compile")
    public R<CompileResult> compile(@RequestBody(required = false) RuleExpressionRequest request) {
        if (request == null || request.getRuleId() == null) {
            return R.ok(CompileResult.fail("规则 ID 不能为空"));
        }
        return R.ok(compileService.compileExpression(request.getRuleId(), request.getOperand()));
    }

    @PostMapping("/schema")
    public R<RuleTestSchema> schema(@RequestBody(required = false) RuleExpressionRequest request) {
        try {
            return R.ok(expressionTestService.buildSchema(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/test")
    public R<RuleExecuteResult> test(@RequestBody(required = false) RuleExpressionRequest request) {
        return R.ok(expressionTestService.execute(request));
    }
}
