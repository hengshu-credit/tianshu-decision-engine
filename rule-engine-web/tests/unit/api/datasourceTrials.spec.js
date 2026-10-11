vi.unmock('@/api/datasource')
import request from '@/api/request'
import { createApiConfigTrial, listApiConfigTrials, getApiConfigTrial, resumeApiConfigTrial, cancelApiConfigTrial } from '@/api/datasource'

test('草稿试运行通过独立会话ID继续/取消，查询保持GET，创建保留幂等键和原配置', async () => {
  vi.clearAllMocks()
  const body = { requestKey: '781a71be-a6aa-43c1-865e-70a6c9a5be24', config: { id: null, datasourceId: 2, apiCode: 'Exact_Code' }, params: { 原字段名: 'value' } }
  await createApiConfigTrial(body)
  await listApiConfigTrials()
  await getApiConfigTrial('trial-a')
  await resumeApiConfigTrial('trial-a')
  await cancelApiConfigTrial('trial-a')
  expect(request.mock.calls.map(([config]) => config)).toEqual([
    { url: '/rule/datasource/api-config/trials', method: 'post', data: body },
    { url: '/rule/datasource/api-config/trials', method: 'get' },
    { url: '/rule/datasource/api-config/trials/trial-a', method: 'get' },
    { url: '/rule/datasource/api-config/trials/trial-a/resume', method: 'post', data: {} },
    { url: '/rule/datasource/api-config/trials/trial-a/cancel', method: 'post', data: {} },
  ])
})
