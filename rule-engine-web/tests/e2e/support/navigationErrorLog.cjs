const fs = require('node:fs/promises')
const { dirname } = require('node:path')

// 仅用于巡检。异步响应归到发起请求的操作，报告不保留请求参数、头或响应数据。
function safeText(value) {
  return String(value ?? '').replace(/https?:\/\/[^\s)]+/g, url => {
    try { return new URL(url).pathname } catch { return '[URL]' }
  }).replace(/([?&][\w.-]+=)[^\s&#]*/g, '$1[redacted]')
    .replace(/(["']?\b(?:password|authorization|(?:set-)?cookie|token|api[_-]?key)\b["']?\s*[:=])[\s\S]*/i, '$1[redacted]')
    .replace(/([\w.+-]+)@[\w.-]+\.[a-z]+/gi, '[email]')
    .replace(/\b(?:Bearer\s+\S+|[a-zA-Z0-9_-]{32,})\b/g, '[redacted]').slice(0, 240)
}

async function observeNavigationErrors(page, origin) {
  const appOrigin = new URL(origin).origin
  const started = new Date().toISOString()
  const issues = new Map()
  const requests = new Map()
  const reads = new Set()
  const listeners = []
  let step = { page: '/', action: '首次打开' }
  const timings = []
  let finishRequest
  let finishing = false

  function record(context, kind, fields = {}) {
    const item = { ...context, kind, ...fields }
    const key = JSON.stringify(item)
    const now = new Date().toISOString()
    const previous = issues.get(key)
    if (previous) { previous.count++; previous.lastSeen = now }
    else issues.set(key, { ...item, firstSeen: now, lastSeen: now, count: 1 })
  }
  function on(name, handler) {
    page.on(name, handler)
    listeners.push([name, handler])
  }
  on('pageerror', error => record(step, 'exception', { message: safeText(error.message) }))
  on('console', entry => {
    if (['error', 'warning'].includes(entry.type())) record(step, 'console.' + entry.type(), { message: safeText(entry.text()) })
  })
  function complete(request) {
    requests.delete(request)
    if (requests.size === 0) finishRequest?.()
  }
  on('request', request => {
    if (finishing) return
    const url = new URL(request.url())
    if (['http:', 'https:'].includes(url.protocol)) {
      requests.set(request, { ...step, method: request.method(), path: (url.origin === appOrigin ? '' : url.origin) + safeText(url.pathname) })
    }
  })
  on('requestfailed', request => {
    const context = requests.get(request)
    if (!context) return
    complete(request)
    const message = safeText(request.failure()?.errorText)
    record(context, /ERR_ABORTED/.test(message) ? 'cancelled' : 'network', { message })
  })
  on('requestfinished', request => {
    const context = requests.get(request)
    if (!context) return
    complete(request)
    const read = (async () => {
      const response = await request.response()
      if (!response) return
      const status = response.status()
      if (status >= 300 && status < 400) return
      let code, message, detail
      if (context.path.startsWith('/api/') && /json/i.test(response.headers()['content-type'] || '')) {
        let body
        try {
          body = await response.json()
        } catch {
          record(context, status >= 400 ? 'http' : 'response-unreadable', { status, message: '无法读取 JSON 响应' })
          return
        }
        code = typeof body?.code === 'string' && /^\d{6}$/.test(body.code) ? body.code : 'invalid-envelope'
        if (code !== '000000') {
          message = safeText(body?.message)
          if (typeof body?.detail === 'string') detail = safeText(body.detail)
        }
      }
      if (status >= 400 || message !== undefined) {
        record(context, status >= 400 ? 'http' : 'business', { status, code, message, detail })
      }
    })().catch(() => record(context, 'response-unreadable'))
    reads.add(read)
    read.finally(() => reads.delete(read))
  })
  await page.exposeBinding('__navigationAuditNotice', ({ frame }, route, kind, message) => {
    if (frame === page.mainFrame() && new URL(frame.url()).origin === appOrigin) {
      record({ ...step, page: safeText(route) }, kind, { message: safeText(message) })
    }
  })
  await page.addInitScript(() => {
    const { document, location, MutationObserver } = globalThis
    const selector = '.el-message--error,.el-message--warning,.el-alert--error,.el-alert--warning,.el-form-item__error'
    const seen = new WeakMap()
    function report(element) {
      if (!element || element.closest('[hidden],[inert],[aria-hidden="true"]')) return
      const message = element.textContent.trim()
      if (!message || seen.get(element) === message) return
      seen.set(element, message)
      const kind = element.matches('.el-form-item__error') ? 'validation'
        : element.matches('.el-message--error,.el-alert--error') ? 'ui-error' : 'ui-warning'
      void globalThis.__navigationAuditNotice(location.hash.slice(1).split('?')[0], kind, message)
    }
    new MutationObserver(records => {
      for (const change of records) {
        report((change.target.parentElement?.closest(selector)) || change.target.closest?.(selector))
        for (const node of change.addedNodes) {
          if (node.nodeType !== 1) continue
          if (node.matches(selector)) report(node)
          node.querySelectorAll(selector).forEach(report)
        }
      }
    }).observe(document, { childList: true, characterData: true, subtree: true })
  })
  return {
    timings,
    step(pagePath, action) { step = { page: pagePath, action } },
    async finish(testInfo) {
      finishing = true
      if (requests.size) {
        let timeout
        await new Promise(resolve => {
          finishRequest = resolve
          timeout = setTimeout(resolve, 2000)
        })
        clearTimeout(timeout)
      }
      for (const [name, handler] of listeners) page.off(name, handler)
      await Promise.all([...reads])
      const report = { started, finished: new Date().toISOString(), step, timings, issues: [...issues.values()],
        pendingRequests: [...requests.values()], testErrors: testInfo.errors.map(e => safeText(e.message?.split('\n')[0])) }
      const path = testInfo.outputPath('navigation-audit.json')
      await fs.mkdir(dirname(path), { recursive: true })
      await fs.writeFile(path, JSON.stringify(report, null, 2) + '\n', 'utf8')
      await testInfo.attach('navigation-audit', { path, contentType: 'application/json' })
      return report
    },
  }
}

module.exports = { observeNavigationErrors }
