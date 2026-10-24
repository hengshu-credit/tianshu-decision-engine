import axios from 'axios'
import { flushPromises } from '@vue/test-utils'
import { initializeSystemBrand, loadSystemBrand, applySystemBrand, systemBrand, DEFAULT_BRAND } from '@/system/systemBrand'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'

const response = brand => ({ data: { code: '000000', data: { ...DEFAULT_BRAND, ...brand } } })
let stop
beforeEach(() => {
  vi.useFakeTimers()
  axios.get.mockReset().mockResolvedValue(response({}))
  setCurrentUser({ username: 'ordinary-user', permissions: ['project:view'] })
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
})
afterEach(() => {
  stop?.(); stop = null
  clearCurrentUser(); applySystemBrand(DEFAULT_BRAND)
  delete document.visibilityState
  vi.useRealTimers()
})

test('无系统权限的已登录用户自动同步已保存配置，隐藏页面暂停，返回时立即刷新', async () => {
  const removeHook = vi.fn()
  stop = initializeSystemBrand({ afterEach: vi.fn(() => removeHook) })
  await flushPromises()
  axios.get.mockResolvedValue(response({ watermarkEnabled: true, watermarkTransparency: 65 }))
  await vi.advanceTimersByTimeAsync(5000)
  expect(systemBrand.watermarkEnabled).toBe(true)
  expect(systemBrand.watermarkTransparency).toBe(65)
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
  const calls = axios.get.mock.calls.length
  await vi.advanceTimersByTimeAsync(10000)
  expect(axios.get).toHaveBeenCalledTimes(calls)
  axios.get.mockResolvedValue(response({ watermarkEnabled: false }))
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  document.dispatchEvent(new Event('visibilitychange')); await flushPromises()
  expect(systemBrand.watermarkEnabled).toBe(false)
  stop(); stop = null
  const stoppedCalls = axios.get.mock.calls.length
  await vi.advanceTimersByTimeAsync(10000)
  window.dispatchEvent(new Event('focus')); await flushPromises()
  expect(axios.get).toHaveBeenCalledTimes(stoppedCalls)
  expect(removeHook).toHaveBeenCalledOnce()
})

test('旧刷新响应不覆盖刚保存的配置，网络失败保留已生效值', async () => {
  let complete
  axios.get.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
  const pending = loadSystemBrand(true)
  applySystemBrand({ ...DEFAULT_BRAND, watermarkEnabled: true, watermarkContent: '刚保存 {用户名}' })
  complete(response({ watermarkEnabled: false }))
  await pending
  expect(systemBrand.watermarkEnabled).toBe(true)
  expect(systemBrand.watermarkContent).toBe('刚保存 {用户名}')
  axios.get.mockRejectedValueOnce(new Error('offline'))
  await loadSystemBrand(true)
  expect(systemBrand.watermarkContent).toBe('刚保存 {用户名}')
})
