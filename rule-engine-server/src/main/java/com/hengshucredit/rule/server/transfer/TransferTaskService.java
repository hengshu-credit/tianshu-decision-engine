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

    public enum Operation { EXPORT, PREVIEW, IMPORT, IMPORT_AND_VERIFY, SET_PREVIEW, SET_IMPORT;
        boolean singleImport() { return this == IMPORT || this == IMPORT_AND_VERIFY; }
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
        if ((operation == Operation.PREVIEW || operation.singleImport()) && request.options() == null)
            throw new IllegalArgumentException("缺少迁移选项");
        if (operation == Operation.IMPORT_AND_VERIFY && !Boolean.TRUE.equals(request.options().publishRules()))
            throw new IllegalArgumentException("请确认回归并发布导入的规则");
        if (operation == Operation.SET_IMPORT && (request.digest() == null || !request.digest().matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("请先预检项目合集");
        UUID id = request.taskId();
        String actor = operators.resolve();
        try { store.create(id, session, operation.name(), "RUNNING", 0); }
        catch (DuplicateKeyException retry) {
            if (!operation.name().equals(store.owned(id, session).operation())) throw new IllegalArgumentException("迁移任务类型不匹配");
            return id;
        }
        Runnable work = projects.wrap(() -> execute(request, file, actor));
        try {
            worker.execute(() -> {
                try { work.run(); }
                catch (Exception failure) { fail(id, failure); }
                catch (OutOfMemoryError exhausted) {
                    // The worker is replaced by its executor. Finalize the persisted task for polling clients.
                    try { fail(id, new IllegalStateException("生成迁移包时节点内存不足", exhausted)); }
                    finally { throw exhausted; }
                }
            });
        } catch (RejectedExecutionException busy) {
            fail(id, new IllegalStateException("迁移队列已满，请稍后重试"));
        }
        return id;
    }

    private void execute(Request request, TransferTaskStore.Task file, String actor) {
        String id = request.taskId().toString();
        TransferProgress progress = (percent, message) -> store.progress(id, percent, message);
        TransferBundleCodec.Decoded decoded = null;
        try {
            access.require(request.operation().permission());
            if (request.operation() == Operation.EXPORT) {
                export(request, actor, id, progress);
                return;
            }
            progress.report(1, "合并已上传分块，准备读取配置包");
            Path archive = Files.createTempFile("tianshu-transfer-", ".zip");
            try {
                try (var out = Files.newOutputStream(archive)) { store.writeFile(file, out); }
                progress.report(6, "校验配置包结构和内容摘要");
                if (request.operation().singleImport() || request.operation() == Operation.PREVIEW) {
                    decoded = new TransferBundleCodec().decode(archive, request.operation().singleImport()
                            || Boolean.TRUE.equals(request.options().createProject()));
                }
                var verified = decoded;
                transaction.executeWithoutResult(tx -> {
                    Object result = process(request, file.size(), archive, verified, actor, progress.range(8, 95, ""));
                    progress.report(97, "保存迁移结果");
                    store.finish(id, "SUCCESS", 0, result);
                });
            } finally {
                try { Files.deleteIfExists(archive); }
                catch (IOException cleanup) { log.warn("清理临时迁移文件失败，任务 {}", id, cleanup); }
            }
        } catch (IOException error) {
            var failure = new UncheckedIOException("无法读取临时迁移文件", error);
            recordFailure(request, actor, failure, decoded);
            throw failure;
        } catch (RuntimeException failure) {
            recordFailure(request, actor, failure, decoded);
            throw failure;
        }
    }

    private void export(Request request, String actor, String id, TransferProgress progress) throws IOException {
        var archive = Files.createTempFile("tianshu-export-", ".zip");
        try {
            transaction.executeWithoutResult(tx -> {
                var selection = request.selection();
                try (var sink = Files.newOutputStream(archive)) {
                    var bundle = transfer.exportTo(selection.roots(), selection.recursive(), progress.range(0, 95, ""), sink);
                    progress.report(96, "保存配置包和导出审计记录");
                    var saved = store.saveExport(id, archive);
                    Long logId = audit.recordExport(selection.roots(), bundle, saved.digest(), actor);
                    store.finish(id, "SUCCESS", saved.size(), Map.of("logId", logId));
                } catch (IOException failure) {
                    throw new UncheckedIOException("无法保存离线迁移包", failure);
                }
            });
        } finally {
            try { Files.deleteIfExists(archive); }
            catch (IOException cleanup) { log.warn("清理临时导出文件失败，任务 {}", id, cleanup); }
        }
    }

    private Object process(Request request, int size, Path archive, TransferBundleCodec.Decoded decoded, String actor, TransferProgress progress) {
        if (request.operation() == Operation.SET_PREVIEW || request.operation() == Operation.SET_IMPORT) {
            try {
                return projectSets.process(Files.newInputStream(archive), size,
                        request.operation() == Operation.SET_IMPORT ? request.digest() : null, actor, progress);
            } catch (IOException error) { throw new UncheckedIOException("无法读取项目合集", error); }
        }
        var options = request.options();
        boolean preview = request.operation() == Operation.PREVIEW;
        if (preview) return transfer.preview(decoded, options, progress);
        boolean verify = request.operation() == Operation.IMPORT_AND_VERIFY;
        var result = importer.apply(decoded, options, actor, TransferInstallationBindings.Profile.EMPTY, false, verify, progress);
        if (verify) {
            Map<String,String> requested = new java.util.LinkedHashMap<>();
            for (var resource : decoded.bundle().resources()) {
                var type = TransferKey.parse(resource.key()).type();
                if (type == TransferResourceType.RULE || type == TransferResourceType.EXPERIMENT)
                    requested.put(resource.key(), String.valueOf(resource.configuration().get(type.codeField)));
            }
            result.put("publicationSteps", projectSets.publicationSteps(result, requested));
        }
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

    private void recordFailure(Request request, String actor, RuntimeException failure, TransferBundleCodec.Decoded decoded) {
        try {
            if (request.operation().singleImport()) {
                audit.recordDecodedImport(decoded, decoded == null ? null : decoded.packageDigest(),
                        Map.of("taskId", request.taskId().toString(), "options", request.options()), actor,
                        java.util.Objects.toString(failure.getMessage(), "导入失败"));
            } else if (request.operation() == Operation.EXPORT || request.operation() == Operation.SET_IMPORT) {
                audit.recordFailure(request.operation() == Operation.EXPORT ? "EXPORT" : "IMPORT",
                        request.selection() == null ? List.of() : request.selection().roots(), actor, failure.getMessage());
            }
        } catch (RuntimeException auditFailure) { failure.addSuppressed(auditFailure); }
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
