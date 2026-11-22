package com.hengshucredit.rule.server.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/** 在首次使用时加载本地发行包。GraalJS 的同一 Context 一次只允许一个线程进入。 */
final class FakerGenerator {
    private FakerGenerator() {}
    private static Value generate;

    private static Context runtime;

    static void validateArguments(FakerMethods.Entry method, JsonNode config) {
        if (config.isTextual()) return;
        JsonNode args = config.path("args");
        if (!config.isObject() || !args.isArray() || config.size() != 2)
            throw new IllegalArgumentException("Faker 配置需要 method 和 args 参数数组");
        if (args.size() > method.parameters().size())
            throw new IllegalArgumentException(method.key() + " 的参数顺序：" + method.parameters());
    }

    static JsonNode sample(String method) {
        return sample(method, MockRule.JSON.createArrayNode());
    }

    static synchronized JsonNode sample(String method, JsonNode args) {
        if (!FakerMethods.METHODS.containsKey(method)) throw new IllegalArgumentException("未知 Faker 方法：" + method);
        if (generate == null) generate = initialize();
        try {
            runtime.resetLimits();
            String json = generate.execute(method, args.isMissingNode() ? "[]" : args.toString()).asString();
            return MockRule.parse(json);
        } catch (PolyglotException error) {
            if (error.isResourceExhausted() || error.isCancelled()) {
                runtime.close(true);
                generate = null;
                throw new IllegalArgumentException("参数生成量过大，请缩短长度或减少数量");
            }
            throw new IllegalArgumentException(method + " 参数无效：" + error.getMessage(), error);
        }
    }

    private static Value initialize() {
        Context context = Context.newBuilder("js")
                .allowIO(IOAccess.newBuilder().fileSystem(new FakerModuleFiles()).build())
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(500_000L, null).build())
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
            // 仅传递目录中的签名，用于校验嵌套回调以及模板中的方法调用。
            var signatures = MockRule.JSON.createObjectNode();
            FakerMethods.METHODS.forEach((key, method) -> signatures.set(key, MockRule.JSON.valueToTree(method.parameters())));
            context.getBindings("js").getMember("configureFakerSignatures").execute(signatures.toString());
            runtime = context;
            return context.getBindings("js").getMember("sampleFakerMethod");
        } catch (IOException | RuntimeException error) {
            context.close();
            throw new IllegalStateException("无法初始化 Faker 生成器", error);
        }
    }
}
