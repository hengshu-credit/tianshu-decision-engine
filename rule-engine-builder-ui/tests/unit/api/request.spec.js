import { ElMessage } from 'element-plus'

const axiosHarness = vi.hoisted(() => {
  const requestUse = vi.fn()
  const responseUse = vi.fn()
  return {
    requestUse,
    responseUse,
    service: {
      interceptors: {
        request: { use: requestUse },
        response: { use: responseUse },
      },
    },
  }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => axiosHarness.service),
  },
}))
vi.unmock('@/api/request')

await import('@/api/request')

describe('request 错误提示', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    ElMessage.error.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('短时间内按消息去重，交错错误不会让同一消息重复提示', async () => {
    const [, rejectResponse] = axiosHarness.responseUse.mock.calls[0]
    const networkError = {
      message: '后端服务暂不可用',
      config: { url: '/rule/project/list' },
    }

    await expect(rejectResponse(networkError)).rejects.toBe(networkError)
    await expect(
      rejectResponse({ ...networkError, message: '权限不足' })
    ).rejects.toMatchObject({ message: '权限不足' })
    expect(ElMessage.error).toHaveBeenCalledTimes(2)

    await expect(rejectResponse(networkError)).rejects.toBe(networkError)
    await expect(rejectResponse(networkError)).rejects.toBe(networkError)
    expect(ElMessage.error).toHaveBeenCalledTimes(2)

    vi.advanceTimersByTime(1501)
    await expect(rejectResponse(networkError)).rejects.toBe(networkError)
    expect(ElMessage.error).toHaveBeenCalledTimes(3)
  })
})
