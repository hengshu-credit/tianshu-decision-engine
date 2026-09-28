import { escapeHtml, serializeForScript } from './escape'
import { normalizeApiDoc } from './model'
import { renderOnlineRunner, renderOnlineRunnerScript } from './onlineRunner'
import { renderLayoutScript, renderResizeHandles } from './layout'
import { renderCodeEditorScript } from './editor'
import { renderAuthentication, renderResponseContract, renderRuleEndpoint } from './sections'
import { apiDocStyles } from './styles'
import { renderJavaIntegration } from './javaIntegration'

function renderOverview(doc) {
  return `<section id="overview" class="panel">
    <h1>${escapeHtml(doc.project.projectName || 'API 接口文档')}</h1>
    <p class="lead"><code>${escapeHtml(doc.project.projectCode)}</code></p>
    <p>${escapeHtml(doc.project.description || '本文档描述项目已发布规则的调用方式。')}</p>
    <div class="notice">本文档中的全部凭据与参数值均为样例；生产环境请以平台单独提供的账号密码、Token、API Key 或 HMAC 密钥为准。</div>
  </section>`
}

function renderNavigation(doc, logoSvg) {
  const endpointLinks = doc.rules.map((rule, index) => `<button class="nav-link${index === 0 ? ' active' : ''}" type="button" data-endpoint-nav="${escapeHtml(rule.id || rule.ruleCode)}">${escapeHtml(rule.ruleName || rule.ruleCode)}</button>`).join('')
  return `<nav class="nav">
  <div class="brand">
    <div class="brand-header-title">
      <div class="brand-logo">${logoSvg}</div>
      <div class="brand-text"><span class="brand-main-text">天枢决策引擎</span><span class="brand-sub-text">天工开物, 枢衡定策</span></div>
    </div>
    <div class="brand-project"><strong>${escapeHtml(doc.project.projectName || '未命名项目')}</strong><code>${escapeHtml(doc.project.projectCode || '-')}</code></div>
  </div>
  <div class="nav-group"><div class="nav-title">接入说明</div><a class="nav-link" href="#overview">基础项目信息</a><a class="nav-link" href="#response-contract">通用响应约定与码表</a><a class="nav-link" href="#authentication">认证鉴权</a><a class="nav-link" href="#java-integration">Java 服务接入</a></div>
    <div class="nav-group"><div class="nav-title">API 接口</div>${endpointLinks || '<div class="empty">暂无已发布接口</div>'}</div>
  </nav>`
}

function renderInteractionScript() {
  return `(function () {
  document.addEventListener('click', function (event) {
    var fieldToggle = event.target.closest('[data-field-toggle]');
    if (fieldToggle) {
      var fieldId = fieldToggle.getAttribute('data-field-toggle');
      var expanded = fieldToggle.getAttribute('aria-expanded') !== 'false';
      fieldToggle.setAttribute('aria-expanded', expanded ? 'false' : 'true');
      fieldToggle.setAttribute('aria-label', (expanded ? '展开 ' : '折叠 ') + fieldId);
      var table = fieldToggle.closest('table');
      if (table) {
        table.querySelectorAll('[data-field-row]').forEach(function (row) {
          var parentId = row.getAttribute('data-parent-id');
          var hidden = false;
          while (parentId) {
            var parentToggle = table.querySelector('[data-field-toggle="' + CSS.escape(parentId) + '"]');
            if (parentToggle && parentToggle.getAttribute('aria-expanded') === 'false') { hidden = true; break; }
            var parentRow = table.querySelector('[data-field-row="' + CSS.escape(parentId) + '"]');
            parentId = parentRow ? parentRow.getAttribute('data-parent-id') : '';
          }
          row.hidden = hidden;
        });
      }
      return;
    }
    var button = event.target.closest('[data-tab-target]');
    if (!button) return;
    var group = button.closest('[data-tabs]');
    if (!group) return;
    group.querySelectorAll('[data-tab-target]').forEach(function (item) {
      item.classList.toggle('active', item === button);
      var panel = document.getElementById(item.getAttribute('data-tab-target'));
      if (panel) panel.classList.toggle('active', item === button);
    });
  });
  var links = Array.from(document.querySelectorAll('.nav-link'));
  links.forEach(function (link) {
    link.addEventListener('click', function () { links.forEach(function (item) { item.classList.remove('active'); }); link.classList.add('active'); });
  });
}());`
}

export function generateApiDocHtml(doc, options = {}) {
  const logoSvg = String(options.logoSvg || '').trim()
  if (!/^<svg(?:\s|>)/i.test(logoSvg)) {
    throw new Error('加载 hengshucredit Logo 失败：内容不是 SVG')
  }
  const normalized = normalizeApiDoc(doc)
  const endpointPanels = normalized.rules.map((rule, index) => renderRuleEndpoint(rule, normalized.authentications, index === 0)).join('')
  const resizeHandles = renderResizeHandles()
  const title = `${normalized.project.projectName || normalized.project.projectCode || '项目'} API 文档`
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <title>${escapeHtml(title)}</title>
  <style>${apiDocStyles}</style>
</head>
<body>
  <div class="app">
    ${renderNavigation(normalized, logoSvg)}
    ${resizeHandles.nav}
    <main class="content">
      ${renderOverview(normalized)}
      ${renderResponseContract()}
      ${renderAuthentication(normalized)}
      ${renderJavaIntegration(normalized)}
      ${endpointPanels || '<section class="panel empty">当前项目暂无可导出的已发布规则。</section>'}
    </main>
    ${resizeHandles.runner}
    ${renderOnlineRunner()}
  </div>
  <script>window.__API_DOC__=${serializeForScript(normalized)};</script>
  <script>${renderInteractionScript()}\n${renderCodeEditorScript()}\n${renderOnlineRunnerScript()}\n${renderLayoutScript()}</script>
</body>
</html>`
}

function openApiType(value) {
  const type = String(value || 'STRING').toUpperCase()
  if (['INTEGER', 'INT', 'LONG'].includes(type)) return { type: 'integer' }
  if (['NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].includes(type)) return { type: 'number' }
  if (['BOOLEAN', 'BOOL'].includes(type)) return { type: 'boolean' }
  if (['ARRAY', 'LIST', 'VECTOR'].includes(type)) return { type: 'array', items: {} }
  if (['OBJECT', 'MAP'].includes(type)) return { type: 'object' }
  return { type: 'string' }
}

function setOpenApiField(root, field, prefix = '') {
  const rawPath = String(field && field.path || '')
  const path = (prefix && rawPath.startsWith(`${prefix}.`) ? rawPath.slice(prefix.length + 1) : rawPath).split('.').filter(Boolean)
  if (!path.length) return
  let current = root
  path.forEach((part, index) => {
    if (index === path.length - 1) {
      current.properties = current.properties || {}
      const schema = openApiType(field.type)
      if (field.label) schema.description = field.label
      if (field.exampleValue != null && field.exampleValue !== '') schema.example = field.exampleValue
      current.properties[part] = schema
      if (field.required) current.required = Array.from(new Set([...(current.required || []), part]))
      return
    }
    current.properties = current.properties || {}
    current.properties[part] = current.properties[part] || { type: 'object', properties: {} }
    current = current.properties[part]
  })
}

function fieldsSchema(fields, prefix = '') {
  const schema = { type: 'object', properties: {} }
  ;(fields || []).forEach(field => setOpenApiField(schema, field, prefix))
  return schema
}

function openApiSecurity(authentications) {
  const schemes = {}
  const alternatives = []
  ;(authentications || []).forEach((auth, index) => {
    const name = `projectAuth${index + 1}`
    const type = String(auth.authType || '').toUpperCase()
    if (type === 'BASIC') schemes[name] = { type: 'http', scheme: 'basic' }
    else if (type === 'API_KEY') schemes[name] = { type: 'apiKey', in: auth.placement === 'QUERY' ? 'query' : 'header', name: auth.parameterName || 'X-Rule-Api-Key' }
    else if (type === 'HMAC_SHA256') schemes[name] = {
      type: 'apiKey',
      in: 'header',
      name: 'X-Rule-Access-Key',
      description: 'HMAC-SHA256 需要同时发送 X-Rule-Access-Key、X-Rule-Timestamp、X-Rule-Nonce、X-Rule-Signature；签名覆盖请求方法、路径、原始 Query、Body SHA-256、时间戳和 nonce。'
    }
    else schemes[name] = { type: 'apiKey', in: 'header', name: 'X-Rule-Token' }
    alternatives.push({ [name]: [] })
  })
  return { schemes, alternatives }
}

/** Generate an importable OpenAPI 3.1 document from the same normalized doc as the HTML export. */
export function generateOpenApiDocument(doc) {
  const normalized = normalizeApiDoc(doc)
  const security = openApiSecurity(normalized.authentications)
  const paths = {}
  normalized.rules.forEach(rule => {
    const pathPrefix = rule.openApiEnabled ? '/api/rule/open/execute/' : '/api/rule/sync/execute/'
    const path = `${pathPrefix}${encodeURIComponent(rule.ruleCode)}`
    const schemaNote = rule.schemaTrust === 'UNVERIFIED'
      ? `字段契约未通过已发布制品校验：${(rule.schemaDiagnostics || []).join('；') || '请重新发布后再使用此文档。'}`
      : ''
    const requestSchema = fieldsSchema(rule.requestFields, '')
    paths[path] = { post: {
      operationId: `execute_${rule.ruleCode}`,
      summary: rule.ruleName || rule.ruleCode,
      description: [rule.description || '执行已发布规则并返回统一平台响应。', schemaNote].filter(Boolean).join('\n\n'),
      'x-rule-schema-trust': rule.schemaTrust || 'UNKNOWN',
      'x-rule-schema-diagnostics': rule.schemaDiagnostics || [],
      'x-open-api-contract-enabled': rule.openApiEnabled,
      'x-open-api-contract': rule.openApiEnabled ? rule.openApiContract : undefined,
      security: security.alternatives,
      requestBody: { required: true, content: { 'application/json': { schema: rule.openApiEnabled
        ? requestSchema
        : { type: 'object', properties: {
            clientAppName: { type: 'string' }, traceEnabled: { type: 'boolean', default: true }, params: fieldsSchema(rule.requestFields, 'params')
          }, required: ['params'] }
      } } },
      responses: {
        '200': { description: '规则执行完成', content: { 'application/json': { schema: {
          type: 'object', properties: { code: { type: 'integer', example: 200 }, message: { type: 'string' }, data: { type: 'object', properties: {
            success: { type: 'boolean' }, traceId: { type: 'string' }, revisionId: { type: 'integer' }, artifactDigest: { type: 'string' }, result: fieldsSchema(rule.responseFields, 'data.result')
          } } }
        } } } },
        '400': { description: '请求参数或规则执行失败' }, '401': { description: '鉴权失败' }, '404': { description: '规则不存在' }, '409': { description: '幂等请求冲突或执行中' }, '429': { description: '限流或并发超过限制' }
      }
    } }
  })
  return {
    openapi: '3.1.0',
    info: { title: normalized.project.projectName || normalized.project.projectCode || '天枢决策 API', description: normalized.project.description || '天枢决策引擎已发布规则接口', version: 'published' },
    servers: [{ url: 'https://api.example.com' }], paths,
    components: { securitySchemes: security.schemes }
  }
}

export { normalizeApiDoc } from './model'
