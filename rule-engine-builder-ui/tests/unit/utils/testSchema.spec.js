import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
  buildNestedSchemaParams
} from '@/utils/testSchema'

describe('testSchema', () => {
  test('统一规则和模型测试字段结构并保留嵌套路径', () => {
    const schema = normalizeTestSchema({ data: {
      inputs: [
        { refId: 1, refType: 'DATA_OBJECT', code: 'bank_card_no', scriptName: 'bankcard.bank_card_no', label: '银行卡信息/银行卡号', valueType: 'STRING' },
        { refId: 2, refType: 'VARIABLE', code: 'age', scriptName: 'age', label: '年龄', valueType: 'INTEGER', validValues: '[18,55]' }
      ],
      sampleParams: { bankcard: { bank_card_no: '6222' }, age: 22 }
    } })

    const fields = schemaFieldsToTestFields(schema.inputs)
    expect(fields[0]).toMatchObject({
      varId: 1,
      refType: 'DATA_OBJECT',
      fieldName: 'bankcard.bank_card_no',
      scriptName: 'bankcard.bank_card_no',
      fieldLabel: '银行卡信息/银行卡号',
      fieldType: 'STRING'
    })
    expect(fields[1].validValues).toEqual([18, 55])
    expect(flattenSchemaSample(fields, schema.sampleParams)).toEqual({
      'bankcard.bank_card_no': '6222',
      age: 22
    })
  })

  test('扁平表单值可以重建统一嵌套 JSON', () => {
    const fields = [
      { fieldName: 'score_f1_fields.HYBASE_X115' },
      { fieldName: 'age' }
    ]
    expect(buildNestedSchemaParams(fields, {
      'score_f1_fields.HYBASE_X115': 0,
      age: 55
    })).toEqual({
      score_f1_fields: { HYBASE_X115: 0 },
      age: 55
    })
  })

  test('flattenSchemaSample parses field defaults by type when sample is missing', () => {
    const fields = [
      { fieldName: 'age', fieldType: 'NUMBER', defaultValue: '55' },
      { fieldName: 'emptyValue', fieldType: 'OBJECT', defaultValue: 'null' },
      { fieldName: 'emptyString', fieldType: 'STRING', defaultValue: '""' },
      { fieldName: 'emptyObject', fieldType: 'OBJECT', defaultValue: '{}' },
      { fieldName: 'emptyList', fieldType: 'LIST', defaultValue: '[]' }
    ]

    expect(flattenSchemaSample(fields, {})).toEqual({
      age: 55,
      emptyValue: null,
      emptyString: '',
      emptyObject: {},
      emptyList: []
    })
  })
})
