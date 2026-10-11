vi.unmock('@/api/model')

import request from '@/api/request'
import { metadataResponse } from '@/utils/metadataResponse'
import {
  analyzeModelImpact,
  deleteModel,
  executeModel,
  getModelVersionTestContext,
  publishModel,
  replaceModel,
  saveTestParams
} from '@/api/model'

describe('model API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('发布已有编辑草稿时复用同一申请，不创建冲突的启用申请', async () => {
    const draft = { id: 91, action: 'UPDATE', status: 'EDITING', resourceId: 10, snapshotDigest: 'shown' }
    request.get.mockResolvedValueOnce({ data: draft })
    const response = await publishModel(10)
    expect(response.data).toEqual(draft)
    expect(request.get).toHaveBeenCalledWith('/rule/governance/workspaces/MODEL/10', metadataResponse)
    expect(request).toHaveBeenCalledTimes(1)
  })

  test('模型已有删除草稿时发布按钮不能把删除申请送审', async () => {
    request.get.mockResolvedValueOnce({ data: { id: 92, action: 'DELETE', status: 'EDITING' } })
    await expect(publishModel(10)).rejects.toThrow('删除或停用草稿')
    expect(request).toHaveBeenCalledTimes(1)
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

  test('指定版本的字段、执行与样例保存使用同一真实模型ID和版本', async () => {
    const id = '9007199254740993'
    await getModelVersionTestContext(id, 3)
    await executeModel(id, { age: 25 }, 72000, 3)
    await saveTestParams(id, '{"age":25}', 3)
    expect(request).toHaveBeenNthCalledWith(1, { url: `/rule/model/version/${id}/3/test-context`, method: 'get' })
    expect(request).toHaveBeenNthCalledWith(2, { url: `/rule/model/execute/${id}`, method: 'post', data: { age: 25 }, timeout: 72000, params: { version: 3 } })
    expect(request).toHaveBeenNthCalledWith(3, { url: `/rule/model/version/${id}/3/testParams`, method: 'post', data: { testParams: '{"age":25}' } })
  })

  test('模型测试原始数值 JSON 直接发送，避免 axios 再次转换精度', async () => {
    const raw = '{"customerId":9007199254740993,"rate":0.1234567890123456789}'
    await executeModel('9007199254740993', raw, 72000, 1)
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      data: raw, headers: { 'Content-Type': 'application/json' }, params: { version: 1 }
    }))
  })

  test('模型删除由后端判断是否需要审批，文件替换仍携带影响分析令牌', async () => {
    await analyzeModelImpact(10, 'DELETE')
    await deleteModel(10, 'impact-token')
    const data = new FormData()
    data.append('file', new Blob(['model']))
    await replaceModel(10, data, 'replace-token')
    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/model/impact/10', method: 'post', params: { action: 'DELETE' }
    })
    expect(request).toHaveBeenNthCalledWith(2, '/rule/governance/resources/MODEL/10/delete', undefined, metadataResponse)
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
      ...metadataResponse,
      url: '/rule/model/10',
      method: 'get'
    })
    expect(request).toHaveBeenNthCalledWith(2, '/rule/governance/workspaces/MODEL/10', metadataResponse)
    const draft = request.mock.calls[2][1]
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

    expect(request).toHaveBeenCalledTimes(2)
    expect(request).toHaveBeenNthCalledWith(2, '/rule/governance/workspaces/MODEL/10', metadataResponse)
  })
})
