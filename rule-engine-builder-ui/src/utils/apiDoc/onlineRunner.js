export function renderOnlineRunner() {
  return `<button id="runner-toggle" class="button primary runner-toggle" type="button">在线调用</button>
  <aside id="online-runner" class="runner" aria-label="在线调用">
    <div class="runner-row"><h2>在线调用</h2><button id="runner-close" class="button secondary" type="button">关闭</button></div>
    <p class="muted">凭据只保留在当前页面内存中，刷新或关闭页面后自动清除。</p>
    <label for="runner-base-url">Base URL / 环境地址</label>
    <input id="runner-base-url" value="https://api.example.com" autocomplete="off" spellcheck="false">
    <label for="runner-endpoint">接口</label>
    <select id="runner-endpoint"></select>
    <label for="runner-auth">鉴权方式</label>
    <select id="runner-auth"></select>
    <div id="runner-credentials"></div>
    <h3>请求参数表单</h3>
    <div id="runner-param-form"></div>
    <label for="runner-query">Query（每行 name=value）</label>
    <textarea id="runner-query" rows="3" placeholder="page=1"></textarea>
    <label for="runner-headers">Header（每行 name=value）</label>
    <textarea id="runner-headers" rows="4" placeholder="X-Request-Id=example"></textarea>
    <label for="runner-body">请求 Body（JSON）</label>
    <textarea id="runner-body" spellcheck="false"></textarea>
    <label for="runner-timeout">超时时间（毫秒）</label>
    <input id="runner-timeout" type="number" min="1000" max="180000" value="30000">
    <div class="runner-actions">
      <button id="runner-send" class="button primary" type="button">发送</button>
      <button id="runner-cancel" class="button secondary" type="button" disabled>取消请求</button>
    </div>
    <h3>返回结果</h3>
    <div id="runner-status" class="empty">输入参数并点击“发送”获取返回结果</div>
    <div id="runner-response-meta" class="response-meta"></div>
    <label>响应 Header</label><pre><code id="runner-response-headers">—</code></pre>
    <label>响应 Body</label><pre><code id="runner-response-body">—</code></pre>
  </aside>`
}

export function renderOnlineRunnerScript() {
  return `(function () {
  'use strict';
  var doc = window.__API_DOC__ || { rules: [], authentications: [] };
  var state = { endpointId: '', controller: null, timeoutMs: 30000, credentialValues: {}, queryRows: [], headerRows: [], bodyText: '', abortReason: '' };
  var elements = {};

  function byId(id) { return document.getElementById(id); }
  function text(value) { return value == null ? '' : String(value); }
  function currentEndpoint() { return doc.rules.find(function (rule) { return text(rule.id || rule.ruleCode) === state.endpointId; }) || doc.rules[0]; }
  function currentAuth() { var index = Number(elements.auth.value); return doc.authentications[index] || null; }
  function endpointPath(rule) { return '/api/rule/sync/execute/' + encodeURIComponent(rule.ruleCode); }
  function inputValue(name) { var input = byId('credential-' + name); return input ? input.value : ''; }
  function escapeMarkup(value) { return text(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function exampleValue(field) { return field && field.exampleValue !== undefined ? field.exampleValue : ''; }
  function setPath(target, path, value) {
    var parts = text(path).split('.').filter(Boolean);
    var current = target;
    parts.forEach(function (part, index) {
      if (index === parts.length - 1) current[part] = value;
      else { if (!current[part] || typeof current[part] !== 'object') current[part] = {}; current = current[part]; }
    });
  }
  function parseInput(field, value) {
    var type = text(field.type).toUpperCase();
    if (['INTEGER', 'INT', 'LONG', 'NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].indexOf(type) >= 0) return value === '' ? null : Number(value);
    if (['BOOLEAN', 'BOOL'].indexOf(type) >= 0) return value === 'true';
    if (['ARRAY', 'LIST', 'VECTOR', 'OBJECT', 'MAP'].indexOf(type) >= 0) { try { return JSON.parse(value); } catch (error) { return value; } }
    return value;
  }
  function buildBody(rule) {
    var value = { clientAppName: 'api-doc-example', params: {} };
    (rule.requestFields || []).forEach(function (field) { setPath(value, field.path, exampleValue(field)); });
    return value;
  }
  function renderEndpoint() {
    var rule = currentEndpoint();
    if (!rule) { elements.body.value = '{}'; elements.paramForm.innerHTML = '<div class="empty">文档中暂无可调用接口</div>'; return; }
    state.endpointId = text(rule.id || rule.ruleCode);
    state.bodyText = JSON.stringify(buildBody(rule), null, 2);
    elements.body.value = state.bodyText;
    var fields = rule.requestFields || [];
    elements.paramForm.innerHTML = fields.length ? fields.map(function (field, index) {
      var type = text(field.type).toUpperCase();
      if (type === 'BOOLEAN' || type === 'BOOL') {
        return '<label for="runner-field-' + index + '">' + escapeMarkup(field.label || field.path) + ' <code>' + escapeMarkup(field.path) + '</code></label><select id="runner-field-' + index + '" data-field-index="' + index + '"><option value="true">true</option><option value="false">false</option></select>';
      }
      var initial = exampleValue(field);
      if (typeof initial === 'object') initial = JSON.stringify(initial);
      return '<label for="runner-field-' + index + '">' + escapeMarkup(field.label || field.path) + ' <code>' + escapeMarkup(field.path) + '</code></label><input id="runner-field-' + index + '" data-field-index="' + index + '" value="' + escapeMarkup(initial) + '" autocomplete="off">';
    }).join('') : '<div class="empty">该接口无需业务参数，可直接编辑 Body。</div>';
    elements.paramForm.querySelectorAll('[data-field-index]').forEach(function (input) {
      input.addEventListener('input', function () {
        var field = fields[Number(input.getAttribute('data-field-index'))];
        var body;
        try { body = JSON.parse(elements.body.value || '{}'); } catch (error) { body = buildBody(rule); }
        setPath(body, field.path, parseInput(field, input.value));
        elements.body.value = JSON.stringify(body, null, 2);
      });
    });
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
  function bytesToHex(value) { return Array.from(new Uint8Array(value)).map(function (item) { return item.toString(16).padStart(2, '0'); }).join(''); }
  async function applyAuthentication(auth, url, headers, method, body) {
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
      var bodyHash = bytesToHex(await window.crypto.subtle.digest('SHA-256', encoder.encode(body)));
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
  function showState(message, kind) { elements.status.className = kind ? 'status ' + kind : 'empty'; elements.status.textContent = message; }
  async function sendRequest() {
    var rule = currentEndpoint();
    if (!rule) return;
    var baseUrl = elements.baseUrl.value.trim();
    var body = elements.body.value;
    try { JSON.parse(body); } catch (error) { showState('请求 Body 不是有效 JSON', 'danger'); return; }
    var url;
    try { url = new URL(endpointPath(rule), baseUrl); } catch (error) { showState('Base URL 格式不正确', 'danger'); return; }
    state.queryRows = parseRows(elements.query.value);
    state.headerRows = parseRows(elements.headers.value);
    state.queryRows.forEach(function (row) { url.searchParams.set(row.name, row.value); });
    var headers = new Headers({ 'Content-Type': 'application/json' });
    state.headerRows.forEach(function (row) { headers.set(row.name, row.value); });
    try { await applyAuthentication(currentAuth(), url, headers, 'POST', body); } catch (error) { showState(error.message, 'danger'); return; }
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
      var response = await fetch(url.toString(), { method: 'POST', headers: headers, body: body, signal: state.controller.signal });
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
  function initialize() {
    elements = { baseUrl: byId('runner-base-url'), endpoint: byId('runner-endpoint'), auth: byId('runner-auth'), credentials: byId('runner-credentials'), paramForm: byId('runner-param-form'), query: byId('runner-query'), headers: byId('runner-headers'), body: byId('runner-body'), timeout: byId('runner-timeout'), send: byId('runner-send'), cancel: byId('runner-cancel'), status: byId('runner-status'), responseMeta: byId('runner-response-meta'), responseHeaders: byId('runner-response-headers'), responseBody: byId('runner-response-body') };
    elements.endpoint.innerHTML = doc.rules.map(function (rule) { return '<option value="' + escapeMarkup(rule.id || rule.ruleCode) + '">' + escapeMarkup(rule.ruleName || rule.ruleCode) + ' · ' + escapeMarkup(rule.ruleCode) + '</option>'; }).join('');
    elements.auth.innerHTML = doc.authentications.length ? doc.authentications.map(function (auth, index) { return '<option value="' + index + '">' + escapeMarkup(auth.authName || auth.authType) + '</option>'; }).join('') : '<option value="-1">未配置鉴权</option>';
    state.endpointId = elements.endpoint.value;
    renderEndpoint(); renderCredentials();
    elements.endpoint.addEventListener('change', function () { state.endpointId = elements.endpoint.value; renderEndpoint(); });
    elements.auth.addEventListener('change', renderCredentials);
    elements.send.addEventListener('click', sendRequest);
    elements.cancel.addEventListener('click', function () { if (state.controller) { state.abortReason = 'manual'; state.controller.abort(); } });
    byId('runner-toggle').addEventListener('click', function () { byId('online-runner').classList.add('open'); });
    byId('runner-close').addEventListener('click', function () { byId('online-runner').classList.remove('open'); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize); else initialize();
}());`
}
