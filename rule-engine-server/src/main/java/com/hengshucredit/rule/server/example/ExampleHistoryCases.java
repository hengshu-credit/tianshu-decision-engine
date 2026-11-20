package com.hengshucredit.rule.server.example;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

/** Explicitly prepares real application history for two manual test cases, using the public contract. */
final class ExampleHistoryCases {
    static void prepare() throws Exception {
        var keys = CanonicalJson.readMap(Files.readAllBytes(Path.of("/state/installation.json")));
        var uri = URI.create(System.getenv().getOrDefault("EXAMPLE_ENGINE_URL", "http://server:8080")
                .replaceAll("/+$","") + "/api/rule/openapi/CREDIT_ADMISSION");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        for (boolean device : List.of(true, false)) {
            for (int i = 0; i < (device ? 3 : 11); i++) {
                String id = "DEMO-HIST-" + (device ? "DEVICE-" : "GPS-") + day + "-" + i;
                var params = Map.of("request_id", id, "device_id", device ? "DEMO-HISTORY-DEVICE" : id,
                        "gps_latitude", device ? -39.2 : -37.33, "gps_longitude", device ? 151.6 : 147.32,
                        "name","合成申请人", "idcard_no","110105199002030718", "mobile_no","13862097542",
                        "idcard_expiry_date","长期", "contact",Map.of("mobile_no","13900002104"));
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                        .header("X-Auth-Code","CREDIT_HTTP").header("X-Rule-Api-Key",(String)keys.get("credit_api"))
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(CanonicalJson.writeBytes(params))).build();
                var response = client.send(request,HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode()!=200 || !(CanonicalJson.readMap(response.body()).get("data") instanceof Map<?,?> data)
                        || !"1".equals(data.get("result"))) throw new IllegalStateException("历史测试进件未得到预期拒绝：" + id);
            }
        }
        System.out.println("两组历史进件准备完成，可在 CREDIT_ADMISSION 的规则测试中执行 QN0005、QN0006");
    }
}
