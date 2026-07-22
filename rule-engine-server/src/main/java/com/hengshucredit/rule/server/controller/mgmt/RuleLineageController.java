package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleLineageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/lineage")
public class RuleLineageController {

    @Resource
    private RuleLineageService lineageService;

    @GetMapping("/options")
    public R<List<Map<String, Object>>> options(@RequestParam String nodeType,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long projectId) {
        return R.ok(lineageService.options(nodeType, keyword, projectId));
    }

    @GetMapping("/graph")
    public R<Map<String, Object>> graph(@RequestParam String nodeType,
                                        @RequestParam Long nodeId,
                                        @RequestParam(required = false, defaultValue = "ALL") String direction,
                                        @RequestParam(required = false) Integer maxDepth) {
        try {
            return R.ok(lineageService.graph(nodeType, nodeId, direction, maxDepth));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
