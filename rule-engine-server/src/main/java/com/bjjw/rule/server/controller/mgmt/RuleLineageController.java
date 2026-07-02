package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleLineageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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
                                        @RequestParam(required = false, defaultValue = "ALL") String direction) {
        try {
            return R.ok(lineageService.graph(nodeType, nodeId, direction));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
