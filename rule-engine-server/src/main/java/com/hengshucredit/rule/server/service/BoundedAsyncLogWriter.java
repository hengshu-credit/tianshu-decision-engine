package com.hengshucredit.rule.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

class BoundedAsyncLogWriter<T> implements AutoCloseable {

    interface BatchSink<T> {
        void write(List<T> batch) throws Exception;
    }

    private final ArrayBlockingQueue<T> detailQueue;
    private final ArrayBlockingQueue<T> metadataQueue;
    private final int batchSize;
    private final long flushMillis;
    private final long shutdownWaitMillis;
    private final Function<T, T> metadataOnly;
    private final BatchSink<T> sink;
    private final ExecutorService executor;
    private final AtomicLong droppedBodies = new AtomicLong();
    private final AtomicLong droppedLogs = new AtomicLong();
    private volatile boolean running;

    BoundedAsyncLogWriter(int capacity, int batchSize, long flushMillis, long shutdownWaitMillis,
                          String threadName, Function<T, T> metadataOnly, BatchSink<T> sink) {
        int boundedCapacity = Math.max(2, capacity);
        int metadataCapacity = Math.max(1, boundedCapacity / 10);
        int detailCapacity = Math.max(1, boundedCapacity - metadataCapacity);
        this.detailQueue = new ArrayBlockingQueue<>(detailCapacity);
        this.metadataQueue = new ArrayBlockingQueue<>(metadataCapacity);
        this.batchSize = Math.max(1, batchSize);
        this.flushMillis = Math.max(10L, flushMillis);
        this.shutdownWaitMillis = Math.max(0L, shutdownWaitMillis);
        this.metadataOnly = metadataOnly;
        this.sink = sink;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(threadFactory);
    }

    void start() {
        if (running) return;
        running = true;
        executor.submit(this::runLoop);
    }

    boolean offer(T event) {
        if (event == null) return false;
        if (detailQueue.offer(event)) return true;
        droppedBodies.incrementAndGet();
        T metadata = metadataOnly.apply(event);
        if (metadataQueue.offer(metadata)) return true;
        droppedLogs.incrementAndGet();
        return false;
    }

    long getDroppedBodies() { return droppedBodies.get(); }
    long getDroppedLogs() { return droppedLogs.get(); }
    int detailSize() { return detailQueue.size(); }
    int metadataSize() { return metadataQueue.size(); }

    private void runLoop() {
        boolean metadataFirst = false;
        while (running || !detailQueue.isEmpty() || !metadataQueue.isEmpty()) {
            try {
                List<T> batch = new ArrayList<>(batchSize);
                T first = metadataFirst
                        ? metadataQueue.poll(flushMillis, TimeUnit.MILLISECONDS)
                        : detailQueue.poll(flushMillis, TimeUnit.MILLISECONDS);
                if (first == null) {
                    first = metadataFirst ? detailQueue.poll() : metadataQueue.poll();
                }
                metadataFirst = !metadataFirst;
                if (first == null) continue;
                batch.add(first);
                detailQueue.drainTo(batch, batchSize - batch.size());
                if (batch.size() < batchSize) {
                    metadataQueue.drainTo(batch, batchSize - batch.size());
                }
                try {
                    sink.write(batch);
                } catch (Exception ignored) {
                    droppedLogs.addAndGet(batch.size());
                }
            } catch (InterruptedException e) {
                if (running) continue;
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownWaitMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
