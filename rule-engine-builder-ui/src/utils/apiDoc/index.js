import { escapeHtml, serializeForScript } from './escape'
import { normalizeApiDoc } from './model'
import { renderOnlineRunner, renderOnlineRunnerScript } from './onlineRunner'
import { renderLayoutScript, renderResizeHandles } from './layout'
import { renderCodeEditorScript } from './editor'
import { renderAuthentication, renderResponseContract, renderRuleEndpoint } from './sections'
import { apiDocStyles } from './styles'

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
  <div class="nav-group"><div class="nav-title">接入说明</div><a class="nav-link" href="#overview">基础项目信息</a><a class="nav-link" href="#response-contract">通用响应约定与码表</a><a class="nav-link" href="#authentication">认证鉴权</a></div>
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

export { normalizeApiDoc } from './model'
