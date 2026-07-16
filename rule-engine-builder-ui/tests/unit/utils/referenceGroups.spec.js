import {
  filterReferenceGroups,
  groupReferenceOptions,
  referenceChildDisplayName,
  referenceChildRelativePath,
  referenceGroupKey
} from '@/utils/referenceGroups'

function option(id, code, label, category, sourceCode, sourceLabel, refType) {
  const source = category === 'model'
    ? { modelCode: sourceCode, modelLabel: sourceLabel }
    : { objectCode: sourceCode, objectLabel: sourceLabel }
  return {
    _varId: id,
    _refType: refType,
    varCode: code,
    varLabel: `${sourceLabel}/${label}`,
    varLabelText: `${sourceLabel}/${label}`,
    varType: 'NUMBER',
    _ref: { category, refType, ...source }
  }
}

describe('referenceGroups', () => {
  const options = [
    option(1, 'LoanApply.amount', '申请金额', 'object', 'LoanApply', '借款申请', 'DATA_OBJECT'),
    option(2, 'LoanApply.term', '借款期限', 'object', 'LoanApply', '借款申请', 'DATA_OBJECT'),
    option(3, 'Profile.age', '年龄', 'object', 'Profile', '客户画像', 'DATA_OBJECT'),
    option(4, 'risk.score', '评分', 'model', 'risk', '风险模型', 'MODEL_OUTPUT')
  ]

  test('数据对象按对象分组且子字段保留稳定引用', () => {
    const groups = groupReferenceOptions(options, 'object')

    expect(groups.map(group => group.groupCode)).toEqual(['LoanApply', 'Profile'])
    expect(groups[0].groupKey).toBe('object:LoanApply')
    expect(groups[0].children[0]).toMatchObject({ _varId: 1, _refType: 'DATA_OBJECT' })
    expect(referenceGroupKey(groups[0])).toBe('object:LoanApply')
  })

  test('相对路径和名称只移除精确的对象前缀', () => {
    expect(referenceChildRelativePath(options[0], 'object')).toBe('amount')
    expect(referenceChildDisplayName(options[0], 'object')).toBe('申请金额')
    expect(referenceChildRelativePath({ ...options[0], varCode: 'LoanApplication.amount' }, 'object')).toBe('LoanApplication.amount')
  })

  test('搜索对象字段时保留分组但只返回匹配子字段', () => {
    const groups = groupReferenceOptions(options, 'object')
    const filtered = filterReferenceGroups(groups, 'amount')

    expect(filtered).toHaveLength(1)
    expect(filtered[0].children.map(child => child.varCode)).toEqual(['LoanApply.amount'])
  })
})
