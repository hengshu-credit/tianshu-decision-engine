import {
  buildSampleParamsFromCodes,
  collectActionDataInputCodes,
  collectScriptInputCodes
} from '@/utils/testSampleParams'

const refs = [
  { refCode: 'inputAmount', varType: 'NUMBER', varObj: { defaultValue: '100' } },
  { refCode: 'riskFlag', varType: 'BOOLEAN', varObj: {} },
  { refCode: 'resultAmount', varType: 'NUMBER', varObj: {} },
  { refCode: 'customerType', varType: 'STRING', varObj: {} }
]

describe('testSampleParams', () => {
  test('按变量默认值和类型生成样例参数', () => {
    expect(buildSampleParamsFromCodes(['inputAmount', 'riskFlag', 'customerType'], refs)).toEqual({
      inputAmount: '100',
      riskFlag: false,
      customerType: ''
    })
  })

  test('模型输出字段样例展开为模型底层输入字段', () => {
    const modelRefs = [{
      refCode: 'score_f1.score',
      refType: 'MODEL_OUTPUT',
      modelCode: 'score_f1',
      varType: 'DOUBLE',
      varObj: {},
      modelInputFields: [
        { scriptName: 'HYBASE_X115', fieldName: 'HYBASE_X115', fieldType: 'DOUBLE' },
        { scriptName: 'HYDK_X760', fieldName: 'HYDK_X760', fieldType: 'DOUBLE' },
        { scriptName: 'ignored_field', fieldName: 'ignored_field', fieldType: 'DOUBLE', status: 0 }
      ]
    }]

    expect(buildSampleParamsFromCodes(['score_f1.score'], modelRefs)).toEqual({
      score_f1_fields: {
        HYBASE_X115: 0,
        HYDK_X760: 0
      }
    })
  })

  test('脚本样例跳过单纯赋值左侧变量并保留右侧输入', () => {
    const codes = collectScriptInputCodes('resultAmount = inputAmount + 1\nif (riskFlag == true) { resultAmount = 0 }', refs)
    expect(Array.from(codes).sort()).toEqual(['inputAmount', 'riskFlag'])
  })

  test('动作块只采集输入表达式和条件变量，不采集赋值目标', () => {
    const codes = collectActionDataInputCodes([
      { type: 'assign', target: 'resultAmount', value: 'inputAmount + 1' },
      { type: 'if-block', branches: [{ type: 'if', condVar: 'riskFlag', condValue: true, actions: [] }] }
    ], refs)
    expect(Array.from(codes).sort()).toEqual(['inputAmount', 'riskFlag'])
  })

  test('source variables and model outputs expand to leaf test params', () => {
    const projectRefs = [
      { refCode: 'mobile', refType: 'VARIABLE', varType: 'STRING', varObj: { varSource: 'INPUT', defaultValue: '13800000000' } },
      { refCode: 'request.idNo', refType: 'DATA_OBJECT', varType: 'STRING', varObj: { defaultValue: 'ID001' } },
      { refCode: 'riskScore', refType: 'VARIABLE', varType: 'NUMBER', varObj: { varSource: 'API', sourceConfig: '{"paramMapping":{"idNo":"$.request.idNo","mobile":"${mobile}"}}' } },
      { refCode: 'dbScore', refType: 'VARIABLE', varType: 'NUMBER', varObj: { varSource: 'DB', sourceConfig: '{"params":["$.request.idNo"]}' } },
      { refCode: 'blackHit', refType: 'VARIABLE', varType: 'BOOLEAN', varObj: { varSource: 'LIST', sourceConfig: '{"queryField":"mobile"}' } },
      { refCode: 'ageCalc', refType: 'VARIABLE', varType: 'NUMBER', varObj: { varSource: 'COMPUTED', sourceConfig: '{"expression":"riskScore + dbScore"}' } },
      {
        refCode: 'scoreCard.score',
        refType: 'MODEL_OUTPUT',
        modelCode: 'scoreCard',
        varType: 'NUMBER',
        varObj: {},
        modelInputFields: [
          { scriptName: 'ageCalc', fieldType: 'NUMBER' },
          { scriptName: 'blackHit', fieldType: 'BOOLEAN' }
        ]
      }
    ]

    expect(buildSampleParamsFromCodes(['scoreCard.score'], projectRefs)).toEqual({
      mobile: '13800000000',
      request: { idNo: 'ID001' }
    })
  })

  test('sample params prefer example values and skip constants', () => {
    const projectRefs = [
      { refCode: 'mobile_no', refType: 'VARIABLE', varType: 'STRING', varObj: { varSource: 'INPUT', defaultValue: '13800000000', exampleValue: '14519144474' } },
      { refCode: 'EMPTY_VALUE', refType: 'CONSTANT', varType: 'STRING', varObj: { varSource: 'CONSTANT', defaultValue: '' } }
    ]

    expect(buildSampleParamsFromCodes(['mobile_no', 'EMPTY_VALUE'], projectRefs)).toEqual({
      mobile_no: '14519144474'
    })
  })

  test('API source variables collect mapping and API request dependencies together', () => {
    const projectRefs = [
      { refCode: 'idcard_no', refType: 'VARIABLE', varType: 'STRING', varObj: { varSource: 'INPUT' } },
      { refCode: 'clientAppName', refType: 'VARIABLE', varType: 'STRING', varObj: { varSource: 'INPUT' } },
      {
        refCode: 'bdzr_result',
        refType: 'VARIABLE',
        varType: 'OBJECT',
        varObj: {
          varSource: 'API',
          sourceConfig: JSON.stringify({
            paramMapping: { idNo: '$.idcard_no' },
            queryConfig: { app: '${clientAppName}' }
          })
        }
      }
    ]

    expect(buildSampleParamsFromCodes(['bdzr_result'], projectRefs)).toEqual({
      idcard_no: '',
      clientAppName: ''
    })
  })
})
