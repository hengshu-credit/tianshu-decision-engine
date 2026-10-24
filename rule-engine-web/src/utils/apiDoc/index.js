import { responseCodes } from './responseCodes'
import { escapeHtml, serializeForScript } from './escape'
import { buildExampleBody, normalizeApiDoc } from './model'
import { renderOnlineRunner, renderOnlineRunnerScript } from './onlineRunner'
import { renderLayoutScript, renderResizeHandles } from './layout'
import { renderCodeEditorScript } from './editor'
import { buildEndpointScenarios, renderAuthentication, renderResponseContract, renderRuleEndpoint } from './sections'
import { apiDocStyles } from './styles'
import { renderJavaIntegration } from './javaIntegration'
import {
  createPrimaryScale,
  mixThemeColors,
  normalizeThemeConfig,
  resolveThemeAccent,
} from '../../theme/themeConfig'

function renderThemeStyles(theme) {
  const normalized = normalizeThemeConfig(theme)
  const accent = resolveThemeAccent(normalized)
  const scale = createPrimaryScale(accent.primary)
  const dark = normalized.colorScheme === 'DARK'
  const surface = dark ? '#151D31' : '#FFFFFF'
  const surfaceSoft = dark ? '#1F2A40' : '#F9FAFB'
  const background = dark ? '#101828' : '#F6F8FB'
  const text = dark ? '#F2F4F7' : '#172033'
  const muted = dark ? '#A7B2C5' : '#667085'
  const border = dark ? '#33415F' : '#DFE5EF'
  const danger = dark ? '#F97066' : '#B42318'
  const dangerSoft = dark ? '#3A1F28' : '#FEF3F2'
  const success = dark ? '#6EE7B7' : '#067647'
  const successSoft = dark ? '#14382F' : '#ECFDF3'
  const warning = dark ? '#FEC84B' : '#B54708'
  const warningSoft = dark ? '#3B2C13' : '#FFFAEB'
  const code = dark ? '#0B1220' : '#111827'
  const runner = surface
  const runnerBorder = border
  const brandSoft = mixThemeColors(accent.primary, dark ? surface : '#FFFFFF', dark ? 0.85 : 0.9)
  const brandText = dark ? mixThemeColors(accent.primary, '#FFFFFF', 0.45) : accent.primary
  const gradient = accent.kind === 'gradient' ? accent.background : 'none'
  return `<style>:root{color-scheme:${dark ? 'dark' : 'light'};--brand:${accent.primary};--brand-strong:${scale.dark2};--brand-soft:${brandSoft};--brand-text:${brandText};--bg:${background};--surface:${surface};--surface-soft:${surfaceSoft};--text:${text};--muted:${muted};--border:${border};--danger:${danger};--danger-soft:${dangerSoft};--success:${success};--success-soft:${successSoft};--warning:${warning};--warning-soft:${warningSoft};--code:${code};--runner:${runner};--runner-border:${runnerBorder};--brand-foreground:${accent.foreground};--brand-gradient:${gradient}}.button.primary{background:var(--brand);background-image:var(--brand-gradient);color:var(--brand-foreground)}</style>`
}

function renderOverview(doc) {
  return `<section id="overview" class="panel overview-panel">
    <div class="section-kicker">开发者文档 / API REFERENCE</div>
    <h1>${escapeHtml(doc.project.projectName || 'API 接口文档')}</h1>
    <p class="lead"><code>${escapeHtml(doc.project.projectCode)}</code></p>
    <p>${escapeHtml(doc.project.description || '本文档描述项目已发布规则的调用方式。')}</p>
    <div class="notice">本文档中的全部凭据与参数值均为样例；生产环境请以平台单独提供的账号密码、Token、API Key 或 HMAC 密钥为准。</div>
    <div class="overview-metrics"><span><strong>${doc.rules.length}</strong> 已发布规则</span><span><strong>${doc.authentications.length}</strong> 鉴权方式</span><span><strong>JSON</strong> 请求与响应</span></div>
    <h2>快速接入</h2>
    <ol class="quick-start"><li><strong>确认服务地址</strong><span>将示例域名替换为实际部署地址，保留网关路径前缀。</span></li><li><strong>配置鉴权与参数</strong><span>从左侧选择接口，按<a href="#authentication">认证鉴权</a>配置凭据，并填写 Body。</span></li><li><strong>调用并核对结果</strong><span>通过 success 判断接口状态，再读取 data 中的业务结果；排查时记录 traceId。</span></li></ol>
    <div class="overview-actions"><button class="button primary" type="button" data-export-openapi>导出 OpenAPI 3.1</button><span class="muted">可直接导入 Postman 或 Apifox 进行接口管理</span></div>
  </section>`
}

function renderNavigation(doc, logoSvg, brand) {
  const endpointLinks = doc.rules.map((rule, index) => `<button class="nav-link endpoint-link${index === 0 ? ' active' : ''}" type="button" data-endpoint-nav="${escapeHtml(rule.id || rule.ruleCode)}" title="${escapeHtml(rule.ruleName || rule.ruleCode)} · ${escapeHtml(rule.ruleCode)}"><span class="nav-method">POST</span><span class="nav-endpoint-text"><span>${escapeHtml(rule.ruleName || rule.ruleCode)}</span><code>${escapeHtml(rule.ruleCode)}</code></span></button>`).join('')
  return `<nav class="nav" aria-label="文档目录">
  <div class="brand">
    <div class="brand-header-title">
      <div class="brand-logo">${logoSvg}</div>
      <div class="brand-text"><span class="brand-main-text">${escapeHtml(brand.systemName)}</span><span class="brand-sub-text">${escapeHtml(brand.subtitle)}</span></div>
    </div>
    <div class="brand-project"><strong>${escapeHtml(doc.project.projectName || '未命名项目')}</strong><code>${escapeHtml(doc.project.projectCode || '-')}</code></div>
  </div>
  <div class="nav-group"><div class="nav-title">接入说明</div><a class="nav-link" href="#overview">基础项目信息</a><a class="nav-link" href="#response-contract">通用响应约定与码表</a><a class="nav-link" href="#authentication">认证鉴权</a><a class="nav-link" href="#java-integration">Java 服务接入</a></div>
    <div class="nav-group"><div class="nav-title">API 接口 <span>${doc.rules.length}</span></div><label class="nav-search"><span class="sr-only">搜索接口名称或编码</span><input id="endpoint-search" type="search" placeholder="搜索名称或编码" autocomplete="off"></label>${endpointLinks || '<div class="empty">暂无已发布接口</div>'}<p id="endpoint-search-empty" class="muted" hidden>没有匹配的接口</p></div>
  </nav>`
}

function renderInteractionScript() {
  return `(function () {
  var search = document.getElementById('endpoint-search');
  search.addEventListener('input', function () {
    var query = search.value.trim().toLowerCase();
    var count = 0;
    document.querySelectorAll('[data-endpoint-nav]').forEach(function (link) {
      link.hidden = !link.textContent.toLowerCase().includes(query);
      if (!link.hidden) count++;
    });
    document.getElementById('endpoint-search-empty').hidden = count > 0 || !query;
  });
  document.addEventListener('click', function (event) {
    var exportOpenApi = event.target.closest('[data-export-openapi]');
    if (exportOpenApi) {
      var openApi = window.__OPEN_API__;
      if (!openApi) return;
      var objectUrl = URL.createObjectURL(new Blob([JSON.stringify(openApi, null, 2)], { type: 'application/json;charset=utf-8' }));
      var anchor = document.createElement('a');
      var project = window.__API_DOC__ && window.__API_DOC__.project || {};
      var projectCode = String(project.projectCode || 'api').replace(/[\\/:*?"<>|]/g, '_');
      anchor.href = objectUrl;
      anchor.download = projectCode + '-openapi.json';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      window.setTimeout(function () { URL.revokeObjectURL(objectUrl); }, 0);
      return;
    }
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
  const brand = options.brand || { systemName: '天枢决策引擎', subtitle: '天工开物，枢衡定策' }
  let logoSvg = String(options.logoSvg || '').trim()
  const png = /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(logoSvg)
  const imageUrl = /^https?:\/\//.test(logoSvg)
  if (imageUrl) {
    const url = new URL(logoSvg)
    if (url.username || url.password) throw new Error('Logo 地址不能包含登录凭据')
  }
  if (!/^<svg(?:\s|>)/i.test(logoSvg) && !png && !imageUrl) {
    throw new Error('加载 hengshucredit Logo 失败：内容不是 SVG')
  }
  if (png || imageUrl) logoSvg = `<img src="${escapeHtml(logoSvg)}" alt="${escapeHtml(brand.systemName)}" style="width:40px;height:40px;object-fit:contain">`
  const normalized = normalizeApiDoc(doc)
  const openApiDocument = generateOpenApiDocument(doc)
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
  ${renderThemeStyles(options.theme)}
</head>
<body>
  <div class="app">
    ${renderNavigation(normalized, logoSvg, brand)}
    ${resizeHandles.nav}
    <main class="content">
      ${renderOverview(normalized)}
      ${renderResponseContract(normalized.codeMapping)}
      ${renderAuthentication(normalized)}
      ${renderJavaIntegration(normalized)}
      ${endpointPanels || '<section class="panel empty">当前项目暂无可导出的已发布规则。</section>'}
    </main>
    ${resizeHandles.runner}
    ${renderOnlineRunner()}
  </div>
  <script>window.__API_DOC__=${serializeForScript(normalized)};window.__OPEN_API__=${serializeForScript(openApiDocument)};</script>
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
      if (field.label || field.description) schema.description = [field.label, field.description].filter(Boolean).join('；')
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
    else if (type === 'BEARER_TOKEN') schemes[name] = { type: 'http', scheme: 'bearer' }
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
    const path = `/api/rule/sync/execute/${encodeURIComponent(rule.ruleCode)}`
    const schemaNote = rule.schemaTrust === 'UNVERIFIED'
      ? `字段契约未通过已发布制品校验：${(rule.schemaDiagnostics || []).join('；') || '请重新发布后再使用此文档。'}`
      : ''
    paths[path] = { post: {
      operationId: `execute_${rule.ruleCode}`,
      summary: rule.ruleName || rule.ruleCode,
      description: [rule.description || '执行已发布规则并返回统一平台响应。', schemaNote].filter(Boolean).join('\n\n'),
      'x-rule-schema-trust': rule.schemaTrust || 'UNKNOWN',
      'x-rule-schema-diagnostics': rule.schemaDiagnostics || [],
      'x-open-api-contract-enabled': false,
      'x-open-api-contract': undefined,
      security: security.alternatives,
      requestBody: { required: true, content: { 'application/json': { example: { clientAppName: 'api-doc-example', params: buildExampleBody(rule.requestFields).params || {} }, schema: { type: 'object', properties: {
            clientAppName: { type: 'string', description: '调用方应用名' }, traceEnabled: { type: 'boolean', default: true, description: '是否采集表达式追踪；false 不关闭服务端基础日志与审计' }, params: fieldsSchema(rule.requestFields, 'params')
          }, required: ['params'] }
      } } },
      responses: {
        '200': { description: '规则执行完成', content: { 'application/json': { schema: {
          type: 'object', properties: { code: { type: 'string', pattern: '^[0-9]{6}$', example: '000000' }, message: { type: 'string', example: '成功' }, detail: { type: ['string', 'null'] }, success: { type: 'boolean' }, traceId: { type: ['string', 'null'] }, data: { type: 'object', properties: {
            success: { type: 'boolean' }, traceId: { type: 'string' }, revisionId: { type: 'integer' }, artifactDigest: { type: 'string' }, errorMessage: { type: ['string', 'null'] }, executeTimeMs: { type: 'integer', description: '规则执行耗时（毫秒）' }, traces: { type: 'array', items: {} }, result: fieldsSchema(rule.responseFields, 'data.result')
          } } }
        } } } },
        '400': { description: '请求参数或规则执行失败' }, '401': { description: '鉴权失败' }, '404': { description: '规则不存在' }, '409': { description: '幂等请求冲突或执行中' }, '429': { description: '限流或并发超过限制' }
      }
    } }
    const schema = paths[path].post.responses['200'].content['application/json'].schema
    for (const httpStatus of new Set(responseCodes(normalized.codeMapping).map(item => item.httpStatus))) {
      const codes = responseCodes(normalized.codeMapping).filter(item => item.httpStatus === httpStatus)
      const responseSchema = {
        ...schema,
        properties: {
          ...schema.properties,
          code: { type: 'string', pattern: normalized.codeMapping?.enabled ? '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$' : '^[0-9]{6}$', enum: [...new Set(codes.map(item => item.code))], example: codes[0].code },
          message: { type: 'string', example: codes[0].message },
          success: { type: 'boolean', example: httpStatus === 200 },
          data: httpStatus === 200 ? schema.properties.data : {},
        },
      }
      paths[path].post.responses[httpStatus] = {
        description: codes.map(item => item.code + ' ' + item.message).join('；'),
        content: { 'application/json': { schema: responseSchema } },
      }
    }
    buildEndpointScenarios(rule).filter(item => item.kind === 'PLATFORM').forEach(item => {
      const response = paths[path].post.responses[item.title.split(' / ')[0]]
      if (response) response.content['application/json'].example = JSON.parse(item.responseJson)
    })
    if (rule.openApiEnabled) {
      const openPath = `/api/rule/openapi/${encodeURIComponent(rule.ruleCode)}`
      const openOperation = JSON.parse(JSON.stringify(paths[path].post))
      openOperation.operationId = `execute_openapi_${rule.ruleCode}`
      openOperation['x-open-api-contract-enabled'] = true
      openOperation['x-open-api-contract'] = rule.openApiContract
      openOperation.requestBody.content['application/json'].schema = fieldsSchema(rule.openApiRequestFields || [], '')
      openOperation.requestBody.content['application/json'].example = buildExampleBody(rule.openApiRequestFields || [])
      // 开放接口 data 由发布模板决定，不能复用同步执行 DTO 的内部字段。
      for (const response of Object.values(openOperation.responses)) {
        response.content['application/json'].schema.properties.data = {}
        delete response.content['application/json'].example
      }
      paths[openPath] = { post: openOperation }
    }
  })
  return {
    openapi: '3.1.0',
    info: { title: normalized.project.projectName || normalized.project.projectCode || '天枢决策 API', description: normalized.project.description || '天枢决策引擎已发布规则接口', version: 'published' },
    servers: [{ url: 'https://api.example.com' }], paths,
    components: { securitySchemes: security.schemes }
  }
}

export { normalizeApiDoc } from './model'
