import request from './request'

export const getSystemSettings = () => request.get('/rule/system/settings')
export const saveSystemBrand = data => request.put('/rule/system/settings/brand', data)
export function uploadSystemAsset(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/rule/system/settings/assets', data)
}
export const testSystemMail = data => request.post('/rule/system/settings/mail/test', data, { timeout: 40000 })
export const getSystemSettingsHistory = params => request.get('/rule/system/settings/history', { params })
