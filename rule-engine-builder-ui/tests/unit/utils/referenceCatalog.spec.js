import {
  buildDetailReferenceMap,
  buildDetailReferenceState,
  buildPickerOptions,
  buildReferenceCatalog,
  resolveDetailReference
} from '@/utils/referenceCatalog'

describe('referenceCatalog', () => {
  test('统一构造普通变量、常量、数据对象完整路径和模型输出', () => {
    const variables = [
      { id: 1, varLabel: '年龄', varCode: 'age', scriptName: 'age', varType: 'INTEGER', varSource: 'INPUT' },
      { id: 2, varLabel: '空字符串', varCode: 'EMPTY_STRING', scriptName: 'EMPTY_STRING', varType: 'STRING', varSource: 'CONSTANT', defaultValue: '' }
    ]
    const objectTree = [{
      object: { id: 10, objectLabel: '银行卡信息', objectCode: 'bankcard', scriptName: 'bankcard' },
      flatVariables: [{ id: 11, varLabel: '银行卡号', varCode: 'bank_card_no', scriptName: 'bank_card_no', varType: 'STRING' }]
    }]
    const models = [{
      id: 20,
      modelName: '反欺诈评分F1',
      modelCode: 'score_f1',
      inputFields: [{ scriptName: 'HYBASE_X115', fieldType: 'DOUBLE' }],
      outputFields: [{ id: 21, fieldLabel: '评分', fieldName: 'score', scriptName: 'score', fieldType: 'DOUBLE' }]
    }]

    const catalog = buildReferenceCatalog(variables, objectTree, models)
    const bankCard = catalog.refs.find(item => item.refCode === 'bankcard.bank_card_no')
    const score = catalog.refs.find(item => item.refCode === 'score_f1.score')
    const emptyString = catalog.refs.find(item => item.refType === 'CONSTANT')

    expect(bankCard.refLabel).toEqual({ label: '银行卡信息/银行卡号', code: 'bankcard.bank_card_no' })
    expect(bankCard.displayName).toBe('银行卡信息/银行卡号 bankcard.bank_card_no')
    expect(score.refLabel).toEqual({ label: '反欺诈评分F1/评分', code: 'score_f1.score' })
    expect(emptyString).toMatchObject({ id: 2, refCode: 'EMPTY_STRING', constantValue: '' })
    expect(catalog.groups.map(group => group.key)).toEqual(['variable', 'constant', 'dataObject', 'model'])

    const detailState = buildDetailReferenceState(catalog)
    expect(detailState.items.find(item => item.id === 2)).toMatchObject({
      refType: 'CONSTANT',
      constantValue: ''
    })
    expect(detailState.items.find(item => item.id === 11)).toMatchObject({
      varCode: 'bankcard.bank_card_no',
      varCodeText: 'bankcard.bank_card_no',
      varLabel: '银行卡信息/银行卡号 bankcard.bank_card_no'
    })

    expect(buildPickerOptions(catalog).find(item => item._varId === 11)).toMatchObject({
      varCode: 'bankcard.bank_card_no',
      _refType: 'DATA_OBJECT',
      _ref: { category: 'object', refType: 'DATA_OBJECT' }
    })

    const referenceMap = buildDetailReferenceMap(detailState)
    expect(resolveDetailReference(referenceMap, {
      refType: 'DATA_OBJECT',
      varId: 11,
      scriptName: 'stale.display.code'
    })).toMatchObject({
      varLabelText: '银行卡信息/银行卡号',
      varCodeText: 'bankcard.bank_card_no'
    })
    expect(resolveDetailReference(referenceMap, {
      refType: 'DATA_OBJECT',
      scriptName: 'bankcard.bank_card_no'
    })).toBeNull()
    expect(resolveDetailReference(referenceMap, {
      varId: 11,
      scriptName: 'bankcard.bank_card_no'
    })).toBeNull()
  })
})
