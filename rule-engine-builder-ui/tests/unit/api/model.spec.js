vi.unmock('@/api/model')

import request from '@/api/request'
import { analyzeModelImpact, deleteModel, executeModel, replaceModel } from '@/api/model'

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

  test('模型破坏性操作必须携带影响分析令牌', async () => {
    await analyzeModelImpact(10, 'DELETE')
    await deleteModel(10, 'impact-token')
    const data = new FormData()
    data.append('file', new Blob(['model']))
    await replaceModel(10, data, 'replace-token')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/model/impact/10', method: 'post', params: { action: 'DELETE' }
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/model/10', method: 'delete', params: { impactToken: 'impact-token' }
    })
    expect(request.mock.calls[2][0].url).toBe('/rule/model/replace/10')
    expect(request.mock.calls[2][0].data.get('impactToken')).toBe('replace-token')
  })
})
