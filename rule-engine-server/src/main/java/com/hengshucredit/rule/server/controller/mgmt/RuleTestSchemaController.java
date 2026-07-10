package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleTestSchema;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleTestSchemaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/rule")
public class RuleTestSchemaController {

    @Resource
    private RuleTestSchemaService ruleTestSchemaService;

    @PostMapping("/test-schema")
    public R<RuleTestSchema> build(@RequestBody RuleTestSchemaRequest request) {
        return R.ok(ruleTestSchemaService.build(request));
    }
}
