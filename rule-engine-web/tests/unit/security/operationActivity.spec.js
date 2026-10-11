import { recordTabVisit } from '@/security/operationActivity'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'

describe('缓存页签访问留痕', () => {
  afterEach(() => { clearCurrentUser(); vi.unstubAllGlobals() })
  test('点击页签记录当前页面及可见页签名称，不采集表单内容', () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetch)
    setCurrentUser({ username: 'viewer', permissions: ['account:view'] })
    const tab = document.createElement('button')
    tab.setAttribute('role', 'tab')
    tab.innerHTML = '<span>角色</span>'
    recordTabVisit({ target: tab.firstChild }, { path: '/account' })
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ action: 'PAGE_VIEW', pagePath: '/account', name: '页签 · 角色' })
    recordTabVisit({ target: document.createElement('input') }, { path: '/account' })
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  test('禁用页签与未登录状态不发送访问事件', () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetch)
    const tab = document.createElement('button'); tab.setAttribute('role', 'tab'); tab.textContent = '角色'
    recordTabVisit({ target: tab }, { path: '/account' })
    setCurrentUser({ username: 'viewer', permissions: [] })
    tab.setAttribute('aria-disabled', 'true')
    recordTabVisit({ target: tab }, { path: '/account' })
    expect(fetch).not.toHaveBeenCalled()
  })
  test('页签访问保留版本定位参数并排除正文和凭据', () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetch)
    setCurrentUser({ username: 'viewer', permissions: ['rule:view'] })
    const tab = document.createElement('button'); tab.setAttribute('role', 'tab'); tab.textContent = '版本历史'
    recordTabVisit({ target: tab }, { path: '/rule/12', query: { sourceType: 'REVISION', sourceId: '99', token: 'PRIVATE', requestJson: 'PRIVATE' } })
    const event = JSON.parse(fetch.mock.calls[0][1].body)
    expect(event.pagePath).toBe('/rule/12?sourceType=REVISION&sourceId=99')
    expect(event.name).toBe('页签 · 版本历史')
    expect(fetch.mock.calls[0][1].body).not.toContain('PRIVATE')
  })
})
