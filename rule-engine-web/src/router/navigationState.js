import { shallowReactive } from 'vue'
import { isNavigationFailure, NavigationFailureType } from 'vue-router'

export const navigationState = shallowReactive({ pendingRoute: null, error: null })
const handledErrors = new WeakSet()

export function isHandledNavigationError(error) { return handledErrors.has(error) }

// 预加载和 Vue Router 共用一次下载；失败允许下次导航重新尝试。
export function preloadableView(loader) {
  let pending
  const load = () => {
    if (!pending) pending = loader().catch(error => { pending = null; throw error })
    return pending
  }
  load.preload = () => load().catch(() => {})
  return load
}

export function preloadRoute(router, path) {
  return Promise.all(router.resolve(path).matched.map(record => record.components?.default?.preload?.()))
}

// 首屏完成后按空闲时段预热菜单代码，不创建页面组件或请求业务数据。
export function preloadMenuRoutes(router, paths) {
  const remaining = [...paths]
  let stopped = false
  let timer
  const idle = typeof requestIdleCallback === 'function'
  const schedule = () => {
    if (stopped || !remaining.length) return
    const run = () => { if (!stopped) preloadRoute(router, remaining.shift()).finally(schedule) }
    timer = idle ? requestIdleCallback(run, { timeout: 1500 }) : setTimeout(run, 0)
  }
  schedule()
  return () => {
    stopped = true
    if (idle) cancelIdleCallback(timer)
    else clearTimeout(timer)
  }
}

export function installNavigationFeedback(router) {
  // 在组件离开确认之后、会话校验之前更新反馈，不卸载旧页面或绕过鉴权。
  router.beforeEach(to => {
    navigationState.pendingRoute = to
    navigationState.error = null
    to.matched.forEach(record => record.components?.default?.preload?.())
  })
  router.afterEach((to, _from, failure) => {
    // 被新导航取代的旧请求不能清除新页面的加载状态。
    if (navigationState.pendingRoute === to || isNavigationFailure(failure, NavigationFailureType.duplicated)) {
      navigationState.pendingRoute = null
    }
  })
  router.onError((error, to) => {
    handledErrors.add(error)
    if (navigationState.pendingRoute !== to) return
    navigationState.pendingRoute = null
    navigationState.error = to
  })
}
