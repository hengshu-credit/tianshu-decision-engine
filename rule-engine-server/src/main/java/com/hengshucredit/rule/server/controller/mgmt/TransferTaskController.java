package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.transfer.TransferTaskService;
import com.hengshucredit.rule.server.transfer.TransferTaskStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rule/transfer")
@RequiredArgsConstructor
public class TransferTaskController {
    private final TransferTaskStore store;
    private final TransferTaskService tasks;

    @PostMapping("/uploads")
    @RequirePermission("transfer:import")
    public ApiResponse<Map<String, Object>> upload(@RequestParam int size, HttpSession session) {
        if (size <= 0 || size > TransferTaskStore.MAX_SIZE) throw new IllegalArgumentException("配置包为空或超过 512 MiB");
        UUID id = UUID.randomUUID();
        store.create(id, owner(session), "UPLOAD", "OPEN", size);
        return ApiResponse.ok(Map.of("uploadId", id, "partSize", TransferTaskStore.PART_SIZE));
    }

    @PutMapping(value = "/uploads/{id}/{index}", consumes = "application/octet-stream")
    @RequirePermission("transfer:import")
    public ApiResponse<Void> part(@PathVariable UUID id, @PathVariable int index, HttpServletRequest request) throws IOException {
        store.receive(id, owner(request.getSession()), index, request.getInputStream());
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks")
    @RequirePermission(value = "transfer:export", anyOf = "transfer:import")
    public ApiResponse<UUID> submit(@RequestBody TransferTaskService.Request request, HttpSession session) {
        return ApiResponse.ok(tasks.submit(request, owner(session)));
    }

    @GetMapping("/tasks/{id}")
    @RequirePermission(value = "transfer:export", anyOf = "transfer:import")
    public ApiResponse<TransferTaskService.Status> status(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after, HttpSession session) {
        return ApiResponse.ok(tasks.status(id, owner(session), after));
    }

    @GetMapping("/tasks/{id}/download")
    @RequirePermission("transfer:export")
    public void download(@PathVariable UUID id, HttpSession session, HttpServletResponse response) throws IOException {
        var file = tasks.downloadable(id, owner(session));
        response.setContentType("application/zip");
        response.setContentLength(file.size());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition", "attachment; filename=tianshu-resource-transfer.zip");
        store.writeFile(file, response.getOutputStream());
    }

    private static String owner(HttpSession session) { return Sha256Digests.text(session.getId()); }
}
