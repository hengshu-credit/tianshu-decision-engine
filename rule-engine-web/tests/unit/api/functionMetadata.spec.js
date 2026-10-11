vi.unmock('axios')
vi.unmock('@/api/function')

import axios from 'axios'
import request from '@/api/request'
import * as api from '@/api/function'

const paramsJson = '[{"name":"data","type":"OBJECT","example":{"customerId":9007199254740997}}]'
beforeEach(() => {
  vi.clearAllMocks()
  const wire = `{"code":200,"data":{"id":9007199254740993,"projectId":9007199254740994,"functionId":9007199254740995,"paramsJson":${JSON.stringify(paramsJson)}}}`
  request.get.mockImplementation((url, config = {}) => axios.request({ ...config, url, method: 'get',
    adapter: async actual => ({ data: wire, status: 200, statusText: 'OK', headers: { 'content-type': 'application/json' }, config: actual }),
  }).then(response => response.data))
})

test.each([
  ['详情', () => api.getFunctionById('9007199254740993')],
  ['列表', () => api.listFunctions({})],
  ['项目分页', () => api.listFunctionsByProject('7', {})],
  ['项目选择', () => api.listAllFunctionsByProject('7')],
  ['版本目录', () => api.listVersions('9007199254740993')],
  ['版本快照', () => api.getVersion('9007199254740993', 1)],
  ['版本对比', () => api.compareVersions('9007199254740993', 1, 2)],
])('函数%s保留相邻Long身份且不改参数样例字符串', async (_label, invoke) => {
  const response = await invoke()
  expect(response.data.id).toBe('9007199254740993')
  expect(response.data.projectId).toBe('9007199254740994')
  expect(response.data.functionId).toBe('9007199254740995')
  expect(response.data.paramsJson).toBe(paramsJson)
})
