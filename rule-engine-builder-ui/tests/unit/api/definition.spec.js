jest.unmock('@/api/definition')

import request from '@/api/request'
import { executeRule, refreshFields } from '@/api/definition'

describe('definition API', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('规则执行允许 ONNX 长链路完成推理', async () => {
    const data = { definitionId: 16, params: { face_image: 'base64' } }

    await executeRule(data, 90000)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/execute',
      method: 'post',
      data,
      timeout: 90000
    })
  })

  test('刷新规则字段以纯文本发送原始模型 JSON', async () => {
    const modelJson = '{"nodes":[],"edges":[]}'

    await refreshFields(16, modelJson)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/refreshFields/16',
      method: 'post',
      data: modelJson,
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
    })
  })
})
