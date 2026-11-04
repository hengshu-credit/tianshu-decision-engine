package com.hengshucredit.rule.server.ledger;

import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** ClamD INSTREAM：仅显式 OK 放行，断连、超时、协议错误与未配置均保留待检。 */
@Service
public class LedgerAntivirus {
    private final LedgerProperties properties;
    public LedgerAntivirus(LedgerProperties properties) { this.properties = properties; }

    public record Result(String status, String message) { }

    public Result scan(byte[] content) {
        if (properties.getAntivirusHost() == null || properties.getAntivirusHost().isBlank()) {
            return new Result("PENDING", "安全检测服务未配置，素材尚未开放访问");
        }
        try (Socket socket = new Socket()) {
            int timeout = Math.max(1000, Math.min(properties.getAntivirusTimeoutMs(), 30000));
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
                    if (text.startsWith("stream: ") && text.endsWith(" FOUND")) return new Result("QUARANTINED", "检测到威胁，素材已隔离");
                    return new Result("PENDING", "安全检测未完成，请稍后重试");
                }
                response.write(value);
            }
            return new Result("PENDING", "安全检测响应异常，素材尚未开放访问");
        } catch (IOException | IllegalArgumentException error) {
            return new Result("PENDING", "安全检测服务暂不可用，素材尚未开放访问");
        }
    }
}
