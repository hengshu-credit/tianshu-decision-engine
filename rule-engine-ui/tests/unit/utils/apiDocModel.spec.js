import { serializeForScript } from '@/utils/apiDoc/escape'
import {
  documentedScenarios,
  flattenRequestFields,
  flattenResponseFields,
  normalizeApiDoc
} from '@/utils/apiDoc/model'

describe('API 文档字段模型', () => {
  test('serializeForScript 不允许数据闭合 script 标签', () => {
    const serialized = serializeForScript({ text: '</script><script>alert(1)</script>' })

    expect(serialized).not.toContain('</script>')
    expect(serialized).toContain('\\u003c/script\\u003e')
  })

  test('未配置业务场景时不推断内层业务 code', () => {
    expect(documentedScenarios({ scenarios: undefined })).toEqual([])
    expect(documentedScenarios({ scenarios: [] })).toEqual([])
  })

  test('请求字段保留原始变量名和对象字段名', () => {
    const fields = flattenRequestFields({
      inputVariables: [{ varCode: 'customer_name', varType: 'STRING', varLabel: '客户姓名' }],
      inputDataObjects: [{
        objectCode: 'customer',
        fields: [{ varCode: 'mobile_no', varType: 'STRING', varLabel: '手机号' }]
      }]
    })

    expect(fields.map(item => item.path)).toEqual([
      'params.customer_name',
      'params.customer.mobile_no'
    ])
  })

  test('对象字段已有完整脚本路径时不重复对象前缀', () => {
    const fields = flattenRequestFields({
      inputDataObjects: [{
        objectCode: 'customer',
        scriptName: 'customer',
        fields: [{ varCode: 'mobile_no', scriptName: 'customer.mobile_no', varType: 'STRING' }]
      }]
    })

    expect(fields[0].path).toBe('params.customer.mobile_no')
  })

  test('响应字段放入外层 data.result 路径', () => {
    const fields = flattenResponseFields({
      outputVariables: [{ varCode: 'decision_code', varType: 'STRING', varLabel: '决策码' }]
    })

    expect(fields[0]).toEqual(expect.objectContaining({
      path: 'data.result.decision_code',
      type: 'STRING',
      label: '决策码'
    }))
  })

  test('对象字段同时出现在变量列表时按原始路径去重', () => {
    const fields = flattenRequestFields({
      inputVariables: [{ varCode: 'mobile_no', scriptName: 'customer.mobile_no', varType: 'STRING' }],
      inputDataObjects: [{
        objectCode: 'customer',
        fields: [{ varCode: 'mobile_no', scriptName: 'customer.mobile_no', varType: 'STRING' }]
      }]
    })

    expect(fields.map(item => item.path)).toEqual(['params.customer.mobile_no'])
  })

  test('数值和布尔配置值生成对应 JSON 类型的样例', () => {
    const fields = flattenRequestFields({
      inputVariables: [
        { varCode: 'age', varType: 'INTEGER', exampleValue: '17' },
        { varCode: 'enabled', varType: 'BOOLEAN', defaultValue: 'true' }
      ]
    })

    expect(fields.map(item => item.exampleValue)).toEqual([17, true])
  })

  test('标准化文档不会携带项目数据对象目录或 modelJson', () => {
    const normalized = normalizeApiDoc({
      project: { projectCode: 'credit' },
      authentications: null,
      dataObjects: [{ objectCode: 'internal' }],
      rules: [{ ruleCode: 'RISK', modelJson: '{"secret":"value"}' }]
    })

    expect(normalized.authentications).toEqual([])
    expect(normalized.rules[0].modelJson).toBeUndefined()
    expect(normalized.dataObjects).toBeUndefined()
  })
})
