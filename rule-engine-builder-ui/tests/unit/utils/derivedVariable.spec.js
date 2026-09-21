import { describe, it, expect } from 'vitest'
import { createDerivedConfig, derivedCurrentInputs, historyAggregateOptions, validateDerivedConfig } from '@/utils/derivedVariable'

const ref = id => ({ kind: 'REFERENCE', refType: 'VARIABLE', refId: id, code: `field${id}`, valueType: 'STRING', resolved: true })

describe('衍生变量配置', () => {
  it('只将本次请求侧暴露为入参，不泄漏历史匹配字段和属性', () => {
    const config = { ...createDerivedConfig(), mode: 'HISTORY', valueField: ref(8), subjectFields: [ref(9)],
      steps: [{ inputs: [ref(1)], fields: [ref(2)] }, { fromFields: [ref(3)], fields: [ref(4)] }] }
    expect(derivedCurrentInputs(config)).toEqual([ref(1)])
    expect(validateDerivedConfig(config)).toBe('')
  })
  it('按属性类型限制统计方法', () => {
    expect(historyAggregateOptions('STRING').map(item => item.value)).not.toContain('SUM')
    expect(historyAggregateOptions('STRING').map(item => item.value)).toContain('STRING_LENGTH')
    expect(historyAggregateOptions('NUMBER').map(item => item.value)).toContain('SAMPLE_VARIANCE')
    expect(historyAggregateOptions('NUMBER').map(item => item.value)).not.toContain('STRING_LENGTH')
  })
  it('不允许不完整的衍生配置被保存', () => {
    expect(validateDerivedConfig(createDerivedConfig())).toBe('请配置衍生表达式')
    const config = { ...createDerivedConfig(), mode: 'HISTORY', aggregate: 'DISTINCT_COUNT' }
    expect(validateDerivedConfig(config)).toBe('请选择主体 key 字段')
    config.subjectFields = [ref(1), ref(2)]
    expect(validateDerivedConfig(config)).toBe('')
    config.steps = [{ inputs: [], fields: [ref(1)] }]
    expect(validateDerivedConfig(config)).toContain('第 1 层')
  })
})
