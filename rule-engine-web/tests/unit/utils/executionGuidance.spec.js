import { executionImpact } from '@/utils/executionImpact'
import { executionDiagnosis, diagnosticResource } from '@/utils/executionDiagnosis'
import { parseDesignerModelJson } from '@/utils/designerModelJson'

test('执行影响按所选结构列出外部取值和名单写入，保留大整数 ID', () => {
  const id = '9007199254740993'
  const result = executionImpact([{ sourceType: 'DB', refId: '7', label: '收入查询' }], { actions: [{ type: 'api-call', apiId: id }, { type: 'list-write', listIds: ['8'], operation: 'REMOVE' }, { type: 'func-call' }] })
  expect(result.calls).toContainEqual({ type: 'API', id, label: `外数接口 #${id}` })
  expect(result.calls.some(call => call.label === '数据库查询：收入查询')).toBe(true)
  expect(result.writes).toEqual([{ id: '8', operation: 'REMOVE' }])
  expect(result.dynamic).toBe(true)
  expect(executionImpact([], {}, 'SCRIPT').dynamic).toBe(true)
})

test('从原始设计 JSON 读取 API 和名单动作时引用 ID 不丢失精度', () => {
  const model = parseDesignerModelJson('{"actions":[{"type":"api-call","apiId":9007199254740993},{"type":"list-write","listIds":[9007199254740995],"operation":"MERGE"}]}')
  const impact = executionImpact([], model)
  expect(impact.calls[0].id).toBe('9007199254740993')
  expect(impact.writes[0].id).toBe('9007199254740995')
})

test('排障只使用追踪证据，名单排队不能显示为已完成', () => {
  const result = executionDiagnosis({ traceInfo: JSON.stringify([{ traceKind: 'RULE', schemaVersion: 2, ruleId: '9', modelJson: '{}', events: [{ type: 'MODULE_CALL', moduleType: 'API', resourceId: '7', status: 'FAILED', errorMessage: '供应商超时' }, { type: 'LIST_ACTION', result: { status: 'QUEUED', taskId: 'task-1' } }], inputParams: { type: 'LIST_ACTION', result: { status: 'SUCCESS' } } }]) })
  expect(result.failures).toHaveLength(1)
  expect(result.failures[0].message).toBe('供应商超时')
  expect(result.snapshot).toBe(true)
  expect(result.effects).toHaveLength(1)
  expect(result.effects[0].message).toContain('尚不能证明已完成')
  expect(result.retry).toContain('不要重新触发整条规则')
  expect(diagnosticResource(result.failures[0])).toEqual({ permission: 'datasource:view', path: '/datasource/api/7' })
  expect(diagnosticResource({ moduleType: 'API', resourceId: '../other' })).toBeNull()
})

test('缺失或损坏的历史快照不虚构失败阶段或安全重试结论', () => {
  const result = executionDiagnosis({ traceInfo: 'invalid', success: 0 })
  expect(result.snapshot).toBe(false)
  expect(result.failures).toEqual([])
  expect(result.retry).toContain('核对供应商请求、账单与名单写入')
})
