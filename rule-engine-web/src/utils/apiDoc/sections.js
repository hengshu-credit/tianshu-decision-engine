import { PLATFORM_CODES, PLATFORM_RESPONSE_CODES } from '../platformResponse'
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
  const rows = PLATFORM_RESPONSE_CODES.map(item => '<tr><td><code>' + item.code + '</code></td><td>' + escapeHtml(item.message) + '</td><td>' + item.httpStatus + '</td></tr>').join('')
  return '<section id="response-contract" class="panel"><h2>通用响应约定与码表</h2>' +
    '<p class="lead">所有接口仅以字符串 <code>000000</code> 表示成功。六位编码按领域、子类和原因分组；message 是平台固定描述，detail 是本次请求的具体原因。HTTP 状态码保持标准语义。业务通过、拒绝或复核由 data 中的业务字段决定。</p>' +
    '<p>执行结果通过 traceId 关联，revisionId 和 artifactDigest 标识实际制品。等待外数或结果未知时先查询状态，避免重复执行。日/月限额编码为预留。</p>' +
    '<div class="table-wrap"><table><thead><tr><th>平台结果码</th><th>固定描述</th><th>HTTP 状态</th></tr></thead><tbody>' + rows + '</tbody></table></div></section>'
}

function platformScenarios(rule) {
  const request = prettyJson({ clientAppName: 'api-doc-example', params: buildExampleBody(rule.requestFields || []).params || {} })
  return [{
    kind: 'PLATFORM',
    title: '200 / 执行成功',
    requestJson: request,
    responseJson: prettyJson({ code: PLATFORM_CODES.SUCCESS.code, message: PLATFORM_CODES.SUCCESS.message, data: { traceId: 'trace-example', revisionId: 12, artifactDigest: 'sha256-example', result: buildExampleBody(rule.responseFields || []).data?.result || {}, traces: [], success: true, errorMessage: null, executeTimeMs: 12 } })
  }, {
    kind: 'PLATFORM',
    title: '422 / 规则执行失败',
    requestJson: request,
    responseJson: prettyJson({ code: PLATFORM_CODES.RESULT_ERROR.code, message: PLATFORM_CODES.RESULT_ERROR.message, detail: '规则执行失败示例', data: { traceId: 'trace-example', result: null, traces: [], success: false, errorMessage: '规则执行失败示例', executeTimeMs: 8 } })
  }, {
    kind: 'PLATFORM',
    title: '401 / 鉴权失败',
    requestJson: request,
    responseJson: prettyJson({ code: PLATFORM_CODES.TOKEN_INVALID.code, message: PLATFORM_CODES.TOKEN_INVALID.message, data: null })
  }, {
    kind: 'PLATFORM',
    title: '404 / 规则不存在',
    requestJson: request,
    responseJson: prettyJson({ code: PLATFORM_CODES.RULE_NOT_FOUND.code, message: PLATFORM_CODES.RULE_NOT_FOUND.message, data: null })
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
  const path = '/api/rule/sync/execute/' + encodeURIComponent(rule.ruleCode)
  const normalFields = [{ path: 'clientAppName', type: 'STRING', required: false, label: '调用方应用名', exampleValue: 'api-doc-example' }, { path: 'traceEnabled', type: 'BOOLEAN', required: false, label: '是否采集表达式追踪，默认 true；false 不关闭服务端基础日志与审计', exampleValue: false }, ...(rule.requestFields || [])]
  const body = prettyJson({ clientAppName: 'api-doc-example', params: buildExampleBody(rule.requestFields || []).params || {} })
  const responseFields = [{ path: 'code', type: 'STRING', required: true, label: '平台响应码，仅 000000 为成功', exampleValue: PLATFORM_CODES.SUCCESS.code }, { path: 'message', type: 'STRING', required: true, label: '平台响应信息', exampleValue: PLATFORM_CODES.SUCCESS.message }, { path: 'data.success', type: 'BOOLEAN', required: true, label: '规则执行状态', exampleValue: true }, { path: 'data.revisionId', type: 'INTEGER', required: false, label: '实际执行修订 ID', exampleValue: 12 }, { path: 'data.artifactDigest', type: 'STRING', required: false, label: '实际执行制品摘要', exampleValue: 'sha256-example' }, { path: 'data.errorMessage', type: 'STRING', required: false, label: '规则执行失败原因', exampleValue: null }, ...(rule.responseFields || [])]
  const endpoint = { method: 'POST', path, baseUrl: 'https://api.example.com', body }
  const schemaNotice = rule.schemaTrust === 'UNVERIFIED'
    ? `<div class="notice warning">字段契约未通过已发布制品校验：${escapeHtml((rule.schemaDiagnostics || []).join('；') || '请重新发布后再使用此文档。')}</div>`
    : ''
  return `<section id="endpoint-${escapeAttribute(rule.ruleCode)}" class="panel endpoint-panel${active ? ' active' : ''}" data-endpoint-id="${escapeAttribute(rule.ruleCode)}" data-endpoint-value="${escapeAttribute(rule.id || rule.ruleCode)}">
    <div class="endpoint-head"><span class="method">POST</span><code class="path">${escapeHtml(path)}</code><span class="badge">${escapeHtml(rule.ruleName || rule.ruleCode)}</span></div>
    <p class="lead">${escapeHtml(rule.description || '执行已发布规则并返回统一平台响应。')}</p>${rule.openApiEnabled ? '<div class="notice">该规则同时提供普通同步执行和 OpenAPI 契约执行两种入口。</div>' : ''}${schemaNotice}
    <h3>请求头 Header</h3><div class="table-wrap"><table><thead><tr><th>名称</th><th>必填</th><th>说明</th></tr></thead><tbody><tr><td><code>Content-Type</code></td><td>是</td><td><code>application/json</code></td></tr><tr><td>鉴权字段</td><td>是</td><td>按“认证鉴权”页当前 Tab 传递</td></tr>${rule.openApiEnabled ? '<tr><td>开放接口映射 Header</td><td>按映射</td><td>契约中的 HEADER 来源字段</td></tr>' : ''}</tbody></table></div>
    ${renderFields('请求体 Body', normalFields)}
    <h3>参数结构</h3><pre><code>${escapeHtml(body)}</code></pre>
    <h3>响应头 Header</h3><div class="table-wrap"><table><thead><tr><th>名称</th><th>说明</th></tr></thead><tbody><tr><td><code>Content-Type</code></td><td><code>application/json</code></td></tr></tbody></table></div>
    ${renderFields('响应体 Body', responseFields)}
    ${renderScenarioTabs(rule)}
    <h3>代码示例</h3>${renderCodeTabs(rule, authentications || [], endpoint)}${rule.openApiEnabled ? renderOpenApiEndpoint(rule, authentications, responseFields) : ''}
  </section>`
}

function renderOpenApiEndpoint(rule, authentications, responseFields) {
  const path = '/api/rule/openapi/' + encodeURIComponent(rule.ruleCode)
  const fields = rule.openApiRequestFields || []
  const body = prettyJson(buildExampleBody(fields))
  const endpoint = { method: 'POST', path, baseUrl: 'https://api.example.com', body }
  const openFields = [
    ...responseFields.filter(field => ['code', 'message'].includes(field.path)),
    { path: 'success', type: 'BOOLEAN', required: true, label: '平台操作是否成功', exampleValue: true },
    { path: 'detail', type: 'STRING', required: false, label: '本次请求的详细原因' },
    { path: 'traceId', type: 'STRING', required: false, label: '执行追踪标识' },
    { path: 'data', type: 'JSON', required: false, label: '按已发布响应模板生成的业务数据' },
  ]
  return '<div class="open-api-documentation">' +
    '<h2>OpenAPI 契约执行入口</h2>' +
    '<div class="notice">仅在规则已启用 OpenAPI 服务时生成。该入口使用已发布契约的请求映射、响应模板、幂等和错误码。</div>' +
    '<div class="endpoint-head"><span class="method">POST</span><code class="path">' + escapeHtml(path) + '</code><span class="badge">OpenAPI</span></div>' +
    renderFields('Body 映射字段', fields) +
    '<pre><code>' + escapeHtml(body) + '</code></pre>' +
    renderFields('OpenAPI 响应体', openFields) +
    '<h3>OpenAPI 代码示例</h3>' + renderCodeTabs(rule, authentications || [], endpoint) +
    '</div>'
}
