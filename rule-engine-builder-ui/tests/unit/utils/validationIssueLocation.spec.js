import { validationRepairHint } from '@/utils/validationIssueLocation'

test('修复建议使用当前设计器真实按钮名', () => {
  expect(validationRepairHint({ code: 'COMPILE_FAILED' })).toContain('点击“编译”')
  expect(validationRepairHint({ code: 'COMPILE_FAILED' })).not.toContain('暂存并检查')
  expect(validationRepairHint({ code: 'UNKNOWN' })).not.toContain('暂存并检查')
})
