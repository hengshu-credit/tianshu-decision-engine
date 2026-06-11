// tests/unit/utils/varDisplay.spec.js
import { formatVarDisplay, makeRefLabel } from '@/utils/varDisplay'

describe('formatVarDisplay', () => {
  test('普通变量返回 "中文标签 monospace编码" 格式', () => {
    const result = formatVarDisplay({ varLabel: '年龄', varCode: 'age' })
    expect(result).toBe('年龄 age')
  })

  test('仅 label 无 code 时返回 label', () => {
    const result = formatVarDisplay({ varLabel: '年龄', varCode: '' })
    expect(result).toBe('年龄')
  })

  test('仅 code 无 label 时返回 code', () => {
    const result = formatVarDisplay({ varLabel: '', varCode: 'age' })
    expect(result).toBe('age')
  })

  test('无 label 和 code 时返回空字符串', () => {
    const result = formatVarDisplay({ varLabel: '', varCode: '' })
    expect(result).toBe('')
  })

  test('null/undefined 输入返回空字符串', () => {
    expect(formatVarDisplay(null)).toBe('')
    expect(formatVarDisplay(undefined)).toBe('')
  })

  test('数据对象字段（objectLabel 存在）返回 "中文标签 monospace对象路径.编码" 格式', () => {
    const result = formatVarDisplay({ varLabel: '金额', varCode: 'amount', objectLabel: 'TaxRequest' })
    expect(result).toBe('金额 TaxRequest.amount')
  })

  test('数据对象字段无 varCode 时返回 label', () => {
    const result = formatVarDisplay({ varLabel: '金额', varCode: '', objectLabel: 'TaxRequest' })
    expect(result).toBe('金额')
  })

  test('数据对象字段无 label 时返回 "对象路径.编码"', () => {
    const result = formatVarDisplay({ varLabel: '', varCode: 'amount', objectLabel: 'TaxRequest' })
    expect(result).toBe('TaxRequest.amount')
  })

  test('常量返回 "中文标签 monospace编码" 格式', () => {
    const result = formatVarDisplay({ varLabel: '最大重试次数', varCode: 'MAX_RETRY_COUNT' })
    expect(result).toBe('最大重试次数 MAX_RETRY_COUNT')
  })

  test('空格分隔：label 和 code 之间有空格', () => {
    const result = formatVarDisplay({ varLabel: '测试', varCode: 'test' })
    expect(result).toBe('测试 test')
  })
})

describe('makeRefLabel', () => {
  test('返回 { label, code } 对象', () => {
    const result = makeRefLabel({ varLabel: '年龄', varCode: 'age', scriptName: 'age' })
    expect(result).toEqual({ label: '年龄', code: 'age' })
  })

  test('无 scriptName 时 fallback 到 varCode', () => {
    const result = makeRefLabel({ varLabel: '年龄', varCode: 'age' })
    expect(result).toEqual({ label: '年龄', code: 'age' })
  })

  test('无 scriptName 和 varCode 时 code 为空', () => {
    const result = makeRefLabel({ varLabel: '年龄' })
    expect(result).toEqual({ label: '年龄', code: '' })
  })

  test('scriptName 优先于 varCode', () => {
    const result = makeRefLabel({ varLabel: '年龄', varCode: 'age_old', scriptName: 'age' })
    expect(result).toEqual({ label: '年龄', code: 'age' })
  })

  test('null/undefined 输入返回默认空对象', () => {
    expect(makeRefLabel(null)).toEqual({ label: '', code: '' })
    expect(makeRefLabel(undefined)).toEqual({ label: '', code: '' })
  })

  test('空对象返回默认空对象', () => {
    const result = makeRefLabel({})
    expect(result).toEqual({ label: '', code: '' })
  })
})