import { createConsoleAuthGuard } from '../../src/router/authGuard.js'
import {
  clearCurrentUser,
  hasPermission,
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
  afterEach(() => {
    clearCurrentUser()
    setPermissionEnforcement(false)
  })

  test('登录关闭时直接放行受保护路由', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: false } })
    const fetchMe = vi.fn()
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith()
    expect(fetchMe).not.toHaveBeenCalled()
  })

  test('登录开启且未登录时每次访问受保护路由都跳转登录页', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
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
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: 200,
      data: { username: 'admin', permissions: ['project:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith()
  })

  test('登录开启时按最终权限拒绝无权访问的页面', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: 200,
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
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: 200,
      data: { username: 'field-viewer', permissions: ['field:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/designer/table/12'), createRoute('/variable'), next)

    expect(next).toHaveBeenCalledWith({ path: '/dashboard', replace: true })
  })

  test('未显式声明权限的详情路由按所属一级菜单权限放行', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: 200,
      data: { username: 'field-viewer', permissions: ['field:view'] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/list/8'), createRoute('/variable'), next)

    expect(next).toHaveBeenCalledWith()
  })

  test('没有业务模块权限时仍进入自裁剪数据看板', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: true } })
    const fetchMe = vi.fn().mockResolvedValue({
      code: 200,
      data: { username: 'restricted', permissions: [] }
    })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith({ path: '/dashboard', replace: true })
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
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: false } })
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
