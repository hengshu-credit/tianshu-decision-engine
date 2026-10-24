const fs = require('node:fs/promises')
const path = require('node:path')
const { platformResponse } = require('./platformResponse.cjs')

const distRoot = process.env.E2E_DIST_DIR ? path.resolve(process.env.E2E_DIST_DIR) : path.resolve(__dirname, '../../../dist')

const apiData = new Map([
  ['/api/rule/governance/workspaces/states', ({ url }) => (url.searchParams.get('ids') || '').split(',').filter(Boolean).map(resourceId => ({ resourceId, effectiveStatus: null, hasEffectiveVersion: false, requestId: null, requestStatus: null, owned: false, hasRuleDraft: false }))],
  ['/api/rule/governance/workspaces/draft-ids', []],
  ['/api/rule/billing/summary/refresh', null],
  ['/api/rule/governance/workspaces', { records: [], total: 0 }],
  ['/api/auth/console/config', { loginEnabled: false }],
  ['/api/auth/console/brand', {}],
  ['/api/auth/console/me', { username: 'e2e' }],
  ['/api/rule/console/accounts/project-options', [
    { id: 1, projectCode: 'e2e_project', projectName: 'E2E 项目', status: 1 }
  ]],
  ['/api/rule/project/1', { id: 1, projectCode: 'e2e_project', projectName: 'E2E 项目', status: 1 }],
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
    geo: { points: [{ longitude: 120.12, latitude: 30.28, count: 18, passCount: 12, reviewCount: 3, rejectCount: 3 }], validCount: 18, excludedCount: 0, excludedReasons: {} },
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
  ['/api/rule/datasource/api-config/list', { records: [], total: 0 }],
  ['/api/rule/datasource/api-config/available', []],
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

function resolveApiFixture(routeApiData, pathname, method = 'GET') {
  const direct = routeApiData.get(`${method} ${pathname}`)
  if (direct) return direct
  // 同一路径可配置多个方法；方法不匹配时仍保留期望方法用于严格错误报告。
  const otherMethod = [...routeApiData].find(([key]) => key.endsWith(` ${pathname}`))?.[1]
  if (otherMethod) return otherMethod
  // 项目选择器使用只读目录；复用同一组测试项目，但仅返回真实目录契约允许的元数据。
  if (method === 'GET' && pathname === '/api/rule/project/options') {
    return projectOptionsFixture(routeApiData.get('GET /api/rule/project/list'), true)
  }
  const projectOption = /^\/api\/rule\/project\/options\/(\d+)$/.exec(pathname)
  if (method === 'GET' && projectOption) {
    return projectOptionsFixture(routeApiData.get(`GET /api/rule/project/${projectOption[1]}`), false)
  }
  if (/^\/api\/rule\/governance\/workspaces\/[A-Z_]+\/\d+$/.test(pathname)) return { data: null, expectedMethod: 'GET' }
  if (/^\/api\/rule\/resource-versions\/[A-Z_]+\/\d+$/.test(pathname)) return { data: { versions: [], history: [], drafts: [], actor: 'e2e' }, expectedMethod: 'GET' }
  if (/^\/api\/rule\/resource-versions\/[A-Z_]+\/\d+\/events$/.test(pathname)) return { data: [], expectedMethod: 'GET' }
  return null
}

function projectOptionsFixture(source, paged) {
  if (!source) return null
  const metadata = project => project == null ? null : ({
    id: project.id, projectCode: project.projectCode, projectName: project.projectName, status: project.status
  })
  return { expectedMethod: 'GET', data: async args => {
    const value = typeof source.data === 'function' ? await source.data(args) : source.data
    if (!paged) return metadata(value)
    if (Array.isArray(value)) return { records: value.map(metadata), total: value.length }
    return { ...value, records: (value?.records || []).map(metadata) }
  } }
}

async function apiResponse(url, request, routeApiData) {
  const configuredData = resolveApiFixture(routeApiData, url.pathname, request.method())?.data
  const data = typeof configuredData === 'function'
    ? await configuredData({ url, request })
    : configuredData
  return platformResponse(data === undefined ? { records: [], total: 0 } : data)
}

function addApiFixtures(target, fixtures) {
  for (const [key, data] of fixtures) {
    const methodMatch = /^([A-Z]+)\s+(\/api\/.*)$/.exec(key)
    const expectedMethod = methodMatch?.[1] || 'GET'
    const pathname = methodMatch?.[2] || key
    target.set(`${expectedMethod} ${pathname}`, { data, expectedMethod })
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
    if (message.type() === 'warning' && /Failed to resolve (component|directive)/.test(message.text())) consoleErrors.push(message.text())
  })
  await page.route(`${options.origin || 'http://tianshu.local'}/**`, async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      const method = route.request().method()
      const fixture = resolveApiFixture(routeApiData, url.pathname, method)
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

    if (options.liveStatic) { await route.continue(); return }
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
