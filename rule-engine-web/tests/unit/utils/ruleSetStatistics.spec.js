import { ruleSetCoverageHint } from '@/utils/ruleSetStatistics'

test('统计缺口分别说明待补齐、未记录、损坏和截断，不能将缺口当成零命中', () => {
  const text = ruleSetCoverageHint({ pendingCount: 5, noTraceCount: 8, invalidCount: 2, truncatedCount: 3 })
  expect(text).toContain('5 条日志正在后台补齐')
  expect(text).toContain('当前统计尚未完整')
  expect(text).toContain('8 条未记录追踪')
  expect(text).toContain('2 条追踪无法解析')
  expect(text).toContain('3 条追踪已截断')
  expect(text).toContain('所选项目与时间范围内')
})

test('旧响应或无缺口不制造数据缺失提示', () => {
  expect(ruleSetCoverageHint(null)).toBe('')
  expect(ruleSetCoverageHint({ pendingCount: 0, noTraceCount: 0, invalidCount: 0, truncatedCount: 0 })).toBe('')
})
