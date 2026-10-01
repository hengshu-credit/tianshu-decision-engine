package com.hengshucredit.rule.client.log;

import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Request;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpLogReporterTest {

    private HttpServer server;
    private ExecutorService serverExecutor;

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test
    public void reportReturnsWithoutWaitingForHttpResponseAndCloseFlushesTheLog() throws Exception {
        CountDownLatch requestEntered = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            requestEntered.countDown();
            await(releaseResponse);
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = new HttpLogReporter(serverUrl, 2000);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        Future<?> call = caller.submit(() -> reporter.report(Collections.singletonList(log("ASYNC"))));
        boolean returnedWithoutResponse = true;
        try {
            call.get(300, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expectedForSynchronousImplementation) {
            returnedWithoutResponse = false;
        } finally {
            releaseResponse.countDown();
            call.get(3, TimeUnit.SECONDS);
            closeIfSupported(reporter);
            caller.shutdownNow();
        }

        assertTrue("report() must only enqueue and return", returnedWithoutResponse);
        assertTrue("close() must flush the queued log", requestEntered.await(2, TimeUnit.SECONDS));
        assertEquals(1, requestCount.get());
    }

    @Test
    public void failedHttpReportIsRetriedUntilItSucceeds() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 200, "{\"code\":500,\"message\":\"temporarily unavailable\"}");
            } else {
                respond(exchange, attempt < 3 ? 500 : 200, "{\"code\":200}");
            }
        });
        HttpLogReporter reporter = new HttpLogReporter(serverUrl, 1000);

        reporter.report(Collections.singletonList(log("RETRY")));
        closeIfSupported(reporter);

        assertTrue("expected initial request plus two retries",
                awaitCount(requestCount, 3, 3000));
        assertEquals(3, requestCount.get());
    }

    @Test
    public void fullBufferDropsNewestLogAndTracksTheDrop() throws Exception {
        CountDownLatch firstRequestEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            int current = requestCount.incrementAndGet();
            if (current == 1) {
                firstRequestEntered.countDown();
                await(releaseFirstRequest);
            }
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 2, 1, 10);
        try {
            reporter.report(Collections.singletonList(log("FIRST")));
            assertTrue(firstRequestEntered.await(2, TimeUnit.SECONDS));

            reporter.report(Arrays.asList(log("SECOND"), log("THIRD"), log("DROPPED")));

            assertEquals(1L, droppedLogCount(reporter));
        } finally {
            releaseFirstRequest.countDown();
            closeIfSupported(reporter);
        }
        assertEquals("the newest log is dropped when the queue is full", 3, requestCount.get());
    }

    @Test
    public void incompleteBatchWaitsForFlushWindowAndSecondLogCompletesTheBatch() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        CountDownLatch requestReceived = new CountDownLatch(1);
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            requestReceived.countDown();
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 10, 2, 2000);
        try {
            reporter.report(Collections.singletonList(log("FIRST")));
            assertTrue("an incomplete batch must wait for its flush window",
                    !requestReceived.await(750, TimeUnit.MILLISECONDS));

            reporter.report(Collections.singletonList(log("SECOND")));
            assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
            assertEquals("both logs must be delivered in one batch request", 1, requestCount.get());
        } finally {
            closeIfSupported(reporter);
        }
    }

    @Test
    public void reporterCanRestartAfterClientLifecycleClose() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 10, 1, 10);

        reporter.report(Collections.singletonList(log("BEFORE_CLOSE")));
        reporter.close();
        reporter.start();
        reporter.report(Collections.singletonList(log("AFTER_RESTART")));
        reporter.close();

        assertEquals(2, requestCount.get());
    }

    @Test
    public void reportAcceptanceAndCloseStateChangeAreAtomic() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 10, 1, 10);
        BlockingFirstOfferQueue queue = new BlockingFirstOfferQueue(10);
        setQueue(reporter, queue);

        Thread reporting = new Thread(() -> reporter.report(Collections.singletonList(log("RACING"))));
        reporting.start();
        assertTrue(queue.firstOfferEntered.await(2, TimeUnit.SECONDS));
        Thread closing = new Thread(reporter::close);
        closing.start();
        Thread.sleep(150);
        boolean closeWaitedForAcceptedOffer = closing.isAlive();
        queue.releaseFirstOffer.countDown();
        reporting.join(2000);
        closing.join(3000);

        assertTrue("close must serialize with an in-progress acceptance decision", closeWaitedForAcceptedOffer);
        assertFalse(reporting.isAlive());
        assertFalse(closing.isAlive());
        assertEquals("an accepted racing log must be flushed", 1, requestCount.get());
        assertEquals(0L, reporter.getDroppedLogCount());

        reporter.report(Collections.singletonList(log("AFTER_CLOSE")));
        assertEquals("a post-close log must be observably dropped", 1L, reporter.getDroppedLogCount());
        assertEquals(1, requestCount.get());
    }

    @Test
    public void closeCancelsUnresponsiveCallWithinBoundedTimeAndCountsFailedBatch() throws Exception {
        CountDownLatch requestEntered = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            requestEntered.countDown();
            await(releaseResponse);
            respond(exchange, 200, "{\"code\":200}");
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 1000, 10, 1, 10);
        reporter.report(Collections.singletonList(log("HANGING")));
        assertTrue(requestEntered.await(2, TimeUnit.SECONDS));

        long started = System.nanoTime();
        reporter.close();
        long closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        releaseResponse.countDown();

        assertTrue("close must have a finite shutdown deadline, elapsed=" + closeMillis,
                closeMillis < 1500);
        assertEquals(1L, reporter.getFailedBatchCount());
        assertEquals(1L, reporter.getFailedLogCount());
        Thread.sleep(200);
        assertEquals("shutdown cancellation must not start another delivery attempt", 1, requestCount.get());
        assertTrue("cancelled worker must terminate before close returns", workerThread(reporter) == null);

        reporter.start();
        reporter.report(Collections.singletonList(log("AFTER_CANCEL_RESTART")));
        reporter.close();
        assertEquals("restart must use one fresh worker without the cancelled worker retrying", 2,
                requestCount.get());
    }

    @Test
    public void closeDuringAuthenticationPreventsPublishingAnotherCallAndAllowsRestartAfterWorkerStops()
            throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, "{\"code\":200}");
        });
        BlockingAuthenticator authenticator = new BlockingAuthenticator(serverUrl);
        HttpLogReporter reporter = new HttpLogReporter(serverUrl, 1000, authenticator,
                10, 1, 10);
        reporter.report(Collections.singletonList(log("AUTH_RACE")));
        assertTrue(authenticator.entered.await(2, TimeUnit.SECONDS));

        long started = System.nanoTime();
        reporter.close();
        long closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue("close must remain bounded while authentication is blocked, elapsed=" + closeMillis,
                closeMillis < 1500);
        assertEquals(0, requestCount.get());
        try {
            reporter.start();
            fail("restart must be rejected until the closing worker has stopped");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("shutting down"));
        }

        authenticator.release.countDown();
        assertTrue("the closing worker must stop after authentication returns",
                awaitWorkerStopped(reporter, 3000));
        assertEquals("a call must not be published after shutdown abort", 0, requestCount.get());
        assertEquals(1L, reporter.getFailedBatchCount());
        assertEquals("the active batch must only be counted once", 1L, reporter.getFailedLogCount());

        reporter.start();
        reporter.report(Collections.singletonList(log("AFTER_AUTH_RACE")));
        reporter.close();
        assertEquals("restart must deliver through a fresh worker", 1, requestCount.get());
    }

    @Test
    public void closeDuringRetryDelayDoesNotStartAnotherAttemptOrDoubleCountFailure() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        CountDownLatch firstResponseSent = new CountDownLatch(1);
        String serverUrl = startServer(exchange -> {
            int attempt = requestCount.incrementAndGet();
            respond(exchange, attempt == 1 ? 500 : 200, "{\"code\":200}");
            if (attempt == 1) firstResponseSent.countDown();
        });
        HttpLogReporter reporter = new HttpLogReporter(serverUrl, 1000, null,
                10, 1, 10, 2000L);
        reporter.report(Collections.singletonList(log("RETRY_DELAY_RACE")));
        assertTrue(firstResponseSent.await(2, TimeUnit.SECONDS));
        assertTrue("the first Call must be cleared before closing during retry delay",
                awaitActiveCallCleared(reporter, 1000));

        long started = System.nanoTime();
        reporter.close();
        long closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue("close must remain bounded while the worker is in retry delay, elapsed=" + closeMillis,
                closeMillis < 1500);
        assertEquals(1, requestCount.get());
        assertEquals("a retry only counts when another attempt actually starts", 0L,
                reporter.getRetryCount());
        assertEquals(1L, reporter.getFailedBatchCount());
        assertEquals(1L, reporter.getFailedLogCount());
        try {
            reporter.start();
            fail("restart must be rejected while the delayed worker is still closing");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("shutting down"));
        }

        assertTrue("the delayed worker must stop without another attempt",
                awaitWorkerStopped(reporter, 3000));
        assertEquals(1, requestCount.get());
        assertEquals("the failed batch must still only be counted once", 1L,
                reporter.getFailedBatchCount());

        reporter.start();
        reporter.report(Collections.singletonList(log("AFTER_RETRY_DELAY")));
        reporter.close();
        assertEquals("a fresh worker must deliver without an old close cancelling it", 2,
                requestCount.get());
    }

    @Test
    public void emptySuccessBodyIsRetriedThreeTimesThenCountedFailed() throws Exception {
        assertInvalidSuccessEnvelopeIsRetried("");
    }

    @Test
    public void missingCodeSuccessBodyIsRetriedThreeTimesThenCountedFailed() throws Exception {
        assertInvalidSuccessEnvelopeIsRetried("{}");
    }

    @Test
    public void malformedSuccessBodyIsRetriedThreeTimesThenCountedFailed() throws Exception {
        assertInvalidSuccessEnvelopeIsRetried("not-json");
    }

    @Test
    public void zeroHttpTimeoutIsRejectedInsteadOfAllowingUnboundedCalls() {
        try {
            new HttpLogReporter("http://127.0.0.1", 0);
            fail("Expected a strictly positive HTTP timeout");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("timeoutMs"));
        }
    }

    private String startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/api/rule/log/report", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RuleExecutionLog log(String ruleCode) {
        RuleExecutionLog entry = new RuleExecutionLog();
        entry.setRuleCode(ruleCode);
        return entry;
    }

    private HttpLogReporter configuredReporter(String serverUrl, int bufferSize,
                                                int batchSize, int flushIntervalMs) throws Exception {
        return configuredReporter(serverUrl, 1000, bufferSize, batchSize, flushIntervalMs);
    }

    private HttpLogReporter configuredReporter(String serverUrl, int timeoutMs, int bufferSize,
                                                int batchSize, int flushIntervalMs) throws Exception {
        Constructor<HttpLogReporter> constructor = HttpLogReporter.class.getConstructor(
                String.class, int.class,
                com.hengshucredit.rule.client.auth.ClientRequestAuthenticator.class,
                int.class, int.class, int.class);
        return constructor.newInstance(serverUrl, timeoutMs, null, bufferSize, batchSize, flushIntervalMs);
    }

    private void setQueue(HttpLogReporter reporter, ArrayBlockingQueue<RuleExecutionLog> queue) throws Exception {
        Field field = HttpLogReporter.class.getDeclaredField("queue");
        field.setAccessible(true);
        field.set(reporter, queue);
    }

    private Thread workerThread(HttpLogReporter reporter) throws Exception {
        Field field = HttpLogReporter.class.getDeclaredField("worker");
        field.setAccessible(true);
        return (Thread) field.get(reporter);
    }

    private boolean awaitWorkerStopped(HttpLogReporter reporter, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (workerThread(reporter) != null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return workerThread(reporter) == null;
    }

    private boolean awaitActiveCallCleared(HttpLogReporter reporter, long timeoutMs) throws Exception {
        Field field = HttpLogReporter.class.getDeclaredField("activeCall");
        field.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (field.get(reporter) != null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return field.get(reporter) == null;
    }

    private void assertInvalidSuccessEnvelopeIsRetried(String body) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        String serverUrl = startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, body);
        });
        HttpLogReporter reporter = configuredReporter(serverUrl, 10, 1, 10);

        reporter.report(Collections.singletonList(log("INVALID_ENVELOPE")));
        reporter.close();

        assertEquals(3, requestCount.get());
        assertEquals(2L, reporter.getRetryCount());
        assertEquals(1L, reporter.getFailedBatchCount());
        assertEquals(1L, reporter.getFailedLogCount());
    }

    private long droppedLogCount(HttpLogReporter reporter) throws Exception {
        Method method = HttpLogReporter.class.getMethod("getDroppedLogCount");
        return ((Number) method.invoke(reporter)).longValue();
    }

    private void closeIfSupported(HttpLogReporter reporter) throws Exception {
        if (reporter instanceof AutoCloseable) {
            ((AutoCloseable) reporter).close();
        }
    }

    private boolean awaitCount(AtomicInteger count, int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (count.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return count.get() >= expected;
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void respond(HttpExchange exchange, int status) throws IOException {
        respond(exchange, status, "{\"code\":200}");
    }

    private void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private class BlockingFirstOfferQueue extends ArrayBlockingQueue<RuleExecutionLog> {
        private final CountDownLatch firstOfferEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstOffer = new CountDownLatch(1);
        private final AtomicInteger offerCount = new AtomicInteger();

        private BlockingFirstOfferQueue(int capacity) {
            super(capacity);
        }

        @Override
        public boolean offer(RuleExecutionLog value) {
            if (offerCount.incrementAndGet() == 1) {
                firstOfferEntered.countDown();
                await(releaseFirstOffer);
            }
            return super.offer(value);
        }
    }

    private class BlockingAuthenticator extends ClientRequestAuthenticator {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingAuthenticator(String serverUrl) {
            super(serverUrl, 1000, null);
        }

        @Override
        public Request authenticate(Request request) throws IOException {
            entered.countDown();
            await(release);
            return request;
        }
    }
}
