jest.unmock('@/api/model')

import request from '@/api/request'
import { executeModel } from '@/api/model'

describe('model API', () => {
  beforeEach(() => {
    jest.clearAllMocks()
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
})
