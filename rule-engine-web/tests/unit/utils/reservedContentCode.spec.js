import { describe, expect, it } from 'vitest'
import { reservedCodeError, reservedCodeRule, resourceCodeIssue } from '@/utils/reservedContentCode'

describe('内容编码', () => {
  it('在表单校验阶段拒绝完整保留编码，允许普通业务字段和包含关键字的名称', () => {
    for (const name of ['class', 'IF', ' select ', 'sUm', 'decision_pass', 'BUILTIN_EMAIL', '_dim_1_2']) {
      let error
      reservedCodeRule.validator({}, name, value => { error = value })
      expect(error?.message).toBe(reservedCodeError(name))
      expect(error).toBeInstanceOf(Error)
    }
    for (const code of ['', 'name', 'mobile_no', 'Summary', 'If_Result']) expect(reservedCodeError(code)).toBe('')
  })

  it('版本编辑检查声明位置，允许脚本、样例和对内置内容的引用', () => {
    const snapshot = { objectCode: 'Customer', fields: [{ varCode: 'name', refVariableId: 1 }],
      sourceContent: { varCode: 'class' } }
    expect(resourceCodeIssue('DATA_OBJECT', snapshot)).toBeNull()
    snapshot.fields[0].scriptName = 'True_Value'
    expect(resourceCodeIssue('DATA_OBJECT', snapshot).message).toContain('fields[0].scriptName')
    const source = { funcCode: 'numBetween', scope: 'GLOBAL', paramsJson: '[{"name":"min"}]' }
    expect(resourceCodeIssue('FUNCTION', source, source)).toBeNull()
    expect(resourceCodeIssue('FUNCTION', source).field).toBe('funcCode')
    expect(resourceCodeIssue('EXPERIMENT', { groups: [{ groupCode: 'while' }] }).field).toBe('groups')
  })
})
