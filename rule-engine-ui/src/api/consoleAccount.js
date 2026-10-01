import request from './request'

export function listConsoleAccounts() {
  return request.get('/rule/console/accounts')
}

export function getConsoleAccount(id) {
  return request.get(`/rule/console/accounts/${id}`)
}

export function saveConsoleAccount(data) {
  return request.post('/rule/console/accounts', data)
}

export function saveConsoleAccountPermissions(id, data) {
  return request.put(`/rule/console/accounts/${id}/permissions`, data)
}

export function changeConsoleAccountStatus(id, status) {
  return request.put(`/rule/console/accounts/${id}/status`, null, {
    params: { status }
  })
}

export function resetConsoleAccountPassword(id, password) {
  return request.put(`/rule/console/accounts/${id}/password`, { password })
}

export function listConsoleRoles() {
  return request.get('/rule/console/roles')
}

export function saveConsoleRole(data) {
  return request.post('/rule/console/roles', data)
}

export function changeConsoleRoleStatus(id, status) {
  return request.put(`/rule/console/roles/${id}/status`, null, {
    params: { status }
  })
}

export function listConsolePermissions() {
  return request.get('/rule/console/permissions')
}
