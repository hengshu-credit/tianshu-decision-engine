import { describe, expect, it } from 'vitest'
import { compareResourceCase } from '@/utils/resourceTestCases'

describe('资源测试用例的业务预期', () => {
  it('区分查得、未查得、显式空值和缺少字段', () => {
    const checks = [{ path: ['body', 'found'], op: 'eq', value: true }, { path: ['body', 'income'], op: 'eq', value: null }]
    expect(compareResourceCase({ body: { income: null, found: true } }, checks)).toBe(true)
    expect(compareResourceCase({ body: { found: true } }, checks)).toBe(false)
    expect(compareResourceCase({ body: { income: null, found: false } }, checks)).toBe(false)
    expect(compareResourceCase({ success: false, body: { found: true, income: null } }, checks)).toBe(false)
  })
  it('验证报告路径和自然月边界', () => {
    const checks = [{ path: ['path'], op: 'reportPath' }, { path: ['days'], op: 'between', value: [365, 366] }]
    const path = '/reports/' + 'a'.repeat(32) + '.json'
    expect(compareResourceCase({ path, days: 366 }, checks)).toBe(true)
    expect(compareResourceCase({ path, days: 367 }, checks)).toBe(false)
    expect(compareResourceCase({ path: '/reports/other.json', days: 365 }, checks)).toBe(false)
    expect(compareResourceCase({ path, days: 365 }, [])).toBe(false)
  })
})
