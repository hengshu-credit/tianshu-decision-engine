const fs = require('node:fs/promises')
const path = require('node:path')

const distRoot = path.resolve(__dirname, '../../../dist')

const apiData = new Map([
  ['/api/auth/console/config', { loginEnabled: false }],
  ['/api/auth/console/me', { username: 'e2e' }],
  ['/api/rule/project/list', {
    records: [{ id: 1, projectCode: 'e2e_project', projectName: 'E2E 项目', status: 1 }],
    total: 1
  }],
  ['/api/rule/dashboard/applications', {
    visible: true,
    summary: {
      applicationCount: 18,
      passCount: 12,
      reviewCount: 3,
      rejectCount: 3,
      unclassifiedCount: 0,
      deviceCount: 15,
      passRate: 0.6667,
      reviewRate: 0.1667,
      empty: false
    },
    periods: { items: [{ key: '12', label: '12期', count: 18, rate: 1 }], validCount: 18, excludedCount: 0 },
    amounts: { items: [{ key: '[3000,5000)', label: '3,000–4,999', count: 18, rate: 1 }], validCount: 18, excludedCount: 0 },
    geo: { points: [{ longitude: 120.12, latitude: 30.28, count: 18 }], validCount: 18, excludedCount: 0, excludedReasons: {} },
    mappingIssues: []
  }],
  ['/api/rule/dashboard/operations', {
    ruleExecution: { visible: true, timing: { sampleCount: 18, avgMs: 36, p95Ms: 70, p99Ms: 80, distribution: { items: [], validCount: 18, excludedCount: 0 } } },
    database: { visible: true, resourceCount: 2, calls: { requestCount: 5, successCount: 5, foundCount: 0, foundRate: 0, timing: { avgMs: 20, p95Ms: 30 } } },
    lists: { visible: true, libraryCount: 4, categories: { items: [{ key: 'BLACK', label: 'BLACK', count: 4, rate: 1 }], validCount: 4, excludedCount: 0 }, calls: { timing: { avgMs: 3, p95Ms: 5 } } },
    datasource: { visible: true, resourceCount: 3, calls: { requestCount: 10, successCount: 9, foundCount: 7, foundRate: 0.7, timing: { avgMs: 120, p95Ms: 220 } } },
    downstreamRules: { visible: true, count: 6 },
    billing: { visible: true, amounts: [{ currency: 'CNY', amount: 8.8 }] }
  }],
  ['/api/rule/dashboard/governance', {
    ruleSetHits: { visible: true, items: [{ ruleCode: 'RISK_TOP', ruleName: '风险规则集', applicationCount: 18, hitCount: 6, hitRate: 0.3333 }] },
    approvals: { visible: true, totalCount: 4, statuses: [{ key: 'APPROVED', label: 'APPROVED', count: 4, rate: 1 }] }
  }],
  ['/api/rule/project/1/workbench', {
    project: {
      id: 1,
      projectCode: 'e2e_project',
      projectName: 'E2E 项目',
      status: 1
    },
    metrics: {
      fieldCount: 2,
      dataSourceCount: 0,
      enabledDataSourceCount: 0,
      modelCount: 0,
      ruleCount: 1,
      draftRuleCount: 1,
      publishedRuleCount: 0,
      testScenarioCount: 0,
      pendingApprovalCount: 0,
      recentExecutionCount: 0,
      recentSuccessCount: 0,
      recentSuccessRate: null
    },
    checks: [],
    warnings: [],
    recentExecution: null
  }],
  ['/api/rule/definition/list', { records: [], total: 0 }],
  ['/api/rule/variable/project/1', []],
  ['/api/rule/runtime-log/list', { records: [], total: 0 }],
  ['/api/rule/ops/execution-metrics', {
    instanceId: 'e2e-node-1',
    observedAt: '2026-09-28T11:00:00Z',
    persistence: { queueDepth: 0, queueCapacity: 5000, fallback: 0, failed: 0, avgWriteMs: 0 },
    sourceResolution: { queueDepth: 0, active: 0, parallelism: 4, failed: 0 },
    openExecution: { queueDepth: 0, queueCapacity: 1000, rejected: 0, timedOut: 0 },
    externalCircuitBreakers: { registeredApis: 0, open: 0, halfOpen: 0, closed: 0 },
    publishOutbox: { pending: 0, delivering: 0, retrying: 0, deadLetter: 0, maxRetries: 20 },
    executionPersistenceOutbox: { pending: 0, processing: 0, delivered: 0, deadLetter: 0, maxRetries: 20 },
    ruleWarmup: { state: 'READY', targetCount: 0, preparedCount: 0, failureCount: 0 }
  }],
  ['/api/rule/model/list', { records: [], total: 0 }],
  ['/api/rule/model/health', { healthy: true }],
  ['/api/rule/model/runtimeCapabilities', { availableProviders: ['CPUExecutionProvider'] }]
])

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.geojson': 'application/geo+json; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function resolveApiFixture(routeApiData, pathname) {
  const direct = routeApiData.get(pathname)
  if (direct) return direct
  return null
}

async function apiResponse(url, request, routeApiData) {
  const configuredData = resolveApiFixture(routeApiData, url.pathname)?.data
  const data = typeof configuredData === 'function'
    ? await configuredData({ url, request })
    : configuredData
  return {
    code: 200,
    message: 'success',
    data: data ?? { records: [], total: 0 }
  }
}

function addApiFixtures(target, fixtures) {
  for (const [key, data] of fixtures) {
    const methodMatch = /^([A-Z]+)\s+(\/api\/.*)$/.exec(key)
    const expectedMethod = methodMatch?.[1] || 'GET'
    const pathname = methodMatch?.[2] || key
    target.set(pathname, { data, expectedMethod })
  }
}

function resolveDistFile(pathname) {
  const decoded = decodeURIComponent(pathname)
  const relativePath = decoded === '/' ? 'index.html' : decoded.replace(/^\/+/, '')
  const absolutePath = path.resolve(distRoot, relativePath)
  const rootPrefix = `${distRoot}${path.sep}`
  if (absolutePath !== distRoot && !absolutePath.startsWith(rootPrefix)) return null
  return absolutePath
}

// Monaco AMD 的 css/nls 插件把资源 ID 保留在 URL 中；静态服务器需要把
// /vs/css!vs/editor/editor.main.css 映射为 /vs/editor/editor.main.css，
// 把 /vs/nls!vs/editor/editor.main.nls 映射为对应的 .nls.js 文件。
function normalizeMonacoPluginPath(pathname) {
  const match = /^\/vs\/(css|nls)!vs\/(.+)$/.exec(pathname)
  if (!match) return pathname
  const resource = match[2]
  return `/vs/${match[1] === 'nls' && !/\.js$/.test(resource) ? `${resource}.js` : resource}`
}

async function installDistRoutes(page, options = {}) {
  const routeApiData = new Map()
  addApiFixtures(routeApiData, apiData)
  const customApiData = options.apiData instanceof Map
    ? options.apiData
    : new Map(Object.entries(options.apiData || {}))
  addApiFixtures(routeApiData, customApiData)

  const requests = []
  const unmatchedRequests = []
  const pageErrors = []
  const consoleErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  await page.route('http://tianshu.local/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      const method = route.request().method()
      const fixture = resolveApiFixture(routeApiData, url.pathname)
      requests.push({ method, url: route.request().url() })
      if (!fixture || fixture.expectedMethod !== method) {
        unmatchedRequests.push({
          method,
          pathname: url.pathname,
          expectedMethod: fixture?.expectedMethod,
        })
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json; charset=utf-8',
        body: JSON.stringify(await apiResponse(url, route.request(), routeApiData))
      })
      return
    }

    const absolutePath = resolveDistFile(normalizeMonacoPluginPath(url.pathname))
    if (!absolutePath) {
      await route.fulfill({ status: 403, body: 'Forbidden' })
      return
    }

    try {
      const body = await fs.readFile(absolutePath)
      await route.fulfill({
        status: 200,
        contentType: contentTypes[path.extname(absolutePath)] || 'application/octet-stream',
        body
      })
    } catch (error) {
      if (error && error.code === 'ENOENT') {
        await route.fulfill({ status: 404, body: 'Not Found' })
        return
      }
      throw error
    }
  })

  const assertClean = () => {
    const issues = []
    if (unmatchedRequests.length) {
      issues.push(
        `Unmatched API requests: ${unmatchedRequests
          .map(request => {
            const expectation = request.expectedMethod
              ? ` (expected ${request.expectedMethod})`
              : ''
            return `${request.method} ${request.pathname}${expectation}`
          })
          .join(', ')}`
      )
    }
    if (pageErrors.length) issues.push(`Page errors: ${pageErrors.join(' | ')}`)
    if (consoleErrors.length) {
      issues.push(`Console errors: ${consoleErrors.join(' | ')}`)
    }
    if (issues.length) throw new Error(issues.join('\n'))
  }

  return {
    requests,
    unmatchedRequests,
    pageErrors,
    consoleErrors,
    assertClean,
  }
}

module.exports = { installDistRoutes }
