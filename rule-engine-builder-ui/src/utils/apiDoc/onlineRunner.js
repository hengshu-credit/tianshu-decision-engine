import { renderCodeEditor } from './editor'

export function renderOnlineRunner() {
  return `<button id="runner-toggle" class="button primary runner-toggle" type="button">在线调用</button>
  <aside id="online-runner" class="runner" aria-label="在线调用">
    <div class="runner-heading"><div><h2>在线调用</h2><p>凭据仅保留在当前页面内存</p></div><button id="runner-close" class="button secondary" type="button">关闭</button></div>
    <div class="runner-config-grid">
      <label>Base URL / 环境地址<input id="runner-base-url" value="https://api.example.com" autocomplete="off" spellcheck="false"></label>
      <label>接口<select id="runner-endpoint"></select></label>
      <label>鉴权方式<select id="runner-auth"></select></label>
      <label>超时（毫秒）<input id="runner-timeout" type="number" min="1000" max="180000" value="30000"></label>
    </div>
    <div class="runner-request-bar"><span class="method">POST</span><code id="runner-path">/api/rule/sync/execute/</code><button id="runner-send" class="button primary" type="button">发送</button><button id="runner-cancel" class="button secondary" type="button" disabled>取消请求</button></div>
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
      <div class="runner-response-title"><h3>返回结果</h3><div id="runner-response-meta" class="response-meta"></div></div>
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
  function currentEndpoint() { return doc.rules.find(function (rule) { return text(rule.id || rule.ruleCode) === state.endpointId; }) || doc.rules[0]; }
  function currentAuth() { var index = Number(elements.auth.value); return doc.authentications[index] || null; }
  function endpointPath(rule) { return '/api/rule/sync/execute/' + encodeURIComponent(rule.ruleCode); }
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
    var value = { clientAppName: 'api-doc-example', params: {} };
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
  function showState(message, kind) { elements.status.className = kind ? 'status ' + kind : 'runner-empty'; elements.status.textContent = message; }
  async function sendRequest() {
    var rule = currentEndpoint();
    if (!rule) return;
    if (window.ApiDocEditors.validate('runner-query')) { showState(window.ApiDocEditors.validate('runner-query'), 'danger'); return; }
    if (window.ApiDocEditors.validate('runner-headers')) { showState(window.ApiDocEditors.validate('runner-headers'), 'danger'); return; }
    var url;
    try { url = new URL(endpointPath(rule), elements.baseUrl.value.trim()); } catch (error) { showState('Base URL 格式不正确', 'danger'); return; }
    parseRows(window.ApiDocEditors.get('runner-query')).forEach(function (row) { url.searchParams.set(row.name, row.value); });
    var headers = new Headers();
    parseRows(window.ApiDocEditors.get('runner-headers')).forEach(function (row) { headers.set(row.name, row.value); });
    var auth = currentAuth();
    var prepared;
    try {
      prepared = await prepareBody(headers, auth, url);
      await applyAuthentication(auth, url, headers, 'POST', prepared.signingBody);
    } catch (error) { showState(error.message, 'danger'); return; }
    if (state.controller) state.controller.abort();
    state.controller = new AbortController();
    state.abortReason = '';
    state.timeoutMs = Math.max(1000, Number(elements.timeout.value) || 30000);
    var started = performance.now();
    var timer = setTimeout(function () { state.abortReason = 'timeout'; state.controller.abort(); }, state.timeoutMs);
    elements.send.disabled = true;
    elements.cancel.disabled = false;
    showState('请求发送中…', 'warning');
    try {
      var requestOptions = { method: 'POST', headers: headers, signal: state.controller.signal };
      if (state.bodyType !== 'none') requestOptions.body = prepared.body;
      var response = await fetch(url.toString(), requestOptions);
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
    } catch (error) {
      if (error && error.name === 'AbortError') showState(state.abortReason === 'timeout' ? '请求超时' : '请求已取消', 'danger');
      else showState('网络连接失败或被跨域策略阻止：' + (error && error.message ? error.message : '未知错误'), 'danger');
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
    elements = { baseUrl: byId('runner-base-url'), endpoint: byId('runner-endpoint'), auth: byId('runner-auth'), authSummary: byId('runner-auth-summary'), credentials: byId('runner-credentials'), path: byId('runner-path'), timeout: byId('runner-timeout'), send: byId('runner-send'), cancel: byId('runner-cancel'), formRows: byId('runner-form-data-rows'), formAdd: byId('runner-form-data-add'), status: byId('runner-status'), responseMeta: byId('runner-response-meta'), responseHeaders: byId('runner-response-headers'), responseBody: byId('runner-response-body') };
    elements.endpoint.innerHTML = doc.rules.map(function (rule) { return '<option value="' + escapeMarkup(rule.id || rule.ruleCode) + '">' + escapeMarkup(rule.ruleName || rule.ruleCode) + ' · ' + escapeMarkup(rule.ruleCode) + '</option>'; }).join('');
    elements.auth.innerHTML = doc.authentications.length ? doc.authentications.map(function (auth, index) { return '<option value="' + index + '">' + escapeMarkup(auth.authName || auth.authType) + '</option>'; }).join('') : '<option value="-1">未配置鉴权</option>';
    state.endpointId = elements.endpoint.value;
    renderEndpoint();
    renderCredentials();
    elements.endpoint.addEventListener('change', function () { state.endpointId = elements.endpoint.value; renderEndpoint(); });
    elements.auth.addEventListener('change', renderCredentials);
    elements.send.addEventListener('click', sendRequest);
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
