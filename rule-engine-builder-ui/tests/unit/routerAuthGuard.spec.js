import { createConsoleAuthGuard } from '../../src/router/authGuard.js'

function createRoute(path) {
  return {
    path,
    fullPath: path,
    query: {}
  }
}

describe('router console auth guard', () => {
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
    const fetchMe = vi.fn().mockResolvedValue({ code: 200, data: { username: 'admin' } })
    const next = vi.fn()

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(createRoute('/project'), createRoute('/login'), next)

    expect(next).toHaveBeenCalledWith()
  })

  test('登录关闭时访问登录页跳回 redirect 或项目首页', async () => {
    const fetchConfig = vi.fn().mockResolvedValue({ code: 200, data: { loginEnabled: false } })
    const fetchMe = vi.fn()
    const next = vi.fn()
    const loginRoute = createRoute('/login')
    loginRoute.query = { redirect: '/rule' }

    const guard = createConsoleAuthGuard(fetchConfig, fetchMe)
    await guard(loginRoute, createRoute('/project'), next)

    expect(next).toHaveBeenCalledWith({ path: '/rule', replace: true })
  })
})
