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
})
