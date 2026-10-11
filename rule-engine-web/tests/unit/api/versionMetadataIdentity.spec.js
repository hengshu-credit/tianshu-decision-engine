vi.unmock('@/api/governance')
vi.unmock('@/api/transfer')
import request from '@/api/request'
import * as governance from '@/api/governance'
import * as transfer from '@/api/transfer'
import { parseMetadataResponse } from '@/utils/metadataResponse'

beforeEach(() => vi.clearAllMocks())

test('迁移 ID 映射字典保持精确，不转换业务数值', () => {
  const result = parseMetadataResponse('{"resourceIdMapping":{"RULE:7":9007199254740993},"fieldIdMapping":{"DATA_OBJECT:9":{"1":9007199254740995}},"amount":123.45}')
  expect(result.resourceIdMapping['RULE:7']).toBe('9007199254740993')
  expect(result.fieldIdMapping['DATA_OBJECT:9']['1']).toBe('9007199254740995')
  expect(result.amount).toBe(123.45)
  expect(() => parseMetadataResponse(JSON.parse('{"resourceIdMapping":{"RULE:7":9007199254740993}}'))).toThrow('精度')
})

test.each([
  ['读取审批', () => governance.getGovernanceRequest('9007199254740993'), 'get'],
  ['保存草稿', () => governance.saveGovernanceDraft('9007199254740993', {}), 'put'],
  ['创建草稿', () => governance.createGovernanceDraft({}), 'post'],
  ['提交审批', () => governance.submitGovernanceRequest('9007199254740993', '发布', 'digest'), 'post'],
  ['历史恢复', () => governance.restoreGovernanceVersion('VARIABLE', '7', '9007199254740993'), 'post'],
])('%s 返回的大整数版本/审批标识保持精确', async (_title, call, method) => {
  request[method].mockImplementationOnce(async (...args) => {
    const config = method === 'get' ? args[1] : args[2]
    const raw = '{"data":{"id":9007199254740993,"sourceVersionId":9007199254740995}}'
    return (config?.transformResponse || [JSON.parse]).reduce((value, transform) => transform(value), raw)
  })
  const response = await call()
  expect(response.data.id).toBe('9007199254740993')
  expect(response.data.sourceVersionId).toBe('9007199254740995')
})

test.each([
  ['迁移预检', () => transfer.previewResourceTransfer(new File(['zip'], 'versions.zip'), {})],
  ['迁移结果', () => transfer.importResourceTransfer(new File(['zip'], 'versions.zip'), {})],
])('%s 中目标版本快照 ID 不丢失精度', async (_title, call) => {
  request.mockImplementationOnce(async config => {
    const raw = '{"data":{"binding":{"resourceId":9007199254740993,"versionId":9007199254740995,"expectedSnapshotId":9007199254740997}}}'
    return (config.transformResponse || [JSON.parse]).reduce((value, transform) => transform(value), raw)
  })
  const response = await call()
  expect(response.data.binding).toEqual({ resourceId: '9007199254740993', versionId: '9007199254740995', expectedSnapshotId: '9007199254740997' })
})
