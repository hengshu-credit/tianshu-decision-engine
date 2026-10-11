vi.unmock('axios')
vi.unmock('@/api/model')

import axios from 'axios'
import request from '@/api/request'
import * as api from '@/api/model'

let wire
beforeEach(() => {
  vi.clearAllMocks()
  wire = '{"code":200,"data":{"id":9007199254740993,"modelId":9007199254740994,"varId":9007199254740995,"modelConfig":"{\\"testParams\\":\\"{\\\\\\"customerId\\\\\\":9007199254740997}\\"}"}}'
  request.mockImplementation(config => axios.request({ ...config,
    adapter: async actual => ({ data: wire, status: 200, statusText: 'OK', headers: { 'content-type': 'application/json' }, config: actual })
  }).then(response => response.data))
})

test.each([
  ['详情', () => api.getModel('9007199254740993')],
  ['列表', () => api.listModels({})],
  ['项目模型选择', () => api.listAllModelsByProject('7')],
  ['版本目录', () => api.listVersions('9007199254740993')]
])('模型%s原始HTTP元数据保留相邻大ID，不解析样例字符串', async (_name, invoke) => {
  const result = await invoke()
  expect(result.data.id).toBe('9007199254740993')
  expect(result.data.modelId).toBe('9007199254740994')
  expect(result.data.varId).toBe('9007199254740995')
  expect(result.data.modelConfig).toContain('9007199254740997')
})

test('测试context不套全对象ID转换，业务customerId仍为数值', async () => {
  wire = '{"code":200,"data":{"modelId":"9007199254740993","schema":{"sampleParams":{"customerId":123},"sampleParamsJson":"{\\"customerId\\":9007199254740997}"}}}'
  const result = await api.getModelVersionTestContext('9007199254740993', 1)
  expect(result.data.schema.sampleParams.customerId).toBe(123)
  expect(result.data.schema.sampleParamsJson).toBe('{"customerId":9007199254740997}')
})
