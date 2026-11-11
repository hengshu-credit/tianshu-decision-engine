import { EventEmitter } from 'node:events'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { observeNavigationErrors } from '../e2e/support/navigationErrorLog.cjs'

test('报错按触发动作归属、去重并保留失败前的耗时，不导出请求参数和业务数据', async () => {
  const dir = await mkdtemp(path.join(tmpdir(), 'navigation-audit-'))
  try {
    const page = new EventEmitter()
    page.exposeBinding = vi.fn()
    page.addInitScript = vi.fn()
    const audit = await observeNavigationErrors(page, 'http://tianshu.local')
    const request = (status, code) => ({
      method: () => 'POST',
      url: () => 'http://tianshu.local/api/rule/console/activity?token=private-query',
      response: async () => ({
        status: () => status,
        headers: () => ({ 'content-type': 'application/json' }),
        json: async () => ({ code, message: '入参校验失败', detail: '不支持的页面', data: { password: 'private-body' } }),
      }),
    })
    audit.step('/ledger', '切换文章管理')
    const requests = [request(400, '100001'), request(400, '100001'), request(200, '100001')]
    requests.forEach(req => page.emit('request', req))
    audit.timings.push({ kind: 'tab', name: '文章管理', duration: 40 })
    audit.step('/project', '返回项目')
    requests.forEach(req => page.emit('requestfinished', req))
    page.emit('pageerror', new TypeError('页面未完成加载'))
    const info = {
      errors: [{ message: '定位器超时\nprivate-stack' }],
      outputPath: name => path.join(dir, name),
      attach: vi.fn(),
    }
    await audit.finish(info)
    const saved = await readFile(path.join(dir, 'navigation-audit.json'), 'utf8')
    const report = JSON.parse(saved)
    expect(report.issues).toHaveLength(3)
    expect(report.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ page: '/ledger', action: '切换文章管理', kind: 'http', status: 400, code: '100001', detail: '不支持的页面', count: 2 }),
      expect.objectContaining({ page: '/ledger', kind: 'business', status: 200, code: '100001', count: 1 }),
      expect.objectContaining({ page: '/project', kind: 'exception', message: '页面未完成加载' }),
    ]))
    expect(report.timings).toEqual(audit.timings)
    expect(report.testErrors).toEqual(['定位器超时'])
    expect(saved).not.toMatch(/private-query|private-body|private-stack/)
    expect(info.attach).toHaveBeenCalledOnce()
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
})
