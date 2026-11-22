package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/** 在首次使用时加载本地发行包。GraalJS 的同一 Context 一次只允许一个线程进入。 */
final class FakerGenerator {
    private FakerGenerator() {}
    private static Value generate;

    static synchronized JsonNode sample(String method) {
        if (!FakerMethods.METHODS.containsKey(method)) throw new IllegalArgumentException("未知 Faker 方法：" + method);
        if (generate == null) generate = initialize();
        return MockRule.parse(generate.execute(method).asString());
    }

    private static Value initialize() {
        Context context = Context.newBuilder("js")
                .allowIO(IOAccess.newBuilder().fileSystem(new FakerModuleFiles()).build())
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        try (var stream = FakerGenerator.class.getResourceAsStream("/mock/faker-entry.mjs")) {
            // Faker 在生成内嵌图片和模拟 JWT 时需要浏览器或 Node 的 Base64 编码。
            context.getBindings("js").putMember("encodeBase64", (ProxyExecutable) args -> {
                var encoder = "base64url".equals(args[1].asString()) ? Base64.getUrlEncoder().withoutPadding() : Base64.getEncoder();
                return encoder.encodeToString(args[0].asString().getBytes(StandardCharsets.UTF_8));
            });
            context.eval("js", "globalThis.Buffer = { from: text => ({ toString: encoding => encodeBase64(text, encoding) }) }");
            Source source = Source.newBuilder("js", new String(stream.readAllBytes(), StandardCharsets.UTF_8), "faker-entry.mjs")
                    .mimeType("application/javascript+module").uri(FakerModuleFiles.ROOT.resolve("faker-entry.mjs").toUri()).build();
            context.eval(source);
            return context.getBindings("js").getMember("sampleFakerMethod");
        } catch (IOException | RuntimeException error) {
            context.close();
            throw new IllegalStateException("无法初始化 Faker 生成器", error);
        }
    }
}
