package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.dto.LedgerRequests;
import com.hengshucredit.rule.server.ledger.LedgerArticleService;
import com.hengshucredit.rule.server.ledger.LedgerAssetService;
import com.hengshucredit.rule.server.ledger.LedgerQueryService;
import com.hengshucredit.rule.server.ledger.LedgerTypeService;
import com.hengshucredit.rule.server.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rule/ledger")
@RequirePermission("ledger:view")
@RequiredArgsConstructor
public class LedgerController {
    private final LedgerArticleService articles;
    private final LedgerAssetService assets;
    private final LedgerQueryService queries;
    private final LedgerTypeService types;

    @GetMapping("/types")
    public ApiResponse<?> types() { return ApiResponse.ok(types.list()); }
    @PostMapping("/types") @RequirePermission("ledger:manage")
    public ApiResponse<?> createType(@RequestBody LedgerRequests.EventType request) { return ApiResponse.ok(types.save(null,request)); }
    @PutMapping("/types/{id}") @RequirePermission("ledger:manage")
    public ApiResponse<?> updateType(@PathVariable long id,@RequestBody LedgerRequests.EventType request) { return ApiResponse.ok(types.save(id,request)); }
    @DeleteMapping("/types/{id}") @RequirePermission("ledger:manage")
    public ApiResponse<?> deleteType(@PathVariable long id,@RequestParam Long version) { types.delete(id,version); return ApiResponse.ok(); }
    @GetMapping("/events/manage")
    public ApiResponse<?> manageEvents(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long typeId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(queries.manageEvents(keyword,typeId,pageNum,pageSize)); }

    @GetMapping("/timeline")
    public ApiResponse<?> timeline(@RequestParam(required=false) String scope, @RequestParam(required=false) Long projectId,
            @RequestParam(required=false) List<Long> eventIds, @RequestParam(required=false) String category, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(queries.timeline(new LedgerQueryService.Filter(scope,projectId,eventIds,category,keyword,start,end),pageNum,pageSize));
    }
    @GetMapping("/calendar")
    public ApiResponse<?> calendar(@RequestParam String month, @RequestParam(required=false) String scope, @RequestParam(required=false) Long projectId,
            @RequestParam(required=false) List<Long> eventIds, @RequestParam(required=false) String category, @RequestParam(required=false) String keyword) {
        return ApiResponse.ok(queries.calendar(month,new LedgerQueryService.Filter(scope,projectId,eventIds,category,keyword,null,null)));
    }
    @GetMapping("/articles")
    public ApiResponse<?> articles(@RequestParam(required=false) String status, @RequestParam(required=false) String scope, @RequestParam(required=false) Long projectId,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(articles.list(status,scope,projectId,keyword,pageNum,pageSize));
    }
    @GetMapping("/articles/{id}/edit") @RequirePermission("ledger:edit")
    public ApiResponse<?> edit(@PathVariable long id) { return ApiResponse.ok(articles.edit(id)); }
    @PostMapping("/articles") @RequirePermission("ledger:edit")
    public ApiResponse<?> create(@RequestBody LedgerRequests.SaveDraft request) { return ApiResponse.ok(articles.save(null,request)); }
    @PutMapping("/articles/{id}/draft") @RequirePermission("ledger:edit")
    public ApiResponse<?> save(@PathVariable long id,@RequestBody LedgerRequests.SaveDraft request) { return ApiResponse.ok(articles.save(id,request)); }
    @PostMapping("/articles/{id}/publish") @RequirePermission("ledger:edit")
    public ApiResponse<?> publish(@PathVariable long id,@RequestBody LedgerRequests.Publish request) { return ApiResponse.ok(articles.publish(id,request.version())); }
    @DeleteMapping("/articles/{id}/draft") @RequirePermission("ledger:edit")
    public ApiResponse<?> discard(@PathVariable long id,@RequestParam Long version) { articles.discard(id,version); return ApiResponse.ok(); }
    @PostMapping("/articles/{id}/restore/{revisionId}") @RequirePermission("ledger:edit")
    public ApiResponse<?> restore(@PathVariable long id,@PathVariable long revisionId,@RequestBody LedgerRequests.Publish request) { return ApiResponse.ok(articles.restore(id,revisionId,request.version())); }
    @GetMapping("/articles/{id}/versions")
    public ApiResponse<?> versions(@PathVariable long id) { return ApiResponse.ok(articles.workspace(id)); }
    @GetMapping("/articles/{id}/version-events")
    public ApiResponse<?> versionEvents(@PathVariable long id) { return ApiResponse.ok(articles.versionEvents(id)); }
    @GetMapping("/revisions/{id}")
    public ApiResponse<?> revision(@PathVariable long id) { return ApiResponse.ok(articles.revision(id)); }
    @GetMapping("/events")
    public ApiResponse<?> events(@RequestParam(required=false) String keyword,@RequestParam(required=false) String scope,@RequestParam(required=false) Long projectId) { return ApiResponse.ok(queries.events(keyword,scope,projectId)); }
    @PostMapping("/events") @RequirePermission("ledger:edit")
    public ApiResponse<?> createEvent(@RequestBody LedgerRequests.Event request) { return ApiResponse.ok(queries.saveEvent(null,request)); }
    @GetMapping("/events/{id}")
    public ApiResponse<?> event(@PathVariable long id) { return ApiResponse.ok(queries.event(id)); }
    @PutMapping("/events/{id}") @RequirePermission("ledger:edit")
    public ApiResponse<?> saveEvent(@PathVariable long id,@RequestBody LedgerRequests.Event request) { return ApiResponse.ok(queries.saveEvent(id,request)); }
    @DeleteMapping("/events/{id}") @RequirePermission("ledger:edit")
    public ApiResponse<?> deleteEvent(@PathVariable long id,@RequestParam Long version) { queries.deleteEvent(id,version); return ApiResponse.ok(); }
    @GetMapping("/assets")
    public ApiResponse<?> assets(@RequestParam(required=false) String scope,@RequestParam(required=false) Long projectId,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(assets.list(scope,projectId,status,keyword,pageNum,pageSize)); }
    @PostMapping("/assets") @RequirePermission("ledger:edit")
    public ApiResponse<?> upload(@RequestParam MultipartFile file) throws IOException { return ApiResponse.ok(assets.upload(file)); }
    @PutMapping("/assets/{id}") @RequirePermission("ledger:edit")
    public ApiResponse<?> asset(@PathVariable long id,@RequestBody LedgerRequests.Asset request) { return ApiResponse.ok(assets.update(id,request)); }
    @PostMapping("/assets/{id}/scan") @RequirePermission("ledger:edit")
    public ApiResponse<?> scan(@PathVariable long id,@RequestBody LedgerRequests.Publish request) { return ApiResponse.ok(assets.retry(id,request.version())); }
    @DeleteMapping("/assets") @RequirePermission("ledger:edit")
    public ApiResponse<?> deleteAssets(@RequestBody List<Long> ids) { assets.delete(ids); return ApiResponse.ok(); }
    @GetMapping("/assets/{id}/references")
    public ApiResponse<?> references(@PathVariable long id) { return ApiResponse.ok(queries.assetReferences(id)); }
    @GetMapping("/assets/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable long id,@RequestParam(defaultValue="false") boolean download) {
        var metadata=assets.get(id); byte[] bytes=assets.content(id);
        String name=String.valueOf(metadata.get("name")).replaceFirst("\\.[^.]+$","")+".png";
        String disposition=(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(name,StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,disposition)
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").body(bytes);
    }
}
