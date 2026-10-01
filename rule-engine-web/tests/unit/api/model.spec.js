vi.unmock('@/api/model')

import request from '@/api/request'
import {
  analyzeModelImpact,
  deleteModel,
  executeModel,
  replaceModel,
  saveTestParams
} from '@/api/model'

describe('model API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('模型测试请求使用页面传入的可见超时时间', async () => {
    await executeModel(10, { image: 'base64' }, 95000)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/model/execute/10',
      method: 'post',
      data: { image: 'base64' },
      timeout: 95000
    })
  })

  test('模型删除进入统一审批，文件替换仍携带影响分析令牌', async () => {
    await analyzeModelImpact(10, 'DELETE')
    await deleteModel(10, 'impact-token')
    const data = new FormData()
    data.append('file', new Blob(['model']))
    await replaceModel(10, data, 'replace-token')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/model/impact/10', method: 'post', params: { action: 'DELETE' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'MODEL',
        resourceId: 10,
        action: 'DELETE'
      })
    )
    expect(request.mock.calls[2][0].url).toBe('/rule/model/replace/10')
    expect(request.mock.calls[2][0].data.get('impactToken')).toBe('replace-token')
  })

  test('模型测试参数作为模型草稿提交，不直接修改生效模型', async () => {
    request.mockResolvedValueOnce({
      data: {
        id: 10,
        modelCode: 'risk_model',
        modelConfig: '{"executionProvider":"CPU","testParams":"{}"}'
      }
    })

    await saveTestParams(10, '{"score":720}')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/model/10',
      method: 'get'
    })
    const draft = request.mock.calls[1][1]
    const snapshot = JSON.parse(draft.snapshotJson)
    expect(draft).toMatchObject({
      resourceType: 'MODEL',
      resourceId: 10,
      action: 'UPDATE'
    })
    expect(snapshot).not.toHaveProperty('testParams')
    expect(JSON.parse(snapshot.modelConfig)).toEqual({
      executionProvider: 'CPU',
      testParams: '{"score":720}',
      testParamsSource: 'SAVED'
    })
  })

  test('模型配置损坏时拒绝覆盖并且不创建审批草稿', async () => {
    request.mockResolvedValueOnce({
      data: { id: 10, modelCode: 'risk_model', modelConfig: '{invalid json' }
    })

    await expect(saveTestParams(10, '{"score":720}')).rejects.toThrow(
      '模型配置格式异常'
    )

    expect(request).toHaveBeenCalledTimes(1)
  })
})
