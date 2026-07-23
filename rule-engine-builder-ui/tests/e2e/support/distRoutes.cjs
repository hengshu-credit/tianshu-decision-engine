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
  ['/api/rule/definition/list', { records: [], total: 0 }],
  ['/api/rule/runtime-log/list', { records: [], total: 0 }],
  ['/api/rule/model/list', { records: [], total: 0 }],
  ['/api/rule/model/health', { healthy: true }],
  ['/api/rule/model/runtimeCapabilities', { availableProviders: ['CPUExecutionProvider'] }]
])

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

async function apiResponse(url, request, routeApiData) {
  const configuredData = routeApiData.get(url.pathname)?.data
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
      const fixture = routeApiData.get(url.pathname)
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

    const absolutePath = resolveDistFile(url.pathname)
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
