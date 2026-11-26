package com.hengshucredit.rule.server.upload;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** ClamD INSTREAM：仅显式 OK 放行，断连、超时、协议错误与未配置均保留待检。 */
@Service
public class UploadSecurity {
    private final Semaphore scans = new Semaphore(2);
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "upload-scan-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private final UploadScanProperties properties;
    public UploadSecurity(UploadScanProperties properties) {
        this.properties = properties;
        deadlines.setRemoveOnCancelPolicy(true);
    }

    @jakarta.annotation.PreDestroy public void close() { deadlines.shutdownNow(); }

    public record Result(String status, String message) { }

    public static String filename(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.chars().anyMatch(c -> c < 32 || c == 127 || c == '/' || c == '\\')) {
            throw new IllegalArgumentException("文件名称不能为空，且不能包含路径或控制字符");
        }
        return name;
    }

    public static byte[] read(MultipartFile file, long limit) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > limit) throw new IllegalArgumentException("文件为空或超过上传大小上限");
        filename(file.getOriginalFilename());
        try (var input = file.getInputStream()) {
            int max = (int) Math.min(limit, Integer.MAX_VALUE - 1L);
            byte[] bytes = input.readNBytes(max + 1);
            if (bytes.length == 0 || bytes.length > max) throw new IllegalArgumentException("文件为空或超过上传大小上限");
            return bytes;
        }
    }

    public Result scan(byte[] content) {
        if (!scans.tryAcquire()) return new Result("PENDING", "安全检测队列已满，请稍后重试");
        try {
            return scanWithClamd(content);
        } finally {
            scans.release();
        }
    }

    private Result scanWithClamd(byte[] content) {
        if (properties.getAntivirusHost() == null || properties.getAntivirusHost().isBlank()) {
            return new Result("PENDING", "安全检测服务未配置，请在 server 的生效配置中设置 UPLOAD_ANTIVIRUS_HOST，并重建 server 容器使配置生效");
        }
        int timeout = Math.max(1000, Math.min(properties.getAntivirusTimeoutMs(), 30000));
        Socket socket = new Socket();
        var deadline = deadlines.schedule(() -> {
            try { socket.close(); } catch (IOException ignored) { }
        }, timeout, TimeUnit.MILLISECONDS);
        try (socket) {
            socket.connect(new InetSocketAddress(properties.getAntivirusHost(), properties.getAntivirusPort()), Math.min(timeout, 3000));
            socket.setSoTimeout(timeout);
            var output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += 8192) {
                int count = Math.min(8192, content.length - offset);
                output.writeInt(count); output.write(content, offset, count);
            }
            output.writeInt(0); output.flush();
            var response = new ByteArrayOutputStream();
            while (response.size() < 8192) {
                int value = socket.getInputStream().read();
                if (value < 0) throw new IOException("Incomplete antivirus response");
                if (value == 0) {
                    String text = response.toString(StandardCharsets.UTF_8);
                    if ("stream: OK".equals(text)) return new Result("CLEAN", "安全检测通过");
                    if (text.startsWith("stream: ") && text.endsWith(" FOUND")) return new Result("QUARANTINED", "检测到威胁，文件已隔离");
                    return new Result("PENDING", "安全检测未完成，请稍后重试");
                }
                response.write(value);
            }
            return new Result("PENDING", "安全检测响应异常，文件尚未开放访问");
        } catch (IOException | IllegalArgumentException error) {
            return new Result("PENDING", "安全检测服务暂不可用，文件尚未开放访问");
        } finally {
            deadline.cancel(false);
        }
    }
}
