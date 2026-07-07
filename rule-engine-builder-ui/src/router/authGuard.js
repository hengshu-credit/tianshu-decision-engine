export function createConsoleAuthGuard(loadConfig, loadCurrentUser) {
  return async function consoleAuthGuard(to, from, next) {
    var body = await loadConfig()
    if (to.path === '/login') {
      if (body && body.code === 200 && body.data && !body.data.loginEnabled) {
        // 登录已禁用，直接跳转（使用 Vue Router 而非 location 避免页面刷新）
        return next({ path: to.query.redirect || '/project', replace: true })
      }
      return next()
    }
    if (!body || body.code !== 200 || !body.data || !body.data.loginEnabled) {
      return next()
    }
    try {
      var me = await loadCurrentUser()
      if (me && me.code === 200 && me.data && me.data.username) {
        return next()
      }
    } catch (e) { /* ignore */ }
    return next({ path: '/login', query: { redirect: to.fullPath }, replace: true })
  }
}
