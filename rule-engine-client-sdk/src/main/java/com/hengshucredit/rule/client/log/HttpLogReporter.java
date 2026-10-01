package com.hengshucredit.rule.client.log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class HttpLogReporter implements ExecutionLogReporter {

    private static final Logger log = LoggerFactory.getLogger(HttpLogReporter.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int DEFAULT_BUFFER_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int DEFAULT_FLUSH_INTERVAL_MS = 5000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 50L;
    private static final long SHUTDOWN_GRACE_MS = 500L;
    private static final RuleExecutionLog CLOSE_SIGNAL = new RuleExecutionLog();

    private enum LifecycleState {
        STOPPED,
        RUNNING,
        CLOSING
    }

    private final OkHttpClient httpClient;
    private final String reportUrl;
    private final ClientRequestAuthenticator authenticator;
    private final ArrayBlockingQueue<RuleExecutionLog> queue;
    private final int batchSize;
    private final int flushIntervalMs;
    private final long retryDelayMs;
    private final AtomicLong droppedLogCount = new AtomicLong();
    private final AtomicLong retryCount = new AtomicLong();
    private final AtomicLong failedBatchCount = new AtomicLong();
    private final AtomicLong failedLogCount = new AtomicLong();
    private final Object lifecycleLock = new Object();
    private volatile Thread worker;
    private volatile LifecycleState lifecycleState = LifecycleState.STOPPED;
    private volatile long generation;
    private long abortGeneration = -1L;
    private Call activeCall;
    private long activeCallGeneration = -1L;
    private int activeBatchSize;
    private long activeBatchGeneration = -1L;
    private boolean activeFailureAccounted;

    public HttpLogReporter(String serverUrl, int timeoutMs) {
        this(serverUrl, timeoutMs, (ClientRequestAuthenticator) null);
    }

    public HttpLogReporter(String serverUrl, int timeoutMs, String token) {
        this(serverUrl, timeoutMs, token == null || token.isEmpty() ? null
                : new ClientRequestAuthenticator(serverUrl, timeoutMs, ClientAuthConfig.legacyToken(token)));
    }

    public HttpLogReporter(String serverUrl, int timeoutMs, ClientRequestAuthenticator authenticator) {
        this(serverUrl, timeoutMs, authenticator,
                DEFAULT_BUFFER_SIZE, DEFAULT_BATCH_SIZE, DEFAULT_FLUSH_INTERVAL_MS);
    }

    public HttpLogReporter(String serverUrl, int timeoutMs, ClientRequestAuthenticator authenticator,
                           int bufferSize, int batchSize, int flushIntervalMs) {
        this(serverUrl, timeoutMs, authenticator, bufferSize, batchSize, flushIntervalMs,
                RETRY_DELAY_MS);
    }

    HttpLogReporter(String serverUrl, int timeoutMs, ClientRequestAuthenticator authenticator,
                    int bufferSize, int batchSize, int flushIntervalMs, long retryDelayMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than 0");
        }
        if (retryDelayMs <= 0) {
            throw new IllegalArgumentException("retryDelayMs must be greater than 0");
        }
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.reportUrl = baseUrl + "/api/rule/log/report";
        this.authenticator = authenticator;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, bufferSize));
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(1, flushIntervalMs);
        this.retryDelayMs = retryDelayMs;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        start();
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.RUNNING) return;
            if (lifecycleState == LifecycleState.CLOSING
                    || (worker != null && worker.isAlive())) {
                throw new IllegalStateException("previous HTTP log reporter worker is still shutting down");
            }
            long workerGeneration = ++generation;
            lifecycleState = LifecycleState.RUNNING;
            worker = new Thread(() -> runLoop(workerGeneration), "rule-client-log-reporter");
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override
    public void report(List<RuleExecutionLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        for (RuleExecutionLog entry : logs) {
            if (entry == null) continue;
            synchronized (lifecycleLock) {
                if (lifecycleState != LifecycleState.RUNNING || !queue.offer(entry)) {
                    recordDropped(1);
                }
            }
        }
    }

    public long getDroppedLogCount() {
        return droppedLogCount.get();
    }

    public long getRetryCount() {
        return retryCount.get();
    }

    public long getFailedBatchCount() {
        return failedBatchCount.get();
    }

    public long getFailedLogCount() {
        return failedLogCount.get();
    }

    private void runLoop(long workerGeneration) {
        try {
            while (shouldContinue(workerGeneration)) {
                List<RuleExecutionLog> batch = new ArrayList<>(batchSize);
                try {
                    RuleExecutionLog first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                    if (first == null) continue;
                    if (first == CLOSE_SIGNAL) continue;
                    batch.add(first);
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs);
                    while (isRunning(workerGeneration) && batch.size() < batchSize) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) break;
                        RuleExecutionLog next = queue.poll(remaining, TimeUnit.NANOSECONDS);
                        if (next == null) break;
                        if (next == CLOSE_SIGNAL) break;
                        batch.add(next);
                    }
                } catch (InterruptedException e) {
                    if (isRunning(workerGeneration)) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    Thread.interrupted();
                }
                while (batch.size() < batchSize) {
                    RuleExecutionLog next = queue.poll();
                    if (next == null || next == CLOSE_SIGNAL) break;
                    batch.add(next);
                }
                if (!batch.isEmpty()) {
                    sendWithRetry(batch, workerGeneration);
                }
            }
        } finally {
            synchronized (lifecycleLock) {
                if (worker == Thread.currentThread() && generation == workerGeneration) {
                    worker = null;
                    lifecycleState = LifecycleState.STOPPED;
                    lifecycleLock.notifyAll();
                }
            }
        }
    }

    private boolean shouldContinue(long workerGeneration) {
        return generation == workerGeneration
                && (lifecycleState == LifecycleState.RUNNING || !queue.isEmpty());
    }

    private boolean isRunning(long workerGeneration) {
        return generation == workerGeneration && lifecycleState == LifecycleState.RUNNING;
    }

    private void sendWithRetry(List<RuleExecutionLog> batch, long workerGeneration) {
        synchronized (lifecycleLock) {
            activeBatchSize = batch.size();
            activeBatchGeneration = workerGeneration;
            activeFailureAccounted = false;
        }
        Exception lastFailure = null;
        int attempts = 0;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (!beginAttempt(workerGeneration, attempt)) break;
            attempts = attempt;
            try {
                sendBatch(batch, workerGeneration);
                clearActiveBatch(workerGeneration);
                return;
            } catch (ShutdownAbortException e) {
                lastFailure = e;
                break;
            } catch (Exception e) {
                lastFailure = e;
                if (attempt >= MAX_ATTEMPTS) break;
                try {
                    Thread.sleep(retryDelayMs * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.interrupted();
                }
            }
        }
        recordFailedActiveBatch(workerGeneration);
        long failed = failedBatchCount.get();
        clearActiveBatch(workerGeneration);
        log.warn("HTTP log report failed after {} attempts; batchSize={}, totalFailedBatches={}, error={}",
                attempts, batch.size(), failed,
                lastFailure == null ? "shutdown requested" : lastFailure.getMessage());
    }

    private boolean beginAttempt(long workerGeneration, int attempt) {
        synchronized (lifecycleLock) {
            if (abortGeneration == workerGeneration || generation != workerGeneration) {
                return false;
            }
            if (attempt > 1) retryCount.incrementAndGet();
            return true;
        }
    }

    private void clearActiveBatch(long workerGeneration) {
        synchronized (lifecycleLock) {
            if (activeBatchGeneration == workerGeneration) {
                activeBatchSize = 0;
                activeBatchGeneration = -1L;
            }
        }
    }

    private void sendBatch(List<RuleExecutionLog> batch, long workerGeneration) throws Exception {
        RequestBody body = RequestBody.create(JSON.toJSONString(batch), JSON_TYPE);
        Request request = new Request.Builder().url(reportUrl).post(body).build();
        if (authenticator != null) request = authenticator.authenticate(request);
        Call call = httpClient.newCall(request);
        synchronized (lifecycleLock) {
            if (abortGeneration == workerGeneration || generation != workerGeneration) {
                throw new ShutdownAbortException();
            }
            activeCall = call;
            activeCallGeneration = workerGeneration;
        }
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP status " + response.code());
            }
            String responseBody = response.body() == null ? null : response.body().string();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                throw new IOException("Empty response envelope");
            }
            JSONObject envelope = JSON.parseObject(responseBody);
            Integer code = envelope.getInteger("code");
            if (code == null) {
                throw new IOException("Response envelope is missing code");
            }
            if (code != 200) {
                throw new IOException("Business code " + code + ": " + envelope.getString("message"));
            }
        } finally {
            synchronized (lifecycleLock) {
                if (activeCall == call && activeCallGeneration == workerGeneration) {
                    activeCall = null;
                    activeCallGeneration = -1L;
                }
            }
        }
    }

    @Override
    public void close() {
        Thread closingWorker;
        long closingGeneration;
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.STOPPED) return;
            closingWorker = worker;
            closingGeneration = generation;
            if (lifecycleState == LifecycleState.RUNNING) {
                lifecycleState = LifecycleState.CLOSING;
            }
            queue.offer(CLOSE_SIGNAL);
            if (Thread.currentThread() == closingWorker) return;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_GRACE_MS);
        joinUntil(closingWorker, deadline);
        if (closingWorker != null && closingWorker.isAlive()) {
            Call call = null;
            synchronized (lifecycleLock) {
                if (generation == closingGeneration
                        && lifecycleState == LifecycleState.CLOSING) {
                    abortGeneration = closingGeneration;
                    if (activeCallGeneration == closingGeneration) {
                        call = activeCall;
                    }
                }
            }
            if (call != null) call.cancel();
            joinUntil(closingWorker, deadline + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_GRACE_MS));
        }
        synchronized (lifecycleLock) {
            if (generation == closingGeneration
                    && lifecycleState == LifecycleState.CLOSING
                    && closingWorker != null && closingWorker.isAlive()) {
                int queued = removePendingLogs();
                int active = activeBatchGeneration == closingGeneration ? activeBatchSize : 0;
                if (active > 0) {
                    recordFailedActiveBatch(closingGeneration);
                }
                if (queued > 0) recordDropped(queued);
                log.warn("HTTP log reporter shutdown deadline exceeded; activeLogs={}, droppedQueued={}",
                        active, queued);
            }
            if (generation == closingGeneration && worker == closingWorker
                    && (closingWorker == null || !closingWorker.isAlive())) {
                worker = null;
                if (lifecycleState == LifecycleState.CLOSING) {
                    lifecycleState = LifecycleState.STOPPED;
                }
            }
        }
    }

    private void joinUntil(Thread thread, long deadline) {
        if (thread == null) return;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return;
        try {
            long millis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int removePendingLogs() {
        int count = 0;
        RuleExecutionLog entry;
        while ((entry = queue.poll()) != null) {
            if (entry != CLOSE_SIGNAL) count++;
        }
        return count;
    }

    private void recordDropped(long count) {
        long dropped = droppedLogCount.addAndGet(count);
        if (dropped == count || dropped % 100 < count) {
            log.warn("HTTP log buffer is full or closed; dropped newest log, totalDropped={}", dropped);
        }
    }

    private void recordFailedActiveBatch(long workerGeneration) {
        synchronized (lifecycleLock) {
            if (activeBatchGeneration != workerGeneration) return;
            if (activeFailureAccounted) return;
            activeFailureAccounted = true;
            failedBatchCount.incrementAndGet();
            failedLogCount.addAndGet(activeBatchSize);
        }
    }

    private static class ShutdownAbortException extends IOException {
        private ShutdownAbortException() {
            super("HTTP log reporter shutdown requested");
        }
    }
}
