import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.alibaba.fastjson.JSON;
import java.nio.file.Files;
import java.nio.file.Path;

/** java -cp "sdk/lib/*" sdk/examples/CallRule.java RULE_CODE params.json */
class CallRule {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("用法：CallRule RULE_CODE params.json");
        try (RuleHttpClient client = RuleHttpClient.builder()
                .serverUrl(System.getenv("RULE_SERVER_URL"))
                .token(System.getenv("PROJECT_ACCESS_TOKEN"))
                .appName("partner-java-example")
                .traceEnabled(Boolean.parseBoolean(System.getenv("RULE_TRACE_ENABLED")))
                .build()) {
            RuleResult result = client.execute(args[0], JSON.parseObject(Files.readString(Path.of(args[1]))));
            System.out.println(JSON.toJSONString(result));
            if (!result.isSuccess()) System.exit(2);
        }
    }
}
