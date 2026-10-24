import { createConsoleAuthGuard } from '../../src/router/authGuard.js'
import {
  clearCurrentUser,
  hasPermission,
  getCurrentUser,
  setPermissionEnforcement
} from '../../src/security/permissionState.js'

function createRoute(path) {
  return {
    path,
    fullPath: path,
    query: {}
  }
}

describe('router console auth guard', () => {
  test.each([false, true])('较早导航的会话请求晚返回（失败=%s）不会覆盖新身份', async fail => {
    let resolveOld, rejectOld
    const oldMe = new Promise((resolve, reject) => { resolveOld = resolve; rejectOld = reject })
    const fetchMe = vi.fn().mockReturnValueOnce(oldMe).mockResolvedValue({
      code: '000000', data: { username: 'new-user', permissions: ['project:view'] },
    })
    const guard = createConsoleAuthGuard(async () => ({ code: '000000', data: { loginEnabled: true } }), fetchMe)
    const oldNext = vi.fn()
    const oldNavigation = guard(createRoute('/rule'), createRoute('/'), oldNext)
    await Promise.resolve()
    await guard(createRoute('/project'), createRoute('/'), vi.fn())
    if (fail) rejectOld(new Error('expired'))
    else resolveOld({ code: '000000', data: { username: 'old-user', permissions: ['rule:view'] } })
    await oldNavigation
    expect(getCurrentUser()?.username).toBe('new-user')
    expect(oldNext).toHaveBeenCalledWith(false)
  })

  test.each(['/login', '/project', '/admin-recovery'])('未初始化时 %s 进入注册页面，初始化后可进入登录页', async path => {
    const config = { code: '000000', data: { loginEnabled: true, initializationRequired: true } }
    const fetchMe = vi.fn()
    const guard = createConsoleAuthGuard(async () => config, fetchMe)
    const next = vi.fn()
    await guard(createRoute(path), createRoute('/'), next)
    expect(next).toHaveBeenLastCalledWith({ path: '/register', replace: true })
    await guard(createRoute('/register'), createRoute('/login'), next)
    expect(next).toHaveBeenLastCalledWith()
    config.data.initializationRequired = false
    await guard(createRoute('/login'), createRoute('/register'), next)
    expect(next).toHaveBeenLastCalledWith()
    expect(fetchMe).not.toHaveBeenCalled()
  })

  afterEach(() => {
    clearCurrentUser()
    setPermissionEnforcement(false)
  })

  test('登录关闭时直接放行受保护路由', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: false } })
    const fetchMe = vi.fn()
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith()
    expect(fetchMe).not.toHaveBeenCalled()
  })

  test('登录开启且未登录时每次访问受保护路由都跳转登录页', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({ code: 401, message: '未登录' })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)
    await guard(createRoute('/rule'), createRoute('/login'), next)

    expect(fetchMe).toHaveBeenCalledTimes(2)
    expect(next).toHaveBeenNthCalledWith(1, {
      path: '/login',
      query: { redirect: '/project' },
      replace: true
    })
    expect(next).toHaveBeenNthCalledWith(2, {
      path: '/login',
      query: { redirect: '/rule' },
      replace: true
    })
  })

  test('登录开启且会话有效时放行', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: '000000',
      data: { username: 'admin', permissions: ['project:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith()
  })

  test('登录开启时按最终权限拒绝无权访问的页面', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: '000000',
      data: { username: 'viewer', permissions: ['project:view'] }
    })
    const next = vi.fn()
    const route = createRoute('/rule')
    route.meta = { permission: 'rule:view' }

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(route, createRoute('/project'), next)

    expect(next).toHaveBeenCalledWith({ path: '/dashboard', replace: true })
  })

  test('未显式声明权限的设计器路由继承所属一级菜单权限', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: '000000',
      data: { username: 'field-viewer', permissions: ['field:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/designer/table/12'), createRoute('/variable'), next)

    expect(next).toHaveBeenCalledWith({ path: '/dashboard', replace: true })
  })

  test('未显式声明权限的详情路由按所属一级菜单权限放行', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: '000000',
      data: { username: 'list-viewer', permissions: ['list:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/list/8'), createRoute('/variable'), next)

    expect(next).toHaveBeenCalledWith()
  })

  test('没有业务模块权限时进入无权限提示页', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: '000000',
      data: { username: 'restricted', permissions: [] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith({ path: '/no-permission', replace: true })
  })

  test('鉴权配置加载失败时不放行受保护页面', async () => {
    const fetchConfig = vi.fn().mockResolvedValue(null)
    const fetchMe = vi.fn()
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/rule'), createRoute('/project'), next)

    expect(hasPermission('rule:view')).toBe(false)
    expect(fetchMe).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/rule' },
      replace: true
    })
  })

  test('鉴权配置请求异常时也按失败关闭处理', async () => {
    const fetchConfig = vi.fn().mockRejectedValue(new Error('network error'))
    const fetchMe = vi.fn()
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/rule'), createRoute('/project'), next)

    expect(hasPermission('rule:view')).toBe(false)
    expect(fetchMe).not.toHaveBeenCalled()
    expect(next).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/rule' },
      replace: true
    })
  })

  test('登录关闭时访问登录页跳回 redirect 或数据看板', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: '000000', data: { loginEnabled: false } })
    const fetchMe = vi.fn()
    const next = vi.fn()
    const loginRoute = createRoute('/login')
    loginRoute.query = { redirect: '/rule' }

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(loginRoute, createRoute('/project'), next)

    expect(next).toHaveBeenCalledWith({ path: '/rule', replace: true })

    const defaultRoute = createRoute('/login')
    const defaultNext = vi.fn()
    await guard(defaultRoute, createRoute('/project'), defaultNext)
    expect(defaultNext).toHaveBeenCalledWith({ path: '/dashboard', replace: true })
  })
})
