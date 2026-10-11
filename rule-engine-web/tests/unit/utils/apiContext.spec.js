import { apiStagePaths, apiProtocolFields, isAvailableApiPath } from '@/utils/apiContext'
import { buildFieldTree, fieldAncestors } from '@/utils/fieldPicker'

describe('API 各阶段可用上下文', () => {
  const config = { requestFields: [{ id: 'input-1', path: 'customer.age' }], steps: [
    { id: 'first', name: '提交', responseBranches: [{ sample: { task: { id: 'T' } } }] },
    { id: 'poll', name: '查询' }, { id: 'callback', name: '通知', type: 'CALLBACK' },
  ] }
  it('请求和本步骤响应只能读取已完成步骤；回调 URL 在执行前已注册', () => {
    const paths = apiStagePaths(config, 'STEP_REQUEST', 1)
    expect(isAvailableApiPath('steps.first.response.body.task.id', paths)).toBe(true)
    expect(isAvailableApiPath('steps.poll.body', paths)).toBe(false)
    expect(isAvailableApiPath('steps.callback.body', paths)).toBe(false)
    expect(isAvailableApiPath('callbacks.callback.url', paths)).toBe(true)
    expect(isAvailableApiPath('input.__apiFields.input-1', paths)).toBe(true)
    const response = apiStagePaths(config, 'STEP_RESPONSE', 1, { sample: { result: [{ score: 0 }] } })
    expect(response.some(path => path.value === 'response.body.result[0].score')).toBe(true)
    expect(isAvailableApiPath('steps.poll.response', response)).toBe(false)
    expect(apiStagePaths(config, 'RESULT').some(path => path.value === 'steps.callback.response')).toBe(true)
  })
  it('协议字段补齐层级且保留特殊字段名；结构父级不会被当作可取值内容', () => {
    const paths = [{ value: 'steps.first.response.body["原始.字段"]', label: '原始.字段', type: 'NUMBER' }]
    const fields = apiProtocolFields(paths, '画像 API', 7)
    expect(fields.some(field => field.varCode === 'steps.first.response.body["原始')).toBe(false)
    const tree = buildFieldTree(fields)
    const child = [...tree.nodes.values()].find(node => node.code === paths[0].value)
    expect(fieldAncestors(child).map(node => node.code)).toContain('steps.first.response.body')
    expect(fields.find(field => field.varCode === 'steps').disabled).toBe(true)
    expect(child.selectable).toBe(true)
  })
  it('请求阶段不提供响应，重试阶段不提供尚未形成的计费结果', () => {
    expect(apiStagePaths(config, 'REQUEST')).toEqual([])
    expect(isAvailableApiPath('status.billed', apiStagePaths(config, 'RETRY'))).toBe(false)
    expect(isAvailableApiPath('headers.RetryAfter', apiStagePaths(config, 'STEP_CONDITION'))).toBe(true)
  })
  it('已完成步骤提供组装字段，实际对象父级仍可直接选择', () => {
    const execution = { steps: [{ id: 'first', responseBranches: [{ mode: 'VALUE', sample: { payload: { score: 80 } }, value: { kind: 'PATH', value: 'response.body.payload' } }] }, { id: 'second' }] }
    const paths = apiStagePaths(execution, 'STEP_REQUEST', 1)
    expect(paths.find(item => item.value === 'steps.first.body.score')?.type).toBe('NUMBER')
    const fields = apiProtocolFields([{ value: 'body', type: 'OBJECT' }, { value: 'body.data.score', type: 'NUMBER' }])
    expect(fields.find(item => item.varCode === 'body.data').disabled).toBe(false)
  })
})
