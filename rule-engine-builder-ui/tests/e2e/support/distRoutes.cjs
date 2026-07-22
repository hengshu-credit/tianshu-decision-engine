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

function apiResponse(pathname) {
  return {
    code: 200,
    message: 'success',
    data: apiData.get(pathname) ?? { records: [], total: 0 }
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

async function installDistRoutes(page) {
  await page.route('http://tianshu.local/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.startsWith('/api/')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json; charset=utf-8',
        body: JSON.stringify(apiResponse(url.pathname))
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
}

module.exports = { installDistRoutes }
