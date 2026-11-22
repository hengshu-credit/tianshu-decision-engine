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
    private final SavedTestDirectory directory;
    private final TestInputDefaults defaults;
    private final MockFieldBindings bindings;
    private final TestMockService generator;

    @GetMapping("/rules")
    @RequirePermission(value="rule:view",anyOf="field:view")
    public ApiResponse<List<MockRule>> rules() { return ApiResponse.ok(catalog.rules()); }

    @GetMapping("/rules/{id}/fields")
    @RequirePermission("field:view")
    public ApiResponse<List<Map<String,Object>>> fields(@PathVariable long id) { return ApiResponse.ok(bindings.list(id)); }

    @PutMapping("/rules/{id}/fields/{type}/{ref}")
    @RequirePermission("field:edit")
    public ApiResponse<Void> bind(@PathVariable long id,@PathVariable MockFieldBindings.Target type,@PathVariable long ref) {
        bindings.bind(id,type,ref,false); return ApiResponse.ok();
    }
    @DeleteMapping("/rules/{id}/fields/{type}/{ref}")
    @RequirePermission("field:edit")
    public ApiResponse<Void> unbind(@PathVariable long id,@PathVariable MockFieldBindings.Target type,@PathVariable long ref) {
        bindings.bind(id,type,ref,true); return ApiResponse.ok();
    }
    @PostMapping("/rules/preview")
    @RequirePermission(value="rule:view",anyOf="field:view")
    public ApiResponse<Map<String,String>> preview(@RequestBody MockRule rule) {
        rule.validate(); return ApiResponse.ok(Map.of("valueJson",rule.sample().toString()));
    }

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
    @GetMapping("/samples/{type}/{owner}")
    @RequirePermission("rule:view")
    public ApiResponse<Map<String,String>> sample(@PathVariable TestResource type,@PathVariable long owner,
            @RequestParam String source,@RequestParam String id) {
        return ApiResponse.ok(Map.of("paramsJson",directory.sample(type,source,owner,id)));
    }
    @PutMapping("/defaults/{type}/{owner}")
    @RequirePermission(value="rule:edit",anyOf="field:edit")
    public ApiResponse<Void> saveDefault(@PathVariable TestResource type,@PathVariable long owner,@RequestBody Map<String,String> body) {
        defaults.save(type,owner,body.get("paramsJson")); return ApiResponse.ok();
    }

    @GetMapping("/example-cases")
    @RequirePermission("field:view")
    public ApiResponse<Map<String,Object>> exampleCases(@RequestParam(defaultValue="0") Long projectId,
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page) {
        return ApiResponse.ok(catalog.exampleCases(projectId,keyword,page));
    }

    @GetMapping("/example-cases/{type}/{id}")
    @RequirePermission("field:view")
    public ApiResponse<Map<String,Object>> exampleCase(@PathVariable TestResource type,@PathVariable long id,
            @RequestParam(defaultValue="0") Long projectId) {
        return ApiResponse.ok(catalog.exampleCase(type,id,projectId));
    }

    @GetMapping("/cases")
    @RequirePermission("rule:view")
    public ApiResponse<Map<String,Object>> cases(@RequestParam(required=false) TestResource type,
            @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,
            @RequestParam(required=false) String scope,@RequestParam(required=false) Long projectId) {
        return ApiResponse.ok(directory.list(type,keyword,page,scope,projectId));
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
    @RequirePermission(value="field:edit",anyOf={"model:edit","datasource:edit"})
    public ApiResponse<Void> update(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id,@RequestBody TestDataCatalog.ResourceCase row) {
        catalog.saveCase(type,owner,id,row); return ApiResponse.ok();
    }
    @PutMapping("/{type}/{owner}/cases/{id}/input")
    @RequirePermission(value="field:edit",anyOf={"model:edit","datasource:edit"})
    public ApiResponse<Void> updateCaseInput(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id,
            @RequestBody Map<String,String> input) {
        catalog.saveCaseInput(type,owner,id,input.get("paramsJson"),input.get("originalJson")); return ApiResponse.ok();
    }

    @DeleteMapping("/{type}/{owner}/cases/{id}")
    @RequirePermission("rule:view")
    public ApiResponse<Void> remove(@PathVariable TestResource type,@PathVariable long owner,@PathVariable long id) {
        catalog.removeCase(type,owner,id); return ApiResponse.ok();
    }
}
