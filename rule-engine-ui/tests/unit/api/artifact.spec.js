vi.unmock('@/api/artifact')

import request from '@/api/request'
import { deployArtifact, downloadArtifact, importArtifact } from '@/api/artifact'

describe('artifact API', () => {
  beforeEach(() => vi.clearAllMocks())

  test('下载、导入和部署保留摘要与显式组件 ID 绑定', async () => {
    await downloadArtifact(7)
    const file = new Blob(['artifact'])
    await importArtifact(file, 'package-digest')
    await deployArtifact({
      artifactId: 7,
      targetDefinitionId: 16,
      bindings: { 'binding:DB:9': 101 }
    })

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/artifact/7/download', method: 'get', responseType: 'blob'
    })
    expect(request.mock.calls[1][0].data.get('file').size).toBe(file.size)
    expect(request.mock.calls[1][0].params.expectedPackageDigest).toBe('package-digest')
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/rule/artifact/deploy', method: 'post',
      data: { artifactId: 7, targetDefinitionId: 16, bindings: { 'binding:DB:9': 101 } }
    })
  })
})
