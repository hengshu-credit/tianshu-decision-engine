import {
  compileConditionExpression,
  conditionOperatorRequiresValue,
  findConditionOperator,
  getConditionOperatorGroups,
  getConditionOperatorOptions,
  isSourceStatusOperator,
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
    expect(normalizeVarType('PROBABILITY')).toBe('NUMBER')
    expect(normalizeVarType('VECTOR')).toBe('LIST')
    expect(normalizeVarType('MODEL')).toBe('OBJECT')
    expect(normalizeConditionOperator('>', 'Integer')).toBe('>')
  })

  test('日期时间别名统一使用日期比较符', () => {
    ['DATE', 'DATETIME', 'TIMESTAMP', 'LOCALDATE', 'LOCALDATETIME'].forEach(type => {
      expect(normalizeVarType(type)).toBe('DATE')
      expect(getConditionOperatorOptions(type).map(item => item.value)).toContain('between')
    })
  })

  test('API 变量按实际值类型展示值操作符，并追加独立的调用和缓存状态操作符', () => {
    const groups = getConditionOperatorGroups('DOUBLE', {
      refType: 'VARIABLE',
      varSource: 'API'
    })

    expect(groups.map(group => group.label)).toEqual(['值判断', '来源状态'])
    expect(groups[0].options.map(item => item.value)).toContain('between')
    expect(groups[1].options.map(item => item.value)).toEqual(expect.arrayContaining([
      'source_success',
      'source_error',
      'source_timeout',
      'source_fallback',
      'source_cache_enabled',
      'source_cache_disabled',
      'source_cache_hit',
      'source_cache_miss',
      'source_cache_unavailable',
      'source_origin_live',
      'source_origin_cache',
      'source_origin_stale_cache'
    ]))
    expect(groups[1].options.every(item => item.noValue)).toBe(true)
  })

  test('API 来源也可从 sourceType 元数据识别', () => {
    const groups = getConditionOperatorGroups('DOUBLE', {
      refType: 'VARIABLE', sourceType: 'API'
    })
    expect(groups[1].options.map(item => item.value)).toContain('source_cache_hit')
  })

  test('各来源只展示其适用的状态操作符，普通输入变量不展示来源状态组', () => {
    const values = (refType, varSource) => getConditionOperatorOptions('STRING', { refType, varSource }).map(item => item.value)

    expect(values('VARIABLE', 'DB')).toEqual(expect.arrayContaining(['source_has_data', 'source_no_data', 'source_timeout']))
    expect(values('VARIABLE', 'LIST')).toEqual(expect.arrayContaining(['source_match_hit', 'source_match_miss']))
    expect(values('MODEL_OUTPUT')).toEqual(expect.arrayContaining(['source_output_present', 'source_output_missing']))
    expect(values('DATA_OBJECT')).toEqual(expect.arrayContaining(['source_field_present', 'source_field_missing', 'source_field_invalid']))
    expect(getConditionOperatorGroups('STRING', { refType: 'VARIABLE', varSource: 'INPUT' })).toHaveLength(1)
    expect(isSourceStatusOperator('source_cache_hit')).toBe(true)
    expect(isSourceStatusOperator('is_null')).toBe(false)
    expect(conditionOperatorRequiresValue('source_cache_hit', 'STRING', { refType: 'VARIABLE', varSource: 'API' })).toBe(false)
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
