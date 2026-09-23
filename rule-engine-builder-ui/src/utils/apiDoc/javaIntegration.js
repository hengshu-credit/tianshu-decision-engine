import { escapeHtml } from './escape'

function authYaml(auth) {
  switch (auth.authType) {
    case 'BASIC':
      return '    auth-type: BASIC\n    username: ${RULE_USERNAME}\n    password: ${RULE_PASSWORD}'
    case 'API_KEY':
      return `    auth-type: API_KEY
    api-key: \${RULE_API_KEY}
    api-key-placement: ${auth.placement === 'QUERY' ? 'QUERY' : 'HEADER'}
    api-key-parameter-name: ${JSON.stringify(auth.parameterName || 'X-Rule-Api-Key')}`
    case 'HMAC_SHA256':
      return '    auth-type: HMAC_SHA256\n    access-key: ${RULE_ACCESS_KEY}\n    hmac-secret: ${RULE_HMAC_SECRET}'
    default:
      return '    token: ${PROJECT_ACCESS_TOKEN}'
  }
}

export function renderJavaIntegration(doc) {
  const project = doc.project || {}
  const ruleCode = doc.rules?.[0]?.ruleCode || '<已发布规则编码>'
  const authentications = doc.authentications?.length ? doc.authentications : [{ authType: 'LEGACY_TOKEN' }]
  const dependency = `<dependency>
    <groupId>com.hengshucredit.rule</groupId>
    <artifactId>rule-engine-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>`
  const configurations = authentications.map(auth => {
    const yaml = `rule-engine:
  client:
    server-url: \${RULE_SERVER_URL}
    app-name: risk-service
    project-id: ${project.id || '<PROJECT_ID>'}
    project-code: ${JSON.stringify(project.projectCode || '<PROJECT_CODE>')}
${authYaml(auth)}
    server-side-execution: true
    http-timeout-ms: 10000
    trace-enabled: false
    log-report-enabled: false
spring:
  data:
    redis:
      host: \${REDIS_HOST}
      port: \${REDIS_PORT:6379}
      password: \${REDIS_PASSWORD}
      database: \${REDIS_DATABASE:0}`
    return `<h3>${escapeHtml(auth.authName || auth.authType)} 配置</h3><pre><code>${escapeHtml(yaml)}</code></pre>`
  }).join('')
  const controller = `import com.hengshucredit.rule.client.RuleEngineClient;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/decision")
public class DecisionController {
    private final RuleEngineClient ruleClient;

    public DecisionController(RuleEngineClient ruleClient) {
        this.ruleClient = ruleClient;
    }

    @PostMapping
    public ResponseEntity<RuleResult> decide(@RequestBody Map<String, Object> params) {
        RuleResult result = ruleClient.execute(${JSON.stringify(ruleCode)}, params);
        return ResponseEntity.status(result.isSuccess() ? 200 : 422).body(result);
    }
}`
  return `<section id="java-integration" class="panel">
    <h2>Java 服务接入</h2>
    <p class="lead">业务端 → Java 服务 → 决策引擎 → Java 服务 → 业务端。项目凭据只保存在 Java 服务中，不下发浏览器或移动端。</p>
    <p>项目凭据只能访问本项目规则及已关联的全局规则。全局规则需要先在项目中关联；跨项目接入使用各项目独立的凭据与客户端实例，不能通过请求参数切换权限。只执行已发布规则。</p>
    <h3>1. 引入 SDK</h3>
    <p>使用 JDK 17 和 Spring Boot 3，在已有 Spring Boot Web 服务中添加以下依赖。SDK 尚未发布公共仓库，先在引擎源码根目录执行 <code>mvn clean install -DskipTests</code>，或由团队发布到内部 Maven 仓库。</p>
    <pre><code>${escapeHtml(dependency)}</code></pre>
    <h3>2. 配置 application.yml</h3>
    <p>从以下鉴权配置中选择一种，环境变量由部署环境注入。Redis 必须与引擎使用同一实例、密码和 database。project-id / project-code 应与凭据所属项目一致；app-name 仅标识调用服务。</p>
    ${configurations}
    <p><code>server-side-execution: true</code> 在引擎服务端执行，适用于 API、数据库、名单、模型等依赖。设为 false 时在 Java 服务本地执行缓存脚本，需自行提供本地函数与运行依赖；不会自动执行服务端变量取数流程。超时应覆盖完整规则耗时。</p>
    <h3>3. 暴露业务接口</h3>
    <p>以下类放入业务服务的 Spring 扫描包中。业务端 POST /api/decision，请求体直接使用本文档 params 内的字段。业务服务仍需配置自己的登录鉴权、参数校验与规则白名单。</p>
    <pre><code>${escapeHtml(controller)}</code></pre>
    <p>SDK 返回 RuleResult，不含引擎 HTTP 响应的外层 code / message / data。success 表示执行成功，不代表业务通过；实际通过、拒绝或复核由 result 的业务字段决定。鉴权或网络异常需由业务服务统一映射，不能默认为通过。</p>
    <h3>4. 日志与追踪选项</h3>
    <p><code>trace-enabled</code> 默认 true，控制本地及服务端执行的表达式追踪；false 时不返回表达式 traces，仍保留 traceId。直接调用引擎 HTTP 接口使用请求体顶层 <code>traceEnabled: false</code>，省略时默认 true。</p>
    <p><code>log-report-enabled</code> 默认 true，仅控制 SDK 本地执行日志上报；false 时 HTTP、Kafka 和自定义 reporter 均不接收执行日志。服务端执行不重复上报，服务端审计、基础执行日志、外数日志与计费不受该开关影响。两项均为 false 不等于关闭所有平台日志。</p>
    <p>以上配置针对完整 SDK <code>rule-engine-client</code>。对外公司无需源码或 Maven 私服时，使用源码 <code>rule-engine-example/README.md</code> 中的离线 tar.gz 交付方式；当前默认示例使用独立的 <code>rule-engine-client-http</code>，不需要 Redis、不下载规则，配置 RULE_SERVER_URL、项目凭据及 RULE_ALLOWED_CODES 后启动，RULE_TRACE_ENABLED 默认 false。</p>
    <p>示例接口为 <code>POST /api/example/execute</code>，请求体为 <code>{"ruleCode":"已发布规则编码","params":{}}</code>。该通用接口仅供联调，生产业务服务应接入自身鉴权并校验允许执行的规则。</p>
  </section>`
}
