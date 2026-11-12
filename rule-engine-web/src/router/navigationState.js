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

export function loadWorkspaceUi() {
  return import('@/plugins/workspaceUi')
}

// 登录成功即下载布局、一级菜单和组件库，与首次会话复核并行。业务页面由路由守卫放行后挂载。
export function preloadWorkspaceAfterLogin(router) {
  preloadRoute(router, '/')
  loadWorkspaceUi().catch(() => {}) // 真正导航时仍可重试，并由路由处理加载错误。
}

export function installNavigationFeedback(router, canPreload = () => true) {
  // 在组件离开确认之后、会话校验之前更新反馈，不卸载旧页面或绕过鉴权。
  router.beforeEach(to => {
    navigationState.pendingRoute = to
    navigationState.error = null
    if (canPreload(to)) to.matched.forEach(record => record.components?.default?.preload?.())
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
