# 天枢 Java HTTP 接入示例与离线交付包

适用场景：其他业务系统或公司开发 Java 服务，业务端请求该 Java 服务，再由服务调用天枢已发布规则并返回结果。

默认示例使用 `rule-engine-client-http`。不需要 Redis/MySQL，不调用规则同步接口，不含本地执行引擎；规则、变量取数、函数及模型在引擎服务端执行。原来的本地缓存、税率/评分等演示保留在源码的 `legacy/`，不进入对外交付包。

## 一、发布方构建 tar 包

在引擎源码根目录运行（需要 JDK 17、Maven、Node.js 和 tar）：

```text
node scripts/package-java-offline.mjs
```

脚本会构建并测试 SDK 和示例，按白名单收集文件，检查没有带入引擎核心/JPMML，生成：

```text
output/java-offline/tianshu-java-1.0.0-SNAPSHOT-plain-<时间>.tar.gz
output/java-offline/tianshu-java-1.0.0-SNAPSHOT-plain-<时间>.tar.gz.sha256
```

打包机首次构建需要下载 Maven 依赖。交付包可以离线启动、离线重新编译示例，**运行决策仍需通过网络访问引擎**。不包含 JDK，客户需预装 JDK 17。当前为普通包 `plain`，不包含机器绑定/软件到期许可证，也不承诺防反编译。

包内容：

```text
example/rule-engine-example.jar   可直接启动的业务服务
example/lib/                     示例离线编译与运行依赖
example-src/src/main/            可修改的 Java Controller、Service、配置和联调页
example-src/pom.xml              普通 Maven 接入项目参考
sdk/lib/                        HTTP SDK、DTO 和全部运行依赖
sdk/poms/                       SDK、DTO 和父 POM，供导入客户私服
sdk/examples/CallRule.java        无 Spring 的 Java 调用示例
tools/BuildExample.java           仅需 JDK 的离线编译工具
config/example.properties.template
start.ps1 / start.sh
README.md / NOTICE.md / LICENSE / licenses/
dependencies.txt / release.json / SHA256SUMS
```

只有示例源码随包交付，不需要引擎源码。不要把真实 `.env`、客户凭据、私钥或数据库导出放进交付物。服务端使用 JPMML 时仍须单独遵循引擎的开源交付义务。

## 二、客户解压、配置、启动（无需 Maven）

校验 tar 的 SHA-256 后解压。在解压目录中，把 `config/example.properties.template` 复制为 `config/example.properties`，填写：

```properties
RULE_SERVER_URL=https://你的引擎域名
RULE_CLIENT_APP_NAME=partner-risk-service
RULE_ALLOWED_CODES=实际已发布规则编码,另一个规则编码
RULE_AUTH_TYPE=LEGACY_TOKEN
PROJECT_ACCESS_TOKEN=平台提供的项目凭据
RULE_HTTP_TIMEOUT_MS=10000
RULE_TRACE_ENABLED=false
```

也可以使用同名环境变量，环境变量优先。端口默认 7070，`EXAMPLE_PORT` 可修改；默认监听 127.0.0.1，仅供本机联调。不要在源码或浏览器中保存引擎凭据。

Windows PowerShell：

```powershell
.\start.ps1
```

Linux/macOS：

```sh
sh start.sh
```

访问 `http://127.0.0.1:7070/`，选择白名单规则，按项目导出的 API 文档填写 `params`。停止时按 Ctrl+C。`/actuator/health` 仅反映本服务健康，不代表项目凭据或每个规则都可执行；验收应使用一组已知规则入参实际调用。

## 三、业务端调用示例服务

```http
POST /api/example/execute
Content-Type: application/json

{"ruleCode":"实际已发布规则编码","params":{"age":18}}
```

响应为 RuleResult，没有引擎 HTTP 的外层 code/message/data：

```json
{
  "success": true,
  "result": {"decision": "REJECT"},
  "traceId": "由引擎生成",
  "traces": null,
  "errorMessage": null,
  "executeTimeMs": 12
}
```

字段值仅为示意。`success=true` 表示执行成功，不代表批准，实际决策取 `result` 中的业务字段。原始字段名称、中文、大小写及嵌套结构均按规则文档提供，不自动改名。

| 业务服务 HTTP 状态 | 含义 |
| --- | --- |
| 200 | 引擎执行成功；result 仍可能是拒绝或人工复核 |
| 400 | ruleCode/params 无效或不在业务服务白名单 |
| 422 | 平台受理成功，但规则执行失败，查看 errorMessage |
| 502 | 引擎网络、超时、鉴权、404 等平台错误，查看 upstreamHttpStatus / platformCode |

客户端不自动重试决策请求，不跟随重定向。超时后实际执行状态可能未知，不能默认放行或直接重试计费/外部操作。生产必须结合业务流水号和引擎日志制定补偿、幂等及重试策略。

示例不是生产网关：对外暴露之前，应接入业务方自己的登录鉴权、TLS、参数校验、限流、隐私脱敏和错误处理。修改 `EXAMPLE_BIND_ADDRESS=0.0.0.0` 只改变监听地址，不增加这些安全能力。

## 四、嵌入客户自己的 Java 服务

无 Maven 方式：将 `sdk/lib/` 中的 JAR 加入应用 classpath；不要把 `example/lib/` 的整套 Spring 运行库强行加入已有 Spring 应用，以免版本冲突。

```java
import com.hengshucredit.rule.client.http.RuleHttpClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import java.util.Map;

// 创建一次并复用；应用退出时 close，不要每次请求重新建客户端。
RuleHttpClient client = RuleHttpClient.builder()
        .serverUrl(System.getenv("RULE_SERVER_URL"))
        .token(System.getenv("PROJECT_ACCESS_TOKEN"))
        .appName("partner-risk-service")
        .timeoutMs(10000)
        .traceEnabled(false)
        .build();
RuleResult result = client.execute("实际已发布规则编码", Map.of("age", 18));
if (!result.isSuccess()) {
    throw new IllegalStateException(result.getErrorMessage());
}
// result.getResult() 才是业务决策；也支持传 Java DTO。
```

无 Spring 的可运行示例（环境变量中配置地址、项目 Token；params.json 为 JSON 对象）：

```text
java -cp "sdk/lib/*" sdk/examples/CallRule.java RULE_CODE params.json
```

Maven 方式：把 `sdk/poms/` 的父 POM、DTO POM/JAR、HTTP SDK POM/JAR 上传到客户私服，或使用 `mvn install:install-file -Dfile=... -DpomFile=...` 安装到本地仓库，再参考 `example-src/pom.xml`。Maven 自身插件和公共依赖仍需已缓存或可访问仓库；该方式不作为完全离线重编译保证。客户无需获取引擎源码或访问发布方私服。

## 五、完全离线修改并重新编译示例

在解压目录修改 `example-src/src/main/`，然后：

```text
java tools/BuildExample.java
```

该工具使用 JDK 的 javac 和随包 `example/lib`，不启动 Maven、不下载依赖。生成 `build/example.jar`。Windows 使用 `./start.ps1 -Rebuilt`，Linux 使用 `sh start.sh --rebuilt`。默认 start 仍运行发布方预编译包，避免误用旧的重编译产物。

## 六、项目、全局规则和鉴权

- 一个客户端使用一个项目的凭据；凭据决定权限，不接受业务端通过 projectId/projectCode 任意切换项目。
- 项目规则必须已发布；全局规则需先关联到调用项目。未授权或未发布的规则不可调用。
- 多项目使用独立 RuleHttpClient 实例；在业务服务内部按可信配置路由，勿由终端用户提供引擎凭据。
- 本 SDK 不访问同步接口。但现有引擎凭据可能同时有同步能力，如需严格禁止外部客户获取规则，必须由 API 网关/服务端限制接口，仅放行 Token 与执行接口；仅“不提供下载代码”不构成权限隔离。

| RULE_AUTH_TYPE | 相关配置 |
| --- | --- |
| LEGACY_TOKEN | PROJECT_ACCESS_TOKEN |
| BASIC | RULE_USERNAME、RULE_PASSWORD |
| API_KEY | RULE_API_KEY、RULE_API_KEY_NAME（默认 X-Rule-Api-Key）、RULE_API_KEY_PLACEMENT（HEADER/QUERY） |
| HMAC_SHA256 | RULE_ACCESS_KEY、RULE_HMAC_SECRET |

非旧令牌方式默认换取临时 Bearer Token 并复用/提前刷新；`RULE_TOKEN_EXCHANGE_ENABLED=false` 使用直接鉴权。编程方式使用 `ClientAuthConfig.basic(...)` / `apiKey(...)` / `hmac(...)` 配合 `.authConfig(...)`。生产使用 HTTPS，不通过客户端关闭证书校验。

`RULE_TRACE_ENABLED` 控制表达式追踪，默认 false；编程调用可通过 `execute(code, params, true)` 单次开启而不影响并发请求。服务端基础执行日志、鉴权审计、外数日志和计费仍保留。本 HTTP SDK 没有本地执行，因此不需要本地日志上报开关。追踪可能包含敏感输入、规则表达式及模型结构，避免直接返回生产终端；严格保密场景必须由服务端限制追踪权限，不能仅依靠 SDK 默认关闭。

## 七、源码仓库开发

根目录 `mvn -pl rule-engine-example -am install`，再进入 `rule-engine-example`，配置上述环境变量后 `mvn spring-boot:run`。

旧的本地执行演示：根目录先 `mvn -pl rule-engine-client -am install`，再 `mvn -f rule-engine-example/legacy/pom.xml spring-boot:run`。需要 Redis 和原预置示例规则，不属于对外离线包。不要为了演示重放会覆盖现有业务数据的 SQL 快照。
