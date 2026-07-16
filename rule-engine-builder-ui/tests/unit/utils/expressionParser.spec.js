import { ExpressionParseError, parseExpressionScript } from '@/utils/expressionParser'
import { compileOperand, OPERATION_OPERATORS } from '@/utils/operand'

const vars = [
  { _varId: 9, _refType: 'VARIABLE', varCode: 'risk_score', varLabel: '风险分', varType: 'NUMBER' },
  { _varId: 10, _refType: 'VARIABLE', varCode: 'age', varLabel: '年龄', varType: 'NUMBER' }
]
const functions = [
  { id: 31, funcCode: 'projectRisk', funcName: '项目风险', paramsJson: '[{"name":"age","type":"NUMBER"}]' },
  { id: 32, funcCode: 'max', funcName: '最大值', paramsJson: '[{"name":"a","type":"OBJECT"},{"name":"b","type":"OBJECT"}]' }
]

describe('expressionParser', () => {
  test('精确解析字段和受管函数并保留稳定 ID', () => {
    const result = parseExpressionScript('risk_score + projectRisk(age)', { vars, functions })

    expect(result.terms[0].operand).toMatchObject({ refId: 9, refType: 'VARIABLE' })
    expect(result.terms[1].operand).toMatchObject({ functionId: 31, functionCode: 'projectRisk' })
    expect(result.terms[1].operand.args[0]).toMatchObject({ refId: 10, refType: 'VARIABLE' })
  })

  test('所有二元运算符保持同级，显式括号生成嵌套运算', () => {
    OPERATION_OPERATORS.forEach(operator => {
      const operand = parseExpressionScript(`age ${operator} risk_score`, { vars, functions })
      expect(operand.terms[1].operator).toBe(operator)
    })

    const grouped = parseExpressionScript('age + (risk_score * 0.5)', { vars, functions })
    expect(grouped.terms[1].operand.kind).toBe('OPERATION')
    expect(compileOperand(grouped)).toBe('(age + (risk_score * 0.5))')
  })

  test('解析数组、嵌套函数、取值、转换和名单查询专用节点', () => {
    expect(parseExpressionScript('[1, "x", true]', { vars, functions })).toMatchObject({ kind: 'ARRAY', items: [{ valueType: 'NUMBER' }, { valueType: 'STRING' }, { valueType: 'BOOLEAN' }] })
    expect(parseExpressionScript('max(age, projectRisk(risk_score))', { vars, functions }).args[1]).toMatchObject({ kind: 'FUNCTION', functionId: 31 })
    expect(parseExpressionScript('objGet(customer, "age")', { vars, functions })).toMatchObject({ kind: 'ACCESS', accessType: 'KEY' })
    expect(parseExpressionScript('arrGet(items, 0)', { vars, functions })).toMatchObject({ kind: 'ACCESS', accessType: 'INDEX' })
    expect(parseExpressionScript('toNumberValue(age)', { vars, functions })).toMatchObject({ kind: 'CAST', targetType: 'NUMBER' })
    expect(parseExpressionScript('listQuery([1, 2], ["PHONE"], "ANY_FIELD_ANY_LIST", "IN_LIST")', { vars, functions })).toMatchObject({
      kind: 'LIST_QUERY',
      listIds: [1, 2],
      itemTypes: ['PHONE'],
      combinationMode: 'ANY_FIELD_ANY_LIST',
      matchMode: 'IN_LIST'
    })
  })

  test('未受管路径保留为 PATH，不按名称猜测 ID', () => {
    expect(parseExpressionScript('request.customer.age', { vars, functions })).toMatchObject({
      kind: 'PATH', value: 'request.customer.age', refId: null, resolved: false
    })
  })

  test.each([
    ['age = 1', '不支持赋值'],
    ['age + 1;', '不支持分号'],
    ['unknownFn(age)', '未知函数'],
    ['"missing', '字符串未结束'],
    ['age + )', '缺少表达式']
  ])('拒绝非结构化或残缺脚本：%s', (script, message) => {
    expect(() => parseExpressionScript(script, { vars, functions })).toThrow(message)
  })

  test('歧义字段和错误位置会返回行列信息', () => {
    const duplicateVars = vars.concat([{ ...vars[1], _varId: 99 }])
    try {
      parseExpressionScript('\n age + 1', { vars: duplicateVars, functions })
      throw new Error('expected parser to fail')
    } catch (error) {
      expect(error).toBeInstanceOf(ExpressionParseError)
      expect(error.message).toContain('字段编码存在歧义')
      expect(error.line).toBe(2)
      expect(error.column).toBe(2)
    }
  })
})
