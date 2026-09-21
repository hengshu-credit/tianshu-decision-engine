import {
  buildQlCompletionItems,
  buildQlHover,
  buildQlSignatureHelp,
} from '@/utils/qlEditorIntelligence'

const refs = [
  {
    refCode: 'contact.address',
    varLabel: '联系地址',
    varType: 'OBJECT',
    _varId: 21,
    _refType: 'DATA_OBJECT',
  },
  {
    refCode: 'contact.address.city',
    varLabel: '城市',
    varType: 'STRING',
    _varId: 22,
    _refType: 'DATA_OBJECT',
  },
  {
    refCode: 'riskScore',
    varLabel: '风险分',
    varType: 'NUMBER',
    _varId: 30,
    _refType: 'VARIABLE',
  },
]

const functions = [
  {
    funcCode: 'between',
    funcName: '区间判断',
    returnType: 'BOOLEAN',
    paramsJson: '[{"name":"value","type":"NUMBER"},{"name":"min","type":"NUMBER"},{"name":"max","type":"NUMBER"}]',
  },
]

describe('qlEditorIntelligence', () => {
  test('函数补全携带明确选择的函数 ID，不按名称推断引用', () => {
    expect(buildQlCompletionItems([], [{ id: 77, funcCode: 'versioned' }], 'vers')[0].stableReference)
      .toEqual({ varId: 77, refType: 'FUNCTION', refCode: 'versioned' })
    expect(buildQlCompletionItems([], [{ funcCode: 'unbound' }], '')[0].stableReference).toBeNull()
  })
  test('成员补全只插入光标后的剩余路径并携带稳定引用', () => {
    const items = buildQlCompletionItems(refs, functions, 'contact.')
    const city = items.find((item) => item.refCode === 'contact.address.city')

    expect(city).toMatchObject({
      label: 'address.city',
      insertText: 'address.city',
      valueType: 'STRING',
      stableReference: {
        refCode: 'contact.address.city',
        varId: 22,
        refType: 'DATA_OBJECT',
      },
    })
  })

  test('悬浮提示使用光标所在的最长字段编码', () => {
    expect(buildQlHover('result = contact.address.city;', 27, refs)).toEqual({
      refCode: 'contact.address.city',
      label: '城市',
      valueType: 'STRING',
      refType: 'DATA_OBJECT',
    })
  })

  test('函数参数提示返回签名并计算当前参数位置', () => {
    expect(buildQlSignatureHelp('result = between(riskScore, 10, ', functions)).toEqual({
      functionCode: 'between',
      label: 'between(value: NUMBER, min: NUMBER, max: NUMBER): BOOLEAN',
      activeParameter: 2,
      parameters: [
        { label: 'value: NUMBER', documentation: '' },
        { label: 'min: NUMBER', documentation: '' },
        { label: 'max: NUMBER', documentation: '' },
      ],
    })
  })
})
