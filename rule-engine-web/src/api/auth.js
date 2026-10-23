import request from './request'

/**
 * 是否启用控制台用户名密码登录（与后端 rule-engine.console-login.enabled 一致）。
 */
export function getConsoleAuthConfig() {
  return request.get('/auth/console/config')
}

/**
 * 提交用户名密码建立会话。
 */
export function consoleLogin(data) {
  return request.post('/auth/console/login', data)
}

export function consoleRegister(data) {
  return request.post('/auth/console/register', data)
}

export function initializeConsoleAdministrator(data) {
  return request.post('/auth/console/initialize', data)
}

export function verifyConsoleRegistration(data) {
  return request.post('/auth/console/register/verify', data)
}

export function resendConsoleRegistration(data) {
  return request.post('/auth/console/register/resend', data)
}

export function resendConsoleEmailVerification() {
  return request.post('/auth/console/profile/email-verification')
}

export function requestConsolePasswordRecovery(data) {
  return request.post('/auth/console/password-recovery/request', data)
}

export function confirmConsolePasswordRecovery(data) {
  return request.post('/auth/console/password-recovery/confirm', data)
}

export function emergencyResetConsolePassword(data) {
  return request.post('/auth/console/emergency-password-reset', data)
}

export function getConsoleProfile() {
  return request.get('/auth/console/profile')
}

export function updateConsoleProfile(data) {
  return request.put('/auth/console/profile', data)
}

export function changeConsolePassword(data) {
  return request.post('/auth/console/password/change', data)
}

/**
 * 注销当前会话。
 */
export function consoleLogout() {
  return request.post('/auth/console/logout')
}

/**
 * 查询当前登录用户；未登录时请求会失败（401）。
 */
export function getConsoleMe() {
  return request.get('/auth/console/me')
}

/**
 * 查询当前登录用户保存的主题配置。
 */
export function getConsoleThemePreference() {
  return request.get('/auth/console/preferences/theme')
}

/**
 * 保存当前登录用户的主题配置。
 */
export function saveConsoleThemePreference(data) {
  return request.put('/auth/console/preferences/theme', data)
}
