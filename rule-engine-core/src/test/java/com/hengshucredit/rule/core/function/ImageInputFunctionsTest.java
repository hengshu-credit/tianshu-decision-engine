package com.hengshucredit.rule.core.function;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImageInputFunctionsTest {

    private static final byte[] IMAGE_BYTES = "test-image-content".getBytes(StandardCharsets.UTF_8);

    @Test
    public void acceptsPlainBase64AndDataUri() {
        ImageInputFunctions functions = new ImageInputFunctions();
        String base64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);

        assertEquals(base64, functions.imageToBase64(base64, 1000D));
        assertEquals(base64, functions.imageToBase64("data:image/jpeg;base64," + base64, 1000D));
    }

    @Test
    public void downloadsHttpImageAsBase64() {
        String result = functions().imageToBase64("http://test/image", 1000D);

        assertEquals(Base64.getEncoder().encodeToString(IMAGE_BYTES), result);
    }

    @Test
    public void sendsApplicationUserAgentWhenDownloadingHttpImage() {
        StubHttpConnection connection = connection("/requires-user-agent");
        String result = new ImageInputFunctions(url -> connection)
                .imageToBase64("http://test/requires-user-agent", 1000D);

        assertEquals(Base64.getEncoder().encodeToString(IMAGE_BYTES), result);
        String userAgent = connection.userAgent;
        assertTrue(userAgent != null && !userAgent.startsWith("Java/"));
    }

    @Test
    public void rejectsUnsupportedProtocolAndNonSuccessStatus() {
        assertFailure(new ImageInputFunctions(), "file://C:/temp/face.jpg", 1000D, "HTTP(S)");
        assertFailure(functions(), "http://test/missing", 1000D, "404");
    }

    @Test
    public void enforcesDownloadTimeoutAndSizeLimit() {
        assertFailure(functions(), "http://test/slow", 100D, "100 ms");
        assertFailure(functions(), "http://test/large", 1000D, "10 MB");
    }

    private static void assertFailure(ImageInputFunctions functions, String value,
                                      double timeoutMs, String message) {
        try {
            functions.imageToBase64(value, timeoutMs);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
            return;
        }
        throw new AssertionError("Expected image conversion failure");
    }

    private static ImageInputFunctions functions() {
        return new ImageInputFunctions(ImageInputFunctionsTest::connection);
    }

    private static StubHttpConnection connection(URL url) {
        return connection(url.getPath());
    }

    private static StubHttpConnection connection(String path) {
        try {
            URL url = new URL("http://test" + path);
            if ("/missing".equals(path)) return new StubHttpConnection(url, 404, new byte[0], 0L, false);
            if ("/large".equals(path)) {
                return new StubHttpConnection(url, 200, new byte[0],
                        ImageInputFunctions.MAX_IMAGE_BYTES + 1L, false);
            }
            if ("/slow".equals(path)) {
                return new StubHttpConnection(url, 200, IMAGE_BYTES, IMAGE_BYTES.length, true);
            }
            return new StubHttpConnection(url, 200, IMAGE_BYTES, IMAGE_BYTES.length, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubHttpConnection extends HttpURLConnection {
        private final int status;
        private final byte[] body;
        private final long contentLength;
        private final boolean timeout;
        private String userAgent;

        private StubHttpConnection(URL url, int status, byte[] body, long contentLength, boolean timeout) {
            super(url);
            this.status = status;
            this.body = body;
            this.contentLength = contentLength;
            this.timeout = timeout;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            super.setRequestProperty(key, value);
            if ("User-Agent".equalsIgnoreCase(key)) userAgent = value;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public long getContentLengthLong() {
            return contentLength;
        }

        @Override
        public InputStream getInputStream() throws SocketTimeoutException {
            if (timeout) throw new SocketTimeoutException("timed out");
            return new ByteArrayInputStream(body);
        }
    }
}
