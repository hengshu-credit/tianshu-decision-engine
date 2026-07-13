import { formatConstantValue, hasConstantValue } from '@/utils/constantValue'

describe('constantValue', () => {
  test('格式化内置特殊值', () => {
    expect(formatConstantValue('', 'STRING')).toBe("''")
    expect(formatConstantValue('null', 'OBJECT')).toBe('null')
    expect(formatConstantValue('[]', 'LIST')).toBe('[]')
    expect(formatConstantValue('{}', 'MAP')).toBe('{}')
    expect(formatConstantValue('Infinity', 'DOUBLE')).toBe('Infinity')
    expect(formatConstantValue('-Infinity', 'DOUBLE')).toBe('-Infinity')
  })

  test('STRING 允许真正空字符串，其他类型拒绝空白', () => {
    expect(hasConstantValue('', 'STRING')).toBe(true)
    expect(hasConstantValue(null, 'STRING')).toBe(false)
    expect(hasConstantValue('', 'NUMBER')).toBe(false)
    expect(hasConstantValue('  ', 'MAP')).toBe(false)
    expect(hasConstantValue('0', 'NUMBER')).toBe(true)
  })
})
