jest.unmock('@/api/definition')

import request from '@/api/request'
import { executeRule } from '@/api/definition'

describe('definition API', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('规则执行允许 ONNX 长链路完成推理', async () => {
    const data = { definitionId: 16, params: { face_image: 'base64' } }

    await executeRule(data)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/execute',
      method: 'post',
      data,
      timeout: 3 * 60 * 1000
    })
  })
})
