import { recommendLiteralType, literalValidationError, literalTypeCompatible } from '@/utils/literalType'

describe('阈值类型推荐', () => {
  test('数值选择覆盖整数、小数和大数的 NUMBER，编码和歧义日期保留原文', () => {
    for (const value of ['0', '-0.25', '9007199254740993.123456789', '1e20']) expect(recommendLiteralType(value)).toBe('NUMBER')
    for (const value of ['00123', '2026-10-10', ' 12 ', '低风险', '']) expect(recommendLiteralType(value)).toBe('STRING')
    expect(recommendLiteralType('false')).toBe('BOOLEAN')
    expect(recommendLiteralType('[1,')).toBe('LIST')
    expect(recommendLiteralType('{"a":')).toBe('OBJECT')
  })
  test('位置约束优先，明确选择的类型只校验不自动替换', () => {
    expect(recommendLiteralType('123', 'STRING')).toBe('STRING')
    expect(recommendLiteralType('123.4', 'DECIMAL')).toBe('NUMBER')
    expect(literalTypeCompatible('NUMBER', 'INTEGER')).toBe(true)
    expect(literalValidationError({ valueType: 'NUMBER', value: 'hello' })).toBe('请输入有效数值')
    expect(literalValidationError({ valueType: 'STRING', value: '00123' })).toBe('')
    expect(literalValidationError({ valueType: 'LIST', value: '{}' })).toBe('请输入合法的 JSON 数组')
    expect(literalValidationError({ valueType: 'NUMBER', value: '9007199254740993.123456789' })).toBe('')
  })
})
