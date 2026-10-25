import { createMemoryHistory, createRouter, isNavigationFailure } from 'vue-router'
import { flushPromises } from '@vue/test-utils'
import { installNavigationFeedback, isHandledNavigationError, navigationState, preloadableView, preloadMenuRoutes, preloadRoute } from '@/router/navigationState'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function makeRouter(loader = async () => ({ template: '<p>目标页面</p>' })) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { template: '<p>原页面</p>' } },
    { path: '/rule', component: preloadableView(loader), meta: { title: '规则管理' } },
    { path: '/project', component: { template: '<p>项目页面</p>' } },
  ] })
  installNavigationFeedback(router)
  return router
}

afterEach(() => { navigationState.pendingRoute = null; navigationState.error = null })

test('会话校验尚未完成时已经反馈目标页面并开始下载，不提前提交路由', async () => {
  const download = deferred()
  const auth = deferred()
  const loader = vi.fn(() => download.promise)
  const router = makeRouter(loader)
  await router.push('/')
  router.beforeEach(() => auth.promise)
  const navigating = router.push('/rule')
  await flushPromises()
  expect(navigationState.pendingRoute.meta.title).toBe('规则管理')
  expect(router.currentRoute.value.path).toBe('/')
  expect(loader).toHaveBeenCalledTimes(1)
  auth.resolve()
  download.resolve({ template: '<p>目标页面</p>' })
  await navigating
  expect(loader).toHaveBeenCalledTimes(1)
  expect(router.currentRoute.value.path).toBe('/rule')
  expect(navigationState.pendingRoute).toBeNull()
})

test('旧导航结束不能移除后一次导航的状态', async () => {
  const first = deferred(), second = deferred()
  const router = makeRouter()
  await router.push('/')
  router.beforeEach(to => to.path === '/rule' ? first.promise : second.promise)
  const oldNavigation = router.push('/rule')
  await flushPromises()
  const newNavigation = router.push('/project')
  await flushPromises()
  first.resolve()
  expect(isNavigationFailure(await oldNavigation)).toBe(true)
  expect(navigationState.pendingRoute.path).toBe('/project')
  second.resolve()
  await newNavigation
  expect(navigationState.pendingRoute).toBeNull()
  expect(router.currentRoute.value.path).toBe('/project')
})

test('悬停预加载只下载代码，不提前导航或创建页面，点击时复用下载', async () => {
  const created = vi.fn()
  const loader = vi.fn().mockResolvedValue({ created, template: '<p>规则页</p>' })
  const router = makeRouter(loader)
  await router.push('/')
  await preloadRoute(router, '/rule')
  await preloadRoute(router, '/rule')
  expect(loader).toHaveBeenCalledTimes(1)
  expect(created).not.toHaveBeenCalled()
  expect(router.currentRoute.value.path).toBe('/')
  await router.push('/rule')
  expect(loader).toHaveBeenCalledTimes(1)
})

test('布局离开后取消尚未开始的菜单预加载', async () => {
  vi.useFakeTimers()
  const loader = vi.fn().mockResolvedValue({ template: '<p>规则页</p>' })
  try {
    const stop = preloadMenuRoutes(makeRouter(loader), ['/rule'])
    stop()
    await vi.runAllTimersAsync()
    expect(loader).not.toHaveBeenCalled()
  } finally { vi.useRealTimers() }
})

test('加载期间重新点击当前页可取消跳转并恢复内容', async () => {
  const auth = deferred()
  const router = makeRouter()
  await router.push('/')
  router.beforeEach(() => auth.promise)
  const navigating = router.push('/rule')
  await flushPromises()
  await router.push('/')
  expect(navigationState.pendingRoute).toBeNull()
  auth.resolve()
  expect(isNavigationFailure(await navigating)).toBe(true)
  expect(router.currentRoute.value.path).toBe('/')
})

test('路由被拒绝或重定向后不会留下永久加载状态', async () => {
  const router = makeRouter()
  await router.push('/')
  const remove = router.beforeEach(() => false)
  await router.push('/rule')
  expect(navigationState.pendingRoute).toBeNull()
  remove()
  router.beforeEach(to => to.path === '/rule' ? '/project' : true)
  await router.push('/rule')
  expect(router.currentRoute.value.path).toBe('/project')
  expect(navigationState.pendingRoute).toBeNull()
})

test('下载失败保留原路由并提供重试，重试会重新下载', async () => {
  const download = deferred()
  const loader = vi.fn().mockImplementationOnce(() => download.promise).mockResolvedValue({ template: '<p>重试成功</p>' })
  const router = makeRouter(loader)
  await router.push('/')
  const navigating = router.push('/rule')
  const failure = expect(navigating).rejects.toThrow('download failed')
  await flushPromises()
  download.reject(new Error('download failed'))
  await failure
  expect(navigationState.pendingRoute).toBeNull()
  expect(navigationState.error.fullPath).toBe('/rule')
  expect(router.currentRoute.value.path).toBe('/')
  await router.push('/rule')
  expect(loader).toHaveBeenCalledTimes(2)
  expect(navigationState.error).toBeNull()
  expect(router.currentRoute.value.path).toBe('/rule')
})

test('已取消的页面下载报错不会覆盖新页面，并标记为已处理', async () => {
  const download = deferred()
  const router = makeRouter(() => download.promise)
  await router.push('/')
  const oldNavigation = router.push('/rule')
  const failure = expect(oldNavigation).rejects.toThrow('old download failed')
  await flushPromises()
  await router.push('/project')
  const error = new Error('old download failed')
  download.reject(error)
  await failure
  expect(isHandledNavigationError(error)).toBe(true)
  expect(isHandledNavigationError(new Error('unrelated'))).toBe(false)
  expect(navigationState.error).toBeNull()
  expect(navigationState.pendingRoute).toBeNull()
  expect(router.currentRoute.value.path).toBe('/project')
})
