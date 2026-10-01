import { vi } from 'vitest'
import { emptyExecution, newApiId, parseExecution, sampleFields, validateExecution, prepareResponseSample, samplePathOptions } from '@/utils/apiExecution'

describe('统一外数配置', () => {
  it('万字段征信响应默认整体绑定，保留结构与原始字段名供搜索', () => {
    const securityComputingResult = Object.fromEntries(Array.from({ length: 9937 }, (_, index) => [`QY_FIELD_${index}`, index % 3 === 0 ? '' : index % 3 === 1 ? 0 : false]))
    const sample = { code: '0000', data: { securityComputingResult } }
    const prepared = prepareResponseSample(sample)
    expect(prepared.mode).toBe('VALUE')
    expect(prepared.outputFields).toEqual([])
    expect(prepared.sample.data.securityComputingResult).toEqual(securityComputingResult)
    const paths = samplePathOptions(sample)
    expect(paths.find(item => item.value === 'response.body.data.securityComputingResult.QY_FIELD_9936').type).toBe('STRING')
    expect(paths.find(item => item.value === 'response.body.data.securityComputingResult.QY_FIELD_2').type).toBe('BOOLEAN')
  })
  it('样例生成结构保留供应商字段名并绑定原始响应路径', () => {
    const rows = sampleFields({ Data: { Mobile_Status: 1 }, list: [{ id: 2 }] })
    expect(rows.map(row => row.path)).toEqual(['Data.Mobile_Status', 'list'])
    expect(rows[0].value.value).toBe('response.body.Data.Mobile_Status')
    expect(rows[0].id).not.toBe(rows[1].id)
  })
  it.each([1, 9937])('重新导入 %i 个字段的样例保留已有整体取值并清空逐字段配置', fieldCount => {
    const sample = { code: '0000', data: Object.fromEntries(Array.from({ length: fieldCount }, (_, index) => [`QY_${index}`, index])) }
    const value = { kind: 'PATH', value: 'response.body.data' }
    const prepared = prepareResponseSample(sample, { mode: 'VALUE', value, outputFields: [{ id: 'stale', path: 'code' }] })
    expect(prepared.mode).toBe('VALUE')
    expect(prepared.value).toEqual(value)
    expect(prepared.outputFields).toEqual([])
    expect(prepared.sample).toEqual(sample)
  })
  it('明确切换逐字段生成时保留同路径字段 ID 并清空整体取值', () => {
    const sample = { data: { score: 0, approved: false } }
    const previous = { mode: 'VALUE', value: { kind: 'PATH', value: 'response.body.data' }, outputFields: [{ id: 'score-id', path: 'data.score' }, { id: 'removed-id', path: 'data.removed' }] }
    const prepared = prepareResponseSample(sample, previous, 'FIELDS')
    expect(prepared.mode).toBe('FIELDS')
    expect(prepared.value).toBeNull()
    expect(prepared.outputFields.map(field => field.path)).toEqual(['data.score', 'data.approved'])
    expect(prepared.outputFields[0].id).toBe('score-id')
    expect(prepared.outputFields[0].value.value).toBe('response.body.data.score')
    expect(prepared.outputFields[1].id).not.toBe('score-id')
    expect(prepared.outputFields[1].id).not.toBe('removed-id')
  })
  it('多步配置需要异步模式并校验目标', () => {
    const spec = emptyExecution()
    spec.steps.push({ id: 'one', type: 'HTTP', endpointUrl: '/request' })
    expect(validateExecution(spec, 'SYNC')).toContain('异步')
    expect(validateExecution(spec, 'ASYNC')).toBe('')
    spec.steps.push({ id: 'one', type: 'HTTP', endpointUrl: '/poll' })
    expect(validateExecution(spec, 'ASYNC')).toContain('ID')
  })
  it('多步配置为每个步骤初始化独立异常条件树', () => {
    const spec = parseExecution(JSON.stringify({ version: 2, steps: [{ id: 'provider', type: 'HTTP' }] }))
    expect(spec.steps[0].exceptionConditionTree).toEqual({ type: 'group', operator: 'AND', children: [] })
  })
  it('损坏配置不会让 API 编辑器直接崩溃，并保留解析错误提示', () => {
    const spec = parseExecution('{broken')
    expect(spec.__parseError).toBeTruthy()
    expect(spec.requestFields).toEqual([])
  })
  it('在保存前阻止统一链路的前向步骤引用和重复目标路径', () => {
    const spec = emptyExecution()
    spec.steps.push({ id: 'first', type: 'HTTP', endpointUrl: '/first', requestFields: [{ id: 'a', location: 'JSON', path: 'score', value: { kind: 'LITERAL', valueType: 'NUMBER', value: '1' } }] })
    spec.steps.push({ id: 'second', type: 'HTTP', endpointUrl: '/second', requestFields: [{ id: 'b', location: 'JSON', path: 'score', value: { kind: 'PATH', value: 'steps.third.body.score' } }] })
    expect(validateExecution(spec, 'ASYNC')).toContain('尚未完成')
    spec.steps[1].requestFields[0].value = { kind: 'LITERAL', valueType: 'NUMBER', value: '2' }
    spec.steps[1].requestFields.push({ id: 'c', location: 'JSON', path: 'score', value: { kind: 'LITERAL', valueType: 'NUMBER', value: '3' } })
    expect(validateExecution(spec, 'ASYNC')).toContain('目标路径重复')
  })
  it('非安全页面缺少 randomUUID 时仍可生成链路 ID', () => {
    vi.stubGlobal('crypto', { getRandomValues: bytes => bytes.fill(0) })
    try {
      expect(newApiId()).toBe('00000000-0000-4000-8000-000000000000')
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
