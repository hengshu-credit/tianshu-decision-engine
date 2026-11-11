const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ get }) } }))

import { fetchConsoleConfig, getPublicConsoleResource } from '@/api/consoleBootstrap'

test('登录配置首次网络失败后可以重试，成功后共享缓存', async () => {
  const config = { code: '000000', data: { loginEnabled: true } }
  get.mockRejectedValueOnce(new Error('temporarily unavailable'))
  get.mockResolvedValueOnce({ data: { code: 503 } })
  get.mockResolvedValueOnce({ data: config })
  expect(await fetchConsoleConfig()).toBeNull()
  expect(await fetchConsoleConfig()).toEqual({ code: 503 })
  const [first, second] = await Promise.all([fetchConsoleConfig(), fetchConsoleConfig()])
  expect(first).toEqual(config)
  expect(second).toEqual(config)
  expect(await fetchConsoleConfig()).toEqual(config)
  expect(get).toHaveBeenCalledTimes(3)
})

test('公开接口仅对暂时性网络或网关故障重试一次', async () => {
  const unavailable = Object.assign(new Error('gateway unavailable'), { response: { status: 503 } })
  get.mockClear().mockRejectedValueOnce(unavailable).mockResolvedValueOnce({ data: { code: '000000' } })
  await expect(getPublicConsoleResource('/auth/console/brand')).resolves.toHaveProperty('data.code', '000000')
  expect(get).toHaveBeenCalledTimes(2)
  get.mockClear().mockRejectedValueOnce(Object.assign(new Error('denied'), { response: { status: 403 } }))
  await expect(getPublicConsoleResource('/auth/console/brand')).rejects.toThrow('denied')
  expect(get).toHaveBeenCalledTimes(1)
})
