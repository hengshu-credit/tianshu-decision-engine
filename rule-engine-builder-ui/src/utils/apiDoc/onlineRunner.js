import { renderCodeEditor } from './editor'

export function renderOnlineRunner() {
  return `<button id="runner-toggle" class="button primary runner-toggle" type="button">在线调用</button>
  <aside id="online-runner" class="runner" aria-label="在线调用">
    <div class="runner-heading"><div><div class="runner-kicker">接口调试台</div><h2>在线调用</h2><p>凭据仅保留在当前页面内存</p></div><div class="runner-heading-actions"><span id="runner-connection-state" class="runner-connection-state">浏览器直连</span><button id="runner-close" class="button secondary" type="button">关闭</button></div></div>
    <div class="runner-config-grid">
      <label class="runner-base-url-field">服务地址 / Base URL<input id="runner-base-url" value="https://api.example.com" placeholder="https://api.example.com 或 https://gateway.example.com/risk" autocomplete="off" spellcheck="false"></label>
      <label>接口<select id="runner-endpoint"></select></label>
      <label>鉴权方式<select id="runner-auth"></select></label>
      <label>超时（毫秒）<input id="runner-timeout" type="number" min="1000" max="180000" value="30000"></label>
    </div>
    <div class="runner-connection-bar"><span class="runner-connection-dot" aria-hidden="true"></span><span id="runner-connection-help">浏览器会直接请求目标地址；目标服务需要允许当前文档来源的 CORS。</span><button id="runner-use-page-origin" class="button secondary" type="button" hidden>使用当前域名</button></div>
    <div class="runner-request-bar"><span class="method">POST</span><code id="runner-path">/api/rule/sync/execute/</code></div>
    <div class="runner-actions"><button id="runner-copy-curl" class="button secondary" type="button">复制 cURL</button><button id="runner-send" class="button primary" type="button">发送请求</button><button id="runner-cancel" class="button secondary" type="button" disabled>取消请求</button></div>
    <details class="runner-auth-details"><summary>鉴权凭据 <span id="runner-auth-summary" class="muted"></span></summary><div id="runner-credentials"></div></details>
    <div class="runner-tabs tabs" data-tabs="runner-params">
      <button class="tab active" type="button" data-tab-target="runner-query-panel" data-runner-tab="query">Query</button>
      <button class="tab" type="button" data-tab-target="runner-header-panel" data-runner-tab="header">Header</button>
      <button class="tab" type="button" data-tab-target="runner-body-panel" data-runner-tab="body">Body</button>
    </div>
    <div id="runner-query-panel" class="tab-panel active">${renderCodeEditor({ id: 'runner-query', mode: 'kv', rows: 6 })}</div>
    <div id="runner-header-panel" class="tab-panel">${renderCodeEditor({ id: 'runner-headers', mode: 'kv', rows: 6 })}</div>
    <div id="runner-body-panel" class="tab-panel">
      <div class="body-type-tabs">
        <button type="button" data-body-type="none">none</button>
        <button type="button" data-body-type="form-data">form-data</button>
        <button class="active" type="button" data-body-type="json">JSON</button>
      </div>
      <div class="body-type-panel" data-body-panel="none"><div class="runner-empty">该请求不发送 Body</div></div>
      <div class="body-type-panel" data-body-panel="form-data">
        <div class="form-data-table-wrap"><table class="form-data-table"><thead><tr><th>启用</th><th>参数名</th><th>类型</th><th>值</th><th>说明</th><th></th></tr></thead><tbody id="runner-form-data-rows"></tbody></table></div>
        <button id="runner-form-data-add" class="button secondary" type="button">+ 新增参数</button>
      </div>
      <div class="body-type-panel active" data-body-panel="json">${renderCodeEditor({ id: 'runner-body', mode: 'json', value: '{}', rows: 14 })}</div>
    </div>
    <section class="runner-response">
      <div class="runner-response-title"><h3>返回结果</h3><div class="runner-response-actions"><div id="runner-response-meta" class="response-meta"></div><button id="runner-copy-response" class="button secondary" type="button">复制响应</button></div></div>
      <div id="runner-status" class="runner-empty">点击“发送”获取返回结果</div>
      <details open><summary>响应 Body</summary><pre><code id="runner-response-body">—</code></pre></details>
      <details><summary>响应 Header</summary><pre><code id="runner-response-headers">—</code></pre></details>
    </section>
  </aside>`
}

export function renderOnlineRunnerScript() {
  return `(function () {
  'use strict';
  var doc = window.__API_DOC__ || { rules: [], authentications: [] };
  var state = { endpointId: '', controller: null, timeoutMs: 30000, credentialValues: {}, bodyType: 'json', formRows: [], nextFormRowId: 1, abortReason: '' };
  var elements = {};

  function byId(id) { return document.getElementById(id); }
  function text(value) { return value == null ? '' : String(value); }
  function normalizeBaseUrl(value) {
    var raw = text(value).trim();
    if (!raw) throw new Error('请输入服务地址');
    if (!/^[a-z][a-z\\d+.-]*:\\/\\//i.test(raw)) {
      raw = /^(localhost|127(?:\\.\\d{1,3}){3}|0\\.0\\.0\\.0)(?::\\d+)?(?:\\/|$)/i.test(raw) ? 'http://' + raw : 'https://' + raw;
    }
    var parsed = new URL(raw);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('服务地址只支持 HTTP 或 HTTPS');
    return parsed;
  }
  function endpointUrl(baseValue, path) {
    var base = normalizeBaseUrl(baseValue);
    var prefix = base.pathname.replace(/\\/+$/, '');
    base.pathname = (prefix + '/' + text(path).replace(/^\\/+/, '')).replace(/\\/{2,}/g, '/');
    base.search = '';
    base.hash = '';
    return base;
  }
  function currentEndpoint() { return doc.rules.find(function (rule) { return text(rule.id || rule.ruleCode) === state.endpointId; }) || doc.rules[0]; }
  function currentAuth() { var index = Number(elements.auth.value); return doc.authentications[index] || null; }
  function endpointPath(rule) {
    const prefix = rule && rule.openApiEnabled ? '/api/rule/open/execute/' : '/api/rule/sync/execute/'
    return prefix + encodeURIComponent(rule.ruleCode)
  }
  function inputValue(name) { var input = byId('credential-' + name); return input ? input.value : ''; }
  function escapeMarkup(value) { return text(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function exampleValue(field) { return field && field.exampleValue !== undefined ? field.exampleValue : ''; }
  function formValue(value) { return value != null && typeof value === 'object' ? JSON.stringify(value) : text(value); }
  function setPath(target, path, value) {
    var parts = text(path).split('.').filter(Boolean);
    var current = target;
    parts.forEach(function (part, index) {
      if (index === parts.length - 1) current[part] = value;
      else { if (!current[part] || typeof current[part] !== 'object') current[part] = {}; current = current[part]; }
    });
  }
  function buildBody(rule) {
    var value = rule && rule.openApiEnabled ? {} : { clientAppName: 'api-doc-example', params: {} };
    (rule.requestFields || []).forEach(function (field) { setPath(value, field.path, exampleValue(field)); });
    return value;
  }
  function newFormRow(field) {
    return { id: state.nextFormRowId++, enabled: true, name: field ? field.path : '', type: 'TEXT', value: field ? formValue(exampleValue(field)) : '', description: field ? (field.label || field.description || '') : '', file: null };
  }
  function resetFormRows(rule) {
    state.formRows = [newFormRow({ path: 'clientAppName', exampleValue: 'api-doc-example', label: '调用方应用名' })].concat((rule.requestFields || []).map(newFormRow));
    renderFormRows();
  }
  function renderFormRows() {
    elements.formRows.innerHTML = state.formRows.map(function (row) {
      var valueControl = row.type === 'FILE'
        ? '<input type="file" data-form-field="file" aria-label="' + escapeMarkup(row.name || '文件参数') + '">'
        : '<input value="' + escapeMarkup(typeof row.value === 'object' ? JSON.stringify(row.value) : row.value) + '" data-form-field="value" autocomplete="off">';
      return '<tr data-form-row="' + row.id + '"><td><input type="checkbox" data-form-field="enabled" ' + (row.enabled ? 'checked' : '') + ' aria-label="启用参数"></td><td><input value="' + escapeMarkup(row.name) + '" data-form-field="name" placeholder="参数名" autocomplete="off"></td><td><select data-form-field="type"><option value="TEXT"' + (row.type === 'TEXT' ? ' selected' : '') + '>Text</option><option value="FILE"' + (row.type === 'FILE' ? ' selected' : '') + '>File</option></select></td><td>' + valueControl + '</td><td><input value="' + escapeMarkup(row.description) + '" data-form-field="description" placeholder="说明" autocomplete="off"></td><td><button class="form-row-delete" type="button" data-form-row-delete="' + row.id + '" aria-label="删除参数">×</button></td></tr>';
    }).join('');
  }
  function renderEndpoint() {
    var rule = currentEndpoint();
    if (!rule) { elements.path.textContent = '/api/rule/sync/execute/'; return; }
    state.endpointId = text(rule.id || rule.ruleCode);
    elements.path.textContent = endpointPath(rule);
    window.ApiDocEditors.set('runner-query', '', true);
    window.ApiDocEditors.set('runner-headers', '', true);
    window.ApiDocEditors.set('runner-body', JSON.stringify(buildBody(rule), null, 2), true);
    resetFormRows(rule);
  }
  function credentialField(name, label, secret) {
    return '<label for="credential-' + name + '">' + label + '</label><input id="credential-' + name + '" ' + (secret ? 'type="password" ' : '') + 'autocomplete="off" value="' + escapeMarkup(state.credentialValues[name] || '') + '">';
  }
  function rememberCredentialFields() {
    elements.credentials.querySelectorAll('input').forEach(function (input) {
      input.addEventListener('input', function () { state.credentialValues[input.id.replace('credential-', '')] = input.value; });
    });
  }
  function renderCredentials() {
    var auth = currentAuth();
    elements.authSummary.textContent = auth ? '· ' + (auth.authName || auth.authType) : '· 未配置';
    if (!auth) { elements.credentials.innerHTML = '<div class="notice">该文档没有可用鉴权配置，请联系平台管理员。</div>'; return; }
    if (auth.authType === 'LEGACY_TOKEN') elements.credentials.innerHTML = credentialField('projectToken', '项目兼容令牌', true);
    else if (auth.authType === 'BASIC') elements.credentials.innerHTML = credentialField('username', '账号', false) + credentialField('password', '密码', true);
    else if (auth.authType === 'API_KEY') elements.credentials.innerHTML = credentialField('apiKey', 'API Key（' + escapeMarkup(auth.parameterName || 'X-Rule-Api-Key') + '）', true);
    else if (auth.authType === 'HMAC_SHA256') elements.credentials.innerHTML = credentialField('accessKey', 'Access Key', false) + credentialField('hmacSecret', 'HMAC Secret', true);
    else elements.credentials.innerHTML = credentialField('accessToken', 'Bearer Token', true);
    rememberCredentialFields();
  }
  function parseRows(value) {
    return text(value).split(/\\r?\\n/).map(function (line) {
      var separator = line.indexOf('=');
      return separator < 0 ? null : { name: line.slice(0, separator).trim(), value: line.slice(separator + 1).trim() };
    }).filter(function (row) { return row && row.name; });
  }
  function shellQuote(value) { return "'" + text(value).split("'").join(String.fromCharCode(39, 34, 39, 34, 39)) + "'"; }
  async function copyText(value) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(value);
        return;
      } catch (error) {
        // file:// 页面或浏览器权限策略可能拒绝 Clipboard API，继续尝试兼容方案。
      }
    }
    var textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    var copied = document.execCommand('copy');
    document.body.removeChild(textarea);
    if (!copied) throw new Error('当前浏览器不允许访问剪贴板，请手动复制');
  }
  function curlCommand(request) {
    var command = 'curl -X POST ' + shellQuote(request.url.toString());
    request.headers.forEach(function (value, name) { command += ' \\\n  -H ' + shellQuote(name + ': ' + value); });
    if (state.bodyType === 'form-data') {
      state.formRows.forEach(function (row) {
        if (!row.enabled || !row.name) return;
        var value = row.type === 'FILE' ? '@' + (row.file ? row.file.name : 'path/to/file') : text(row.value);
        command += ' \\\n  -F ' + shellQuote(row.name + '=' + value);
      });
    } else if (state.bodyType !== 'none') {
      command += ' \\\n  --data-binary ' + shellQuote(request.prepared.body);
    }
    return command;
  }
  function connectionHelp(message, error) {
    elements.connectionHelp.textContent = message;
    elements.connectionHelp.parentElement.classList.toggle('is-error', Boolean(error));
  }
  function networkFailureMessage(error) {
    var detail = error && error.message ? ': ' + error.message : '';
    var advice = location.protocol === 'file:'
      ? ' 当前文档通过 file:// 打开，目标服务需要允许 Origin: null；也可以复制 cURL 到目标网络执行。'
      : ' 请检查目标地址、防火墙、TLS 证书和 CORS；也可以复制 cURL 到目标网络执行。';
    return '网络请求失败' + detail + '。' + advice;
  }
  function validateFormFiles() {
    var totalSize = state.formRows.reduce(function (size, row) {
      return size + (row.enabled && row.type === 'FILE' && row.file ? row.file.size : 0);
    }, 0);
    if (totalSize > 4 * 1024 * 1024) throw new Error('form-data 文件总大小不能超过 4 MB');
  }
  function buildFormData() {
    validateFormFiles();
    var formData = new FormData();
    state.formRows.forEach(function (row) {
      if (!row.enabled || !row.name) return;
      if (row.type === 'FILE') { if (row.file) formData.append(row.name, row.file, row.file.name); }
      else formData.append(row.name, text(row.value));
    });
    return formData;
  }
  function bytesToHex(value) { return Array.from(new Uint8Array(value)).map(function (item) { return item.toString(16).padStart(2, '0'); }).join(''); }
  async function applyAuthentication(auth, url, headers, method, signingBody) {
    if (!auth) return;
    if (auth.authType === 'LEGACY_TOKEN') headers.set('X-Rule-Token', inputValue('projectToken'));
    else if (auth.authType === 'BASIC') headers.set('Authorization', 'Basic ' + btoa(unescape(encodeURIComponent(inputValue('username') + ':' + inputValue('password')))));
    else if (auth.authType === 'API_KEY') {
      var keyName = auth.parameterName || 'X-Rule-Api-Key';
      if (auth.placement === 'QUERY') url.searchParams.set(keyName, inputValue('apiKey'));
      else headers.set(keyName, inputValue('apiKey'));
    } else if (auth.authType === 'HMAC_SHA256') {
      if (!window.crypto || !window.crypto.subtle) throw new Error('当前浏览器环境不支持安全的 HMAC 计算');
      var encoder = new TextEncoder();
      var timestamp = String(Math.floor(Date.now() / 1000));
      var nonceBytes = new Uint8Array(16);
      window.crypto.getRandomValues(nonceBytes);
      var nonce = bytesToHex(nonceBytes);
      var bodyBytes = typeof signingBody === 'string' ? encoder.encode(signingBody) : signingBody;
      var bodyHash = bytesToHex(await window.crypto.subtle.digest('SHA-256', bodyBytes));
      var rawQuery = url.search.length > 1 ? url.search.slice(1) : '';
      var canonical = method + '\\n' + url.pathname + '\\n' + rawQuery + '\\n' + bodyHash + '\\n' + timestamp + '\\n' + nonce;
      var key = await window.crypto.subtle.importKey('raw', encoder.encode(inputValue('hmacSecret')), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
      var signature = bytesToHex(await window.crypto.subtle.sign('HMAC', key, encoder.encode(canonical)));
      headers.set('X-Rule-Access-Key', inputValue('accessKey'));
      headers.set('X-Rule-Timestamp', timestamp);
      headers.set('X-Rule-Nonce', nonce);
      headers.set('X-Rule-Signature', signature);
    } else headers.set('Authorization', 'Bearer ' + inputValue('accessToken'));
  }
  async function prepareBody(headers, auth, url) {
    if (state.bodyType === 'none') return { body: undefined, signingBody: new Uint8Array(0) };
    if (state.bodyType === 'form-data') {
      headers.delete('Content-Type');
      var formData = buildFormData();
      if (auth && auth.authType === 'HMAC_SHA256') {
        var preparedRequest = new Request(url.toString(), { method: 'POST', body: formData });
        var buffer = await preparedRequest.arrayBuffer();
        headers.set('Content-Type', preparedRequest.headers.get('Content-Type'));
        return { body: buffer, signingBody: new Uint8Array(buffer) };
      }
      return { body: formData, signingBody: new Uint8Array(0) };
    }
    if (window.ApiDocEditors.validate('runner-body')) throw new Error('请求 Body 不是有效 JSON');
    var jsonBody = window.ApiDocEditors.get('runner-body');
    headers.set('Content-Type', 'application/json');
    return { body: jsonBody, signingBody: jsonBody };
  }
  async function prepareRequest() {
    var rule = currentEndpoint();
    if (!rule) throw new Error('当前没有可调用的接口');
    var queryError = window.ApiDocEditors.validate('runner-query');
    if (queryError) throw new Error(queryError);
    var headerError = window.ApiDocEditors.validate('runner-headers');
    if (headerError) throw new Error(headerError);
    var url = endpointUrl(elements.baseUrl.value, endpointPath(rule));
    parseRows(window.ApiDocEditors.get('runner-query')).forEach(function (row) { url.searchParams.set(row.name, row.value); });
    var headers = new Headers();
    parseRows(window.ApiDocEditors.get('runner-headers')).forEach(function (row) { headers.set(row.name, row.value); });
    var auth = currentAuth();
    var prepared = await prepareBody(headers, auth, url);
    await applyAuthentication(auth, url, headers, 'POST', prepared.signingBody);
    return { rule: rule, url: url, headers: headers, auth: auth, prepared: prepared };
  }
  function showState(message, kind) { elements.status.className = kind ? 'status ' + kind : 'runner-empty'; elements.status.textContent = message; }
  async function copyCurl() {
    try {
      var request = await prepareRequest();
      if (request.auth && request.auth.authType === 'HMAC_SHA256' && state.bodyType === 'form-data') throw new Error('HMAC-SHA256 的 form-data 依赖 multipart 边界，无法直接复用浏览器签名，请使用 Shell 示例');
      await copyText(curlCommand(request));
      connectionHelp('cURL 已复制，可在能访问目标服务的终端或网关环境执行。');
      showState('cURL 已复制', 'success');
    } catch (error) { showState(error.message || '复制 cURL 失败', 'danger'); }
  }
  async function copyResponse() {
    var body = elements.responseBody.textContent;
    if (!body || body === '—') { showState('暂无可复制的响应', 'warning'); return; }
    try {
      await copyText(body);
      showState('响应已复制', 'success');
    } catch (error) { showState(error.message || '复制响应失败', 'danger'); }
  }
  async function sendRequest() {
    var request;
    try { request = await prepareRequest(); } catch (error) { showState(error.message || '请求参数不正确', 'danger'); return; }
    if (state.controller) state.controller.abort();
    state.controller = new AbortController();
    state.abortReason = '';
    state.timeoutMs = Math.max(1000, Number(elements.timeout.value) || 30000);
    var started = performance.now();
    var timer = setTimeout(function () { state.abortReason = 'timeout'; state.controller.abort(); }, state.timeoutMs);
    elements.send.disabled = true;
    elements.cancel.disabled = false;
    showState('请求发送中…', 'warning');
    connectionHelp('正在请求 ' + request.url.origin + '，请保持当前页面打开。');
    try {
      var requestOptions = { method: 'POST', headers: request.headers, mode: 'cors', credentials: 'omit', signal: state.controller.signal };
      if (state.bodyType !== 'none') requestOptions.body = request.prepared.body;
      var response = await fetch(request.url.toString(), requestOptions);
      var responseText = await response.text();
      var responseValue;
      try { responseValue = JSON.stringify(JSON.parse(responseText), null, 2); } catch (error) { responseValue = responseText; }
      var headerLines = [];
      response.headers.forEach(function (value, name) { headerLines.push(name + ': ' + value); });
      elements.responseHeaders.textContent = headerLines.join('\\n') || '—';
      elements.responseBody.textContent = responseValue || '（空响应）';
      var duration = Math.round(performance.now() - started);
      elements.responseMeta.innerHTML = '<span class="badge">HTTP ' + response.status + '</span><span class="badge">' + duration + ' ms</span>';
      showState(response.ok ? '请求完成' : '服务端返回非成功 HTTP 状态', response.ok ? 'success' : 'danger');
      connectionHelp(response.ok ? '请求已完成；响应内容保留在当前页面。' : '服务端已返回响应，请根据 HTTP 状态和响应内容排查。', !response.ok);
    } catch (error) {
      if (error && error.name === 'AbortError') showState(state.abortReason === 'timeout' ? '请求超时' : '请求已取消', 'danger');
      else { showState(networkFailureMessage(error), 'danger'); connectionHelp(networkFailureMessage(error), true); }
    } finally {
      clearTimeout(timer);
      state.controller = null;
      elements.send.disabled = false;
      elements.cancel.disabled = true;
    }
  }
  function setBodyType(type) {
    state.bodyType = type;
    document.querySelectorAll('[data-body-type]').forEach(function (button) { button.classList.toggle('active', button.getAttribute('data-body-type') === type); });
    document.querySelectorAll('[data-body-panel]').forEach(function (panel) { panel.classList.toggle('active', panel.getAttribute('data-body-panel') === type); });
  }
  function initialize() {
    elements = { baseUrl: byId('runner-base-url'), endpoint: byId('runner-endpoint'), auth: byId('runner-auth'), authSummary: byId('runner-auth-summary'), credentials: byId('runner-credentials'), path: byId('runner-path'), timeout: byId('runner-timeout'), send: byId('runner-send'), cancel: byId('runner-cancel'), copyCurl: byId('runner-copy-curl'), copyResponse: byId('runner-copy-response'), usePageOrigin: byId('runner-use-page-origin'), connectionState: byId('runner-connection-state'), connectionHelp: byId('runner-connection-help'), formRows: byId('runner-form-data-rows'), formAdd: byId('runner-form-data-add'), status: byId('runner-status'), responseMeta: byId('runner-response-meta'), responseHeaders: byId('runner-response-headers'), responseBody: byId('runner-response-body') };
    elements.endpoint.innerHTML = doc.rules.map(function (rule) { return '<option value="' + escapeMarkup(rule.id || rule.ruleCode) + '">' + escapeMarkup(rule.ruleName || rule.ruleCode) + ' · ' + escapeMarkup(rule.ruleCode) + '</option>'; }).join('');
    elements.auth.innerHTML = doc.authentications.length ? doc.authentications.map(function (auth, index) { return '<option value="' + index + '">' + escapeMarkup(auth.authName || auth.authType) + '</option>'; }).join('') : '<option value="-1">未配置鉴权</option>';
    state.endpointId = elements.endpoint.value;
    renderEndpoint();
    renderCredentials();
    var canUsePageOrigin = location.protocol === 'http:' || location.protocol === 'https:';
    elements.usePageOrigin.hidden = !canUsePageOrigin;
    if (!canUsePageOrigin) connectionHelp('当前文档通过本地文件打开，浏览器直连目标服务需要允许 Origin: null；跨域失败时可复制 cURL。');
    elements.endpoint.addEventListener('change', function () { state.endpointId = elements.endpoint.value; renderEndpoint(); });
    elements.auth.addEventListener('change', renderCredentials);
    elements.send.addEventListener('click', sendRequest);
    elements.copyCurl.addEventListener('click', copyCurl);
    elements.copyResponse.addEventListener('click', copyResponse);
    elements.usePageOrigin.addEventListener('click', function () {
      if (!canUsePageOrigin) return;
      elements.baseUrl.value = location.origin;
      connectionHelp('已填入当前页面域名；如果接口挂在网关前缀下，请手动补充路径。');
    });
    elements.cancel.addEventListener('click', function () { if (state.controller) { state.abortReason = 'manual'; state.controller.abort(); } });
    elements.formAdd.addEventListener('click', function () { state.formRows.push(newFormRow()); renderFormRows(); });
    elements.formRows.addEventListener('input', function (event) {
      var rowElement = event.target.closest('[data-form-row]');
      if (!rowElement) return;
      var row = state.formRows.find(function (item) { return item.id === Number(rowElement.getAttribute('data-form-row')); });
      var field = event.target.getAttribute('data-form-field');
      if (!row || !field) return;
      if (field === 'enabled') row.enabled = event.target.checked;
      else if (field === 'file') row.file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
      else row[field] = event.target.value;
    });
    elements.formRows.addEventListener('change', function (event) {
      if (event.target.getAttribute('data-form-field') !== 'type') return;
      var rowElement = event.target.closest('[data-form-row]');
      var row = state.formRows.find(function (item) { return item.id === Number(rowElement.getAttribute('data-form-row')); });
      if (row) { row.type = event.target.value; row.file = null; renderFormRows(); }
    });
    elements.formRows.addEventListener('click', function (event) {
      var button = event.target.closest('[data-form-row-delete]');
      if (!button) return;
      state.formRows = state.formRows.filter(function (row) { return row.id !== Number(button.getAttribute('data-form-row-delete')); });
      renderFormRows();
    });
    document.querySelectorAll('[data-body-type]').forEach(function (button) { button.addEventListener('click', function () { setBodyType(button.getAttribute('data-body-type')); }); });
    byId('runner-toggle').addEventListener('click', function () { byId('online-runner').classList.add('open'); });
    byId('runner-close').addEventListener('click', function () { byId('online-runner').classList.remove('open'); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize); else initialize();
}());`
}
