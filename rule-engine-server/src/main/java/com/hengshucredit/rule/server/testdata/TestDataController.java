package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.server.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/test-data")
@RequiredArgsConstructor
public class TestDataController {
    private final TestDataCatalog catalog;
    private final TestMockService generator;

    @GetMapping("/rules")
    @RequirePermission(value="rule:view",anyOf="field:view")
    public ApiResponse<List<MockRule>> rules() { return ApiResponse.ok(catalog.rules()); }

    @PostMapping("/rules")
    @RequirePermission("field:edit")
    public ApiResponse<Void> createRule(@RequestBody MockRule rule) {
        catalog.saveRule(null,rule); return ApiResponse.ok();
    }
    @PutMapping("/rules/{id}")
    @RequirePermission("field:edit")
    public ApiResponse<Void> updateRule(@PathVariable long id,@RequestBody MockRule rule) {
        catalog.saveRule(id,rule); return ApiResponse.ok();
    }
    @DeleteMapping("/rules/{id}")
    @RequirePermission("field:edit")
    public ApiResponse<Void> deleteRule(@PathVariable long id) {
        catalog.removeRule(id); return ApiResponse.ok();
    }
    @PostMapping("/generate/{id}")
    @RequirePermission("rule:view")
    public ApiResponse<TestMockService.Sample> generate(@PathVariable long id,@RequestBody RuleTestSchemaRequest request) {
        return ApiResponse.ok(generator.generate(id,request));
    }
    @GetMapping("/{type}/catalog")
    @RequirePermission("rule:view")
    public ApiResponse<Map<String,Object>> page(@PathVariable TestResource type,@RequestParam(defaultValue="true") boolean cases,
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page) {
        return ApiResponse.ok(catalog.page(type,cases,keyword,page));
    }
    @GetMapping("/{type}/{owner}/cases/{id}")
    @RequirePermission("rule:view")
    public ApiResponse<TestDataCatalog.ResourceCase> detail(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id) {
        return ApiResponse.ok(catalog.detail(type,owner,id));
    }
    @PostMapping("/{type}/{owner}/cases")
    @RequirePermission("rule:view")
    public ApiResponse<Void> create(@PathVariable TestResource type,@PathVariable long owner,@RequestBody TestDataCatalog.ResourceCase row) {
        catalog.saveCase(type,owner,null,row); return ApiResponse.ok();
    }
    @PutMapping("/{type}/{owner}/cases/{id}")
    @RequirePermission("rule:view")
    public ApiResponse<Void> update(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id,@RequestBody TestDataCatalog.ResourceCase row) {
        catalog.saveCase(type,owner,id,row); return ApiResponse.ok();
    }
    @DeleteMapping("/{type}/{owner}/cases/{id}")
    @RequirePermission("rule:view")
    public ApiResponse<Void> remove(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id) {
        catalog.removeCase(type,owner,id); return ApiResponse.ok();
    }
}
