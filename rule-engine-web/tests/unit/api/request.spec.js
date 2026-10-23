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

const { isRequestErrorNotified } = await import('@/api/request')

describe('request 错误提示', () => {
  test('下载接口的 JSON Blob 错误保留平台码和详细原因', async () => {
    vi.useRealTimers()
    const [, rejectResponse] = axiosHarness.responseUse.mock.calls[0]
    const error = { config: { responseType: 'blob' }, response: { status: 403, data: new Blob([
      JSON.stringify({ code: '301002', success: false, message: '无操作权限', detail: '无权导出当前项目' }),
    ], { type: 'application/json' }) } }
    await expect(rejectResponse(error)).rejects.toMatchObject({ code: '301002', message: '无操作权限：无权导出当前项目' })
  })
  test('平台成功码只接受六位字符串，详细原因保留在失败响应中', async () => {
    const [resolveResponse] = axiosHarness.responseUse.mock.calls[0]
    const data = { code: '000000', success: true, message: '成功', data: { id: 1 } }
    expect(await resolveResponse({ data, config: {} })).toBe(data)
    const response = { data: { code: '100001', success: false, message: '入参校验失败', detail: '年龄必须大于 0' }, config: {} }
    const error = await resolveResponse(response).catch(reason => reason)
    expect(error.message).toContain('年龄必须大于 0')
    expect(error.response).toBe(response)
  })
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

  test('请求层展示错误后会标记拒绝原因，供页面避免重复提示', async () => {
    const [resolveResponse] = axiosHarness.responseUse.mock.calls[0]
    const rejection = resolveResponse({
      config: { url: '/rule/definition/36/revisions/12/submit' },
      data: { code: '100001', success: false, message: '入参校验失败', detail: '无法序列化规范 JSON' },
    })
    const error = await rejection.catch(reason => reason)

    expect(error).toMatchObject({ message: '入参校验失败：无法序列化规范 JSON' })
    expect(isRequestErrorNotified(error)).toBe(true)
  })

  test('业务错误保留原始响应数据供发布前校验页面展示详情', async () => {
    const [resolveResponse] = axiosHarness.responseUse.mock.calls[0]
    const response = {
      config: { url: '/rule/definition/14/revisions/1/preflight' },
      data: {
        code: '700002',
        success: false,
        message: '发布前校验未通过',
        data: {
          valid: false,
          errors: [{ code: 'FROZEN_REVISION_FIELD_SNAPSHOT_INVALID', message: '字段快照无效' }],
          warnings: [],
        },
      },
    }

    const error = await resolveResponse(response).catch(reason => reason)

    expect(error.response).toBe(response)
    expect(error.response.data.data.errors[0].code)
      .toBe('FROZEN_REVISION_FIELD_SNAPSHOT_INVALID')
  })
})
