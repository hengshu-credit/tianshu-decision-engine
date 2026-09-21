vi.unmock('@/router')

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('axios', () => ({ default: { create: () => ({ get }) } }))

import { fetchConsoleConfig } from '@/router'

test('登录配置首次网络失败后可以重试，成功后共享缓存', async () => {
  const config = { code: 200, data: { loginEnabled: true } }
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
