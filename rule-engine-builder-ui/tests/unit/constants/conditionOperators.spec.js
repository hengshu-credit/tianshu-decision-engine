import {
  compileConditionExpression,
  conditionOperatorRequiresValue,
  findConditionOperator,
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
    expect(stringOps).toContain('regex_match')
    expect(stringOps).toContain('in_array')
    expect(listOps).toContain('array_element_contains')
    expect(listOps).toContain('array_element_starts_with')
    expect(getConditionOperatorOptions('MAP').map(item => item.value)).toContain('has_value')
    expect(getConditionOperatorOptions('MAP').map(item => item.value)).toContain('size_gte')
  })

  test('操作符声明右值表达式上下文和适用类型', () => {
    expect(findConditionOperator('regex_match', 'STRING').rightContext).toBe('READ_EXPRESSION')
    expect(findConditionOperator('in_array', 'STRING').rightValueType).toBe('LIST')
    expect(findConditionOperator('in_list', 'STRING').rightContext).toBe('LIST_QUERY_CONFIG')
    expect(findConditionOperator('in_list', 'LIST')).toBeNull()
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

  test('生成正则、数组元素、字典值和集合长度表达式', () => {
    expect(compileConditionExpression('name', 'STRING', 'regex_match', '^VIP', 'CONST'))
      .toBe('regexMatchValue(name, "^VIP")')
    expect(compileConditionExpression('code', 'STRING', 'in_array', 'allowedCodes', 'VAR'))
      .toBe('containsValue(allowedCodes, code)')
    expect(compileConditionExpression('tags', 'LIST', 'array_element_contains', 'VIP', 'CONST'))
      .toBe('containsElementValue(tags, "VIP")')
    expect(compileConditionExpression('attributes', 'MAP', 'has_value', 'gold', 'CONST'))
      .toBe('hasMapValue(attributes, "gold")')
    expect(compileConditionExpression('items', 'LIST', 'size_gte', '3', 'CONST'))
      .toBe('sizeOfValue(items) >= 3')
  })
})
