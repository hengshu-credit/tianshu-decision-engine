import {
  compileConditionExpression,
  conditionOperatorRequiresValue,
  getConditionOperatorOptions,
  normalizeConditionOperator,
  normalizeVarType
} from '@/constants/conditionOperators'

describe('conditionOperators', () => {
  test('不同字段类型返回不同操作符集合', () => {
    const numberOps = getConditionOperatorOptions('NUMBER').map(item => item.value)
    const stringOps = getConditionOperatorOptions('STRING').map(item => item.value)
    const booleanOps = getConditionOperatorOptions('BOOLEAN').map(item => item.value)
    const listOps = getConditionOperatorOptions('LIST').map(item => item.value)

    expect(numberOps).toContain('between')
    expect(numberOps).not.toContain('starts_with')
    expect(stringOps).toContain('contains')
    expect(booleanOps).toContain('is_true')
    expect(booleanOps).not.toContain('>')
    expect(listOps).toContain('contains_all')
  })

  test('兼容 Java 常见数字类型名', () => {
    expect(normalizeVarType('Integer')).toBe('NUMBER')
    expect(normalizeConditionOperator('>', 'Integer')).toBe('>')
  })

  test('无右值操作符不要求输入值', () => {
    expect(conditionOperatorRequiresValue('is_null', 'STRING')).toBe(false)
    expect(conditionOperatorRequiresValue('contains', 'STRING')).toBe(true)
  })

  test('生成字符串和区间表达式', () => {
    expect(compileConditionExpression('name', 'STRING', 'contains', 'VIP', 'CONST'))
      .toBe('containsValue(name, "VIP")')
    expect(compileConditionExpression('age', 'NUMBER', 'between', '18,60', 'CONST'))
      .toBe('(age >= 18 && age <= 60)')
  })
})
