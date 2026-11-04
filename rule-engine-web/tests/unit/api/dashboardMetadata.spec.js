vi.unmock('axios')
vi.unmock('@/api/dashboard')

import axios from 'axios'
import request from '@/api/request'
import * as api from '@/api/dashboard'

test.each([
  () => api.getDashboardSettings({}),
  () => api.saveDashboardSettings({}, { mappings: {} }),
  () => api.deleteDashboardSetting('AMOUNT', {}),
  () => api.getDashboardDefaults(),
  () => api.saveDashboardDefaults({ version: 1, mappings: {} }),
])('看板配置响应保留完整资源 ID', async invoke => {
  request.mockImplementation(config => axios.request({ ...config,
    adapter: async actual => ({ data: '{"data":{"fields":[{"mapping":{"refId":9007199254740993}}],"defaultVersion":1}}',
      status: 200, statusText: 'OK', headers: {}, config: actual }),
  }).then(response => response.data))
  const response = await invoke()
  expect(response.data.fields[0].mapping.refId).toBe('9007199254740993')
  expect(response.data.defaultVersion).toBe(1)
})
