import { apiStagePaths, apiProtocolFields, isAvailableApiPath } from '@/utils/apiContext'
import { buildFieldTree, fieldAncestors, visibleFieldRows, filterFieldTree } from '@/utils/fieldPicker'

describe('API 各阶段可用上下文', () => {
  it('分类直接展示协议字段，不再增加当前 API 的目录行；类型过滤仍能找到子字段', () => {
    const fields = apiProtocolFields([{ value: 'response.body.Payload.Score', label: '评分', type: 'NUMBER' }])
    const tree = buildFieldTree(fields)
    expect(visibleFieldRows(tree, new Set(), 'api-response', '').map(row => row.node.code)).toEqual(['response'])
    expect([...tree.nodes.keys()].some(key => key.startsWith('group:'))).toBe(false)
    expect(visibleFieldRows(filterFieldTree(buildFieldTree(fields), 'NUMBER'), new Set(), 'api-response', 'Score').at(-1).node.selectable).toBe(true)
  })
  it('可变路径列表更新后重新生成目录，冻结目录可共享缓存', () => {
    const paths = [{ value: 'body.alpha', label: '原字段' }]
    const first = apiProtocolFields(paths)
    paths.push({ value: 'body.beta', label: '新增字段' })
    expect(apiProtocolFields(paths).some(field => field.varCode === 'body.beta')).toBe(true)
    expect(first.some(field => field.varCode === 'body.beta')).toBe(false)
    const frozen = Object.freeze(paths)
    expect(apiProtocolFields(frozen)).toBe(apiProtocolFields(frozen))
  })
  it('当前 API 按请求头、请求体、Query、Cookies、鉴权和响应直接分类', () => {
    const fields = apiProtocolFields(['request.headers', 'request.json', 'request.query', 'request.cookies', 'authentication', 'response.body'].map(value => ({ value, type: 'OBJECT' })))
    expect(fields.filter(field => field.varCode.includes('.')).map(field => field._ref.category)).toEqual(expect.arrayContaining(['api-headers', 'api-body', 'api-query', 'api-cookies', 'api-response']))
    expect(fields.find(field => field.varCode === 'authentication')._ref.category).toBe('api-auth')
    expect(buildFieldTree(fields).roots.some(node => node.code === 'http')).toBe(false)
  })
  it('轮询条件能选择供应商自定义响应结构和根数组，不臆造 code/msg/data', () => {
    const spec = { steps: [{ id: 'poll', responseBranches: [{ sample: [{ Payload: { State: 'DONE' } }] }] }] }
    const paths = apiStagePaths(spec, 'STEP_CONDITION', 0)
    expect(paths.some(field => field.value === 'body[0].Payload.State')).toBe(true)
    expect(paths.some(field => field.value === 'body.code')).toBe(false)
  })
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
