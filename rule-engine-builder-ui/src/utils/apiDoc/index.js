import { escapeHtml, serializeForScript } from './escape'
import { normalizeApiDoc } from './model'
import { renderOnlineRunner, renderOnlineRunnerScript } from './onlineRunner'
import { renderAuthentication, renderResponseContract, renderRuleEndpoint } from './sections'
import { apiDocStyles } from './styles'

function renderOverview(doc) {
  return `<section class="panel">
    <h1>${escapeHtml(doc.project.projectName || 'API 接口文档')}</h1>
    <p class="lead"><code>${escapeHtml(doc.project.projectCode)}</code></p>
    <p>${escapeHtml(doc.project.description || '本文档描述项目已发布规则的调用方式。')}</p>
    <div class="notice">本文档中的全部凭据与参数值均为样例；生产环境请以平台单独提供的账号密码、Token、API Key 或 HMAC 密钥为准。</div>
  </section>`
}

function renderNavigation(doc, logoSvg) {
  const endpointLinks = doc.rules.map(rule => `<a class="nav-link" href="#endpoint-${escapeHtml(rule.ruleCode)}">${escapeHtml(rule.ruleName || rule.ruleCode)}</a>`).join('')
  return `<nav class="nav">
    <div class="brand">${logoSvg}<div><div class="project-title">天枢决策引擎</div><div class="project-code">${escapeHtml(doc.project.projectCode)}</div></div></div>
    <div class="nav-group"><div class="nav-title">接入说明</div><a class="nav-link" href="#authentication">认证鉴权</a><a class="nav-link" href="#response-contract">通用响应约定与码表</a></div>
    <div class="nav-group"><div class="nav-title">API 接口</div>${endpointLinks || '<div class="empty">暂无已发布接口</div>'}</div>
  </nav>`
}

function renderInteractionScript() {
  return `(function () {
  document.addEventListener('click', function (event) {
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
  const endpointPanels = normalized.rules.map(rule => renderRuleEndpoint(rule, normalized.authentications)).join('')
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
    <main class="content">
      ${renderOverview(normalized)}
      ${renderResponseContract()}
      ${renderAuthentication(normalized)}
      ${endpointPanels || '<section class="panel empty">当前项目暂无可导出的已发布规则。</section>'}
    </main>
    ${renderOnlineRunner()}
  </div>
  <script>window.__API_DOC__=${serializeForScript(normalized)};</script>
  <script>${renderInteractionScript()}\n${renderOnlineRunnerScript()}</script>
</body>
</html>`
}

export { normalizeApiDoc } from './model'
