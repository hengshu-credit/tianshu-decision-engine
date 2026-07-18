import { escapeAttribute, escapeHtml } from './escape'
import { buildExampleBody } from './model'
import { EXAMPLE_CREDENTIALS, generateCodeSamples } from './codeSamples'
import { renderFieldTreeRows } from './fieldTree'

function prettyJson(value) {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch (e) {
      return value
    }
  }
  return JSON.stringify(value, null, 2)
}

function authLabel(type) {
  return {
    LEGACY_TOKEN: '项目兼容令牌',
    BASIC: '账号密码（Basic）',
    API_KEY: 'API Key',
    HMAC_SHA256: 'HMAC-SHA256',
    BEARER_TOKEN: '临时 Bearer Token'
  }[type] || type || '鉴权'
}

function duration(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value)) return '以项目配置为准'
  if (value % 3600 === 0) return `${value / 3600} 小时（${value} 秒）`
  if (value % 60 === 0) return `${value / 60} 分钟（${value} 秒）`
  return `${value} 秒`
}

function authInstructions(auth) {
  const type = auth.authType
  if (type === 'LEGACY_TOKEN') {
    return `<p>在业务请求头中传递项目兼容令牌：</p><pre><code>X-Rule-Token: ${escapeHtml(EXAMPLE_CREDENTIALS.LEGACY_TOKEN)}</code></pre>`
  }
  if (type === 'BASIC') {
    return `<p>使用 HTTP Basic 传递账号密码，可直接调用业务接口，也可调用 <code>POST /api/rule/auth/token</code> 换取临时 Token：</p><pre><code>Authorization: Basic BASE64(${escapeHtml(EXAMPLE_CREDENTIALS.BASIC_USERNAME)}:${escapeHtml(EXAMPLE_CREDENTIALS.BASIC_PASSWORD)})</code></pre><p>换取后使用：</p><pre><code>Authorization: Bearer ${escapeHtml(EXAMPLE_CREDENTIALS.BEARER_TOKEN)}</code></pre>`
  }
  if (type === 'API_KEY') {
    const name = auth.parameterName || 'X-Rule-Api-Key'
    const transfer = auth.placement === 'QUERY'
      ? `${escapeHtml(name)}=${escapeHtml(EXAMPLE_CREDENTIALS.API_KEY)}`
      : `${escapeHtml(name)}: ${escapeHtml(EXAMPLE_CREDENTIALS.API_KEY)}`
    return `<p>在已配置的 ${auth.placement === 'QUERY' ? 'Query 参数' : '请求头'}中传递：</p><pre><code>${transfer}</code></pre>`
  }
  if (type === 'HMAC_SHA256') {
    return `<p>请求必须同时传递 Access Key、Unix 秒级时间戳、随机 Nonce 和十六进制小写签名：</p><pre><code>X-Rule-Access-Key: ${escapeHtml(EXAMPLE_CREDENTIALS.ACCESS_KEY)}
X-Rule-Timestamp: &lt;UNIX_SECONDS&gt;
X-Rule-Nonce: &lt;UNIQUE_NONCE&gt;
X-Rule-Signature: &lt;LOWERCASE_HEX_HMAC&gt;</code></pre><p>使用 <code>${escapeHtml(EXAMPLE_CREDENTIALS.HMAC_SECRET)}</code> 对以下规范串按 UTF-8 执行 HMAC-SHA256：</p><pre><code>METHOD
REQUEST_URI
RAW_QUERY
SHA256_HEX(BODY)
TIMESTAMP
NONCE</code></pre><p>RAW_QUERY 必须使用请求实际发送的原始查询串；BODY 哈希必须基于实际发送的 UTF-8 字节。</p>`
  }
  return `<p>使用 Bearer Token：</p><pre><code>Authorization: Bearer ${escapeHtml(EXAMPLE_CREDENTIALS.BEARER_TOKEN)}</code></pre>`
}

export function renderAuthentication(doc) {
  const authentications = Array.isArray(doc && doc.authentications) ? doc.authentications : []
  const tabs = authentications.map((auth, index) => `<button class="tab${index === 0 ? ' active' : ''}" type="button" data-tab-target="auth-${index}">${escapeHtml(auth.authName || authLabel(auth.authType))}</button>`).join('')
  const panels = authentications.map((auth, index) => `<div id="auth-${index}" class="tab-panel${index === 0 ? ' active' : ''}">
    <h3>${escapeHtml(authLabel(auth.authType))}</h3>
    ${authInstructions(auth)}
    <p class="muted">临时 Token 有效期：${escapeHtml(duration(auth.tokenTtlSeconds))}；冗余有效时间：${escapeHtml(duration(auth.tokenGraceSeconds))}。</p>
  </div>`).join('')
  const content = authentications.length
    ? `<div class="tabs" data-tabs="authentication">${tabs}</div>${panels}`
    : '<div class="empty">当前项目未启用可导出的鉴权方式，请联系平台管理员获取接入方式。</div>'
  return `<section id="authentication" class="panel">
    <h1>认证鉴权</h1>
    <p class="lead">以下内容根据项目当前启用的鉴权方式生成，描述的是实际传递位置与签名规则。</p>
    <div class="notice"><strong>安全提示：</strong>文档中的账号、密码、Token、Access Key 与密钥均仅为示例占位值；生产环境请以平台单独提供的账号密码、Token 或 Key 为准。</div>
    ${content}
  </section>`
}

export function renderResponseContract() {
  return `<section id="response-contract" class="panel">
    <h2>通用响应约定与码表</h2>
    <p class="lead">外层 <code>code</code> 表示平台调用状态；<code>data.success</code> 表示规则是否执行成功。规则结果内的字段由具体规则定义，不是业务决策 code，除非文档场景中已单独配置。</p>
    <div class="table-wrap"><table><thead><tr><th>字段 / code</th><th>含义</th><th>调用方处理建议</th></tr></thead><tbody>
      <tr><td><code>code = 200</code></td><td>平台已受理请求；继续检查 <code>data.success</code></td><td>success=true 时读取 result；false 时读取 errorMessage</td></tr>
      <tr><td><code>code = 401</code></td><td>凭据缺失、失效或签名校验失败</td><td>核对鉴权方式、时间戳和生产凭据</td></tr>
      <tr><td><code>code = 404</code></td><td>项目范围内不存在已发布的规则编码</td><td>核对 ruleCode 与发布状态</td></tr>
      <tr><td><code>code = 429</code></td><td>临时 Token 申请触发安全限流</td><td>降低重试频率并采用退避策略</td></tr>
      <tr><td><code>data.success = true</code></td><td>规则执行成功</td><td>按接口字段表读取 <code>data.result</code></td></tr>
      <tr><td><code>data.success = false</code></td><td>规则执行失败</td><td>记录 traceId 与 errorMessage 后排查</td></tr>
    </tbody></table></div>
  </section>`
}

function platformScenarios(rule) {
  const request = prettyJson({ clientAppName: 'api-doc-example', params: buildExampleBody(rule.requestFields || []).params || {} })
  return [{
    kind: 'PLATFORM',
    title: '200 / 执行成功',
    requestJson: request,
    responseJson: prettyJson({ code: 200, message: 'success', data: { traceId: 'trace-example', result: buildExampleBody(rule.responseFields || []).data?.result || {}, traces: [], success: true, errorMessage: null, executeTimeMs: 12 } })
  }, {
    kind: 'PLATFORM',
    title: '200 / 规则执行失败',
    requestJson: request,
    responseJson: prettyJson({ code: 200, message: 'success', data: { traceId: 'trace-example', result: null, traces: [], success: false, errorMessage: '规则执行失败示例', executeTimeMs: 8 } })
  }, {
    kind: 'PLATFORM',
    title: '401 / 鉴权失败',
    requestJson: request,
    responseJson: prettyJson({ code: 401, message: 'Unauthorized project credential', data: null })
  }, {
    kind: 'PLATFORM',
    title: '404 / 规则不存在',
    requestJson: request,
    responseJson: prettyJson({ code: 404, message: 'Rule not found', data: null })
  }]
}

export function buildEndpointScenarios(rule) {
  const currentRule = rule || {}
  const business = (Array.isArray(currentRule.scenarios) ? currentRule.scenarios : []).map(item => {
    const titleParts = [item.outerCode == null ? 200 : item.outerCode, item.scenarioName]
    if (item.businessCode) titleParts.push(item.businessCode)
    return {
      kind: 'BUSINESS',
      title: titleParts.join(' / '),
      description: item.description || '',
      requestJson: item.requestJson,
      responseJson: item.responseJson,
      businessCodePath: item.businessCodePath || ''
    }
  })
  return [...platformScenarios(currentRule), ...business]
}

function renderFields(title, fields) {
  const rows = renderFieldTreeRows(fields)
  return `<h3>${escapeHtml(title)}</h3><div class="table-wrap"><table class="field-tree"><thead><tr><th>字段名称</th><th>类型</th><th>必填</th><th>说明</th><th>示例值</th></tr></thead><tbody>${rows || '<tr><td colspan="5" class="muted">无字段</td></tr>'}</tbody></table></div>`
}

function renderScenarioTabs(rule) {
  const scenarios = buildEndpointScenarios(rule)
  const tabs = scenarios.map((item, index) => `<button class="tab${index === 0 ? ' active' : ''}" type="button" data-tab-target="scenario-${escapeAttribute(rule.ruleCode)}-${index}">${escapeHtml(item.title)}</button>`).join('')
  const panels = scenarios.map((item, index) => `<div id="scenario-${escapeAttribute(rule.ruleCode)}-${index}" class="tab-panel${index === 0 ? ' active' : ''}">${item.description ? `<p>${escapeHtml(item.description)}</p>` : ''}${item.businessCodePath ? `<p class="muted">业务 code 路径：<code>${escapeHtml(item.businessCodePath)}</code></p>` : ''}<h3>输入样例</h3><pre><code>${escapeHtml(prettyJson(item.requestJson))}</code></pre><h3>输出样例</h3><pre><code>${escapeHtml(prettyJson(item.responseJson))}</code></pre></div>`).join('')
  return `<h3>请求与响应样例</h3><div class="tabs" data-tabs="scenarios-${escapeAttribute(rule.ruleCode)}">${tabs}</div>${panels}`
}

function renderCodeTabs(rule, authentications, endpoint) {
  const auths = authentications.length ? authentications : [{ authName: '兼容令牌示例', authType: 'LEGACY_TOKEN' }]
  return auths.map((auth, authIndex) => {
    const samples = generateCodeSamples(endpoint, auth)
    const entries = Object.entries(samples)
    const labels = { shell: 'Shell', java: 'Java', python: 'Python', javascript: 'JavaScript', go: 'Go' }
    const tabs = entries.map(([language], index) => `<button class="tab${index === 0 ? ' active' : ''}" type="button" data-tab-target="code-${escapeAttribute(rule.ruleCode)}-${authIndex}-${language}">${labels[language]}</button>`).join('')
    const panels = entries.map(([language, code], index) => `<div id="code-${escapeAttribute(rule.ruleCode)}-${authIndex}-${language}" class="tab-panel${index === 0 ? ' active' : ''}"><pre><code>${escapeHtml(code)}</code></pre></div>`).join('')
    return `<h3>${escapeHtml(auth.authName || authLabel(auth.authType))}</h3><div class="tabs" data-tabs="code-${escapeAttribute(rule.ruleCode)}-${authIndex}">${tabs}</div>${panels}`
  }).join('')
}

export function renderRuleEndpoint(rule, authentications, active = false) {
  const path = `/api/rule/sync/execute/${encodeURIComponent(rule.ruleCode)}`
  const body = prettyJson({ clientAppName: 'api-doc-example', params: buildExampleBody(rule.requestFields || []).params || {} })
  const responseFields = [{ path: 'code', type: 'INTEGER', required: true, label: '平台响应码', exampleValue: 200 }, { path: 'message', type: 'STRING', required: true, label: '平台响应信息', exampleValue: 'success' }, { path: 'data.success', type: 'BOOLEAN', required: true, label: '规则执行状态', exampleValue: true }, { path: 'data.errorMessage', type: 'STRING', required: false, label: '规则执行失败原因', exampleValue: null }, ...(rule.responseFields || [])]
  const endpoint = { method: 'POST', path, baseUrl: 'https://api.example.com', body }
  return `<section id="endpoint-${escapeAttribute(rule.ruleCode)}" class="panel endpoint-panel${active ? ' active' : ''}" data-endpoint-id="${escapeAttribute(rule.ruleCode)}" data-endpoint-value="${escapeAttribute(rule.id || rule.ruleCode)}">
    <div class="endpoint-head"><span class="method">POST</span><code class="path">${escapeHtml(path)}</code><span class="badge">${escapeHtml(rule.ruleName || rule.ruleCode)}</span></div>
    <p class="lead">${escapeHtml(rule.description || '执行已发布规则并返回统一平台响应。')}</p>
    <h3>请求头 Header</h3><div class="table-wrap"><table><thead><tr><th>名称</th><th>必填</th><th>说明</th></tr></thead><tbody><tr><td><code>Content-Type</code></td><td>是</td><td><code>application/json</code></td></tr><tr><td>鉴权字段</td><td>是</td><td>按“认证鉴权”页当前 Tab 传递</td></tr></tbody></table></div>
    ${renderFields('请求体 Body', [{ path: 'clientAppName', type: 'STRING', required: false, label: '调用方应用名', exampleValue: 'api-doc-example' }, ...(rule.requestFields || [])])}
    <h3>参数结构</h3><pre><code>${escapeHtml(body)}</code></pre>
    <h3>响应头 Header</h3><div class="table-wrap"><table><thead><tr><th>名称</th><th>说明</th></tr></thead><tbody><tr><td><code>Content-Type</code></td><td><code>application/json</code></td></tr></tbody></table></div>
    ${renderFields('响应体 Body', responseFields)}
    ${renderScenarioTabs(rule)}
    <h3>代码示例</h3>${renderCodeTabs(rule, authentications || [], endpoint)}
  </section>`
}
