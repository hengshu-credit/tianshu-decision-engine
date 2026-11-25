package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.OfflineTransferLogService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class TransferTaskService {
    private static final Logger log = LoggerFactory.getLogger(TransferTaskService.class);
    private final TransferTaskStore store;
    private final OfflineResourceTransferService transfer;
    private final OfflineResourceImportService importer;
    private final TransferProjectSetService projectSets;
    private final OfflineTransferLogService audit;
    private final ConsoleProjectAccess projects;
    private final ConsoleAccessGuard access;
    private final ConsoleOperatorResolver operators;
    private final TransactionTemplate transaction;
    // 大包解压、打包与模型治理共用节点堆内存，单节点逐个处理。
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> new Thread(r, "offline-transfer"));

    public enum Operation { EXPORT, PREVIEW, IMPORT, SET_PREVIEW, SET_IMPORT;
        public String permission() { return this == EXPORT ? "transfer:export" : "transfer:import"; }
    }

    public record Request(UUID taskId, Operation operation, UUID uploadId, String digest,
                          TransferExportRequest selection, TransferImportOptions options) {}
    public record Status(String status, List<TransferTaskStore.Action> actions, Map<String, Object> result) {}

    public TransferTaskService(TransferTaskStore store, OfflineResourceTransferService transfer,
                               OfflineResourceImportService importer, TransferProjectSetService projectSets,
                               OfflineTransferLogService audit, ConsoleProjectAccess projects, ConsoleAccessGuard access,
                               ConsoleOperatorResolver operators, TransactionTemplate transaction) {
        this.store = store;
        this.transfer = transfer;
        this.importer = importer;
        this.projectSets = projectSets;
        this.audit = audit;
        this.projects = projects;
        this.access = access;
        this.operators = operators;
        this.transaction = transaction;
    }

    public UUID submit(Request request, String session) {
        if (request == null || request.taskId() == null || request.operation() == null)
            throw new IllegalArgumentException("缺少迁移操作或任务标识");
        Operation operation = request.operation();
        access.require(operation.permission());
        var file = operation == Operation.EXPORT ? null : store.upload(request.uploadId(), session);
        if (operation == Operation.EXPORT && request.selection() == null) throw new IllegalArgumentException("请选择导出资源");
        if ((operation == Operation.PREVIEW || operation == Operation.IMPORT) && request.options() == null)
            throw new IllegalArgumentException("缺少迁移选项");
        if (operation == Operation.SET_IMPORT && (request.digest() == null || !request.digest().matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("请先预检项目合集");
        UUID id = request.taskId();
        String actor = operators.resolve();
        try { store.create(id, session, operation.name(), "RUNNING", 0); }
        catch (DuplicateKeyException retry) {
            if (!operation.name().equals(store.owned(id, session).operation())) throw new IllegalArgumentException("迁移任务类型不匹配");
            return id;
        }
        Runnable work = projects.wrap(() -> {
            try {
                access.require(operation.permission());
                execute(request, file, actor);
            } catch (RuntimeException failure) {
                // 失败审计也在发起账户的权限上下文中写入，不能落为无归属记录。
                try { recordFailure(request, actor, failure); }
                catch (RuntimeException auditFailure) { failure.addSuppressed(auditFailure); }
                throw failure;
            }
        });
        try {
            worker.execute(() -> {
                try { work.run(); }
                catch (Exception failure) { fail(id, failure); }
            });
        } catch (RejectedExecutionException busy) {
            fail(id, new IllegalStateException("迁移队列已满，请稍后重试"));
        }
        return id;
    }

    private void execute(Request request, TransferTaskStore.Task file, String actor) {
        String id = request.taskId().toString();
        TransferProgress progress = (percent, message) -> store.progress(id, percent, message);
        if (request.operation() == Operation.EXPORT) {
            transaction.executeWithoutResult(tx -> {
                var selection = request.selection();
                var exported = transfer.exportPackage(selection.roots(), selection.recursive(), progress.range(0, 95, ""));
                progress.report(96, "保存配置包和导出审计记录");
                store.saveExport(id, exported.bytes());
                Long logId = audit.recordExport(selection.roots(), exported, actor);
                store.finish(id, "SUCCESS", exported.bytes().length, Map.of("logId", logId));
            });
            return;
        }

        progress.report(1, "合并已上传分块，准备读取配置包");
        try {
            Path archive = Files.createTempFile("tianshu-transfer-", ".zip");
            try {
                try (var out = Files.newOutputStream(archive)) { store.writeFile(file, out); }
                progress.report(6, "校验配置包结构和内容摘要");
                transaction.executeWithoutResult(tx -> {
                    Object result = process(request, file.size(), archive, actor, progress.range(8, 95, ""));
                    progress.report(97, "保存迁移结果");
                    // 导入写入、成功审计和任务终态一起提交，轮询到成功即代表事务已经生效。
                    store.finish(id, "SUCCESS", 0, result);
                });
            } finally {
                try { Files.deleteIfExists(archive); }
                catch (IOException cleanup) { log.warn("清理临时迁移文件失败，任务 {}", id, cleanup); }
            }
        } catch (IOException error) { throw new UncheckedIOException("无法读取临时迁移文件", error); }
    }

    private Object process(Request request, int size, Path archive, String actor, TransferProgress progress) {
        if (request.operation() == Operation.SET_PREVIEW || request.operation() == Operation.SET_IMPORT) {
            try {
                return projectSets.process(Files.newInputStream(archive), size,
                        request.operation() == Operation.SET_IMPORT ? request.digest() : null, actor, progress);
            } catch (IOException error) { throw new UncheckedIOException("无法读取项目合集", error); }
        }
        var options = request.options();
        boolean preview = request.operation() == Operation.PREVIEW;
        var decoded = new TransferBundleCodec().decode(archive, !preview || Boolean.TRUE.equals(options.createProject()));
        if (preview) return transfer.preview(decoded, options, progress);
        var result = importer.apply(decoded, options, actor, TransferInstallationBindings.Profile.EMPTY, false, progress);
        result.put("options", options);
        audit.recordDecodedImport(decoded, decoded.packageDigest(), result, actor, null);
        return result;
    }

    private void fail(UUID id, Exception failure) {
        log.warn("迁移任务 {} 执行失败", id, failure);
        String detail = failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                || failure instanceof com.hengshucredit.rule.server.security.ConsoleAccessDeniedException
                || failure instanceof com.hengshucredit.rule.server.common.RuleGovernanceException
                ? failure.getMessage() : "迁移执行失败，请联系管理员核对服务端日志（任务 " + id + "）";
        store.finish(id.toString(), "FAILED", 0, Map.of("message", detail == null ? "迁移执行失败" : detail));
    }

    private void recordFailure(Request request, String actor, RuntimeException failure) {
        if (request.operation() == Operation.EXPORT || request.operation() == Operation.IMPORT || request.operation() == Operation.SET_IMPORT) {
            audit.recordFailure(request.operation() == Operation.EXPORT ? "EXPORT" : "IMPORT",
                    request.selection() == null ? List.of() : request.selection().roots(), actor, failure.getMessage());
        }
    }

    public TransferTaskStore.Task downloadable(UUID id, String session) {
        var task = store.owned(id, session);
        if (!"EXPORT".equals(task.operation()) || !"SUCCESS".equals(task.status()))
            throw new IllegalArgumentException("配置包尚未生成");
        // 重用审计日志的资源可见性索引，下载时再次检查所有随包资源的当前归属。
        if (!(task.result().get("logId") instanceof Number logId) || audit.getById(logId.longValue()) == null)
            throw new com.hengshucredit.rule.server.security.ConsoleAccessDeniedException("无权下载该配置包");
        return task;
    }

    public Status status(UUID id, String session, long after) {
        var task = store.owned(id, session);
        if ("UPLOAD".equals(task.operation())) throw new IllegalArgumentException("请选择迁移任务");
        access.require(Operation.valueOf(task.operation()).permission());
        return new Status(task.status(), store.actions(task.id(), after), task.result());
    }

    @PreDestroy public void close() { worker.shutdown(); }
}
