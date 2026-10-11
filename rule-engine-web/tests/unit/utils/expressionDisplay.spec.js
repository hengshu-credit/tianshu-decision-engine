import { formatExpressionFormula, formatConditionDescription, formatConditionTreeDescription } from '@/utils/expressionDisplay'
import { compileOperand, createFunctionOperand, createOperationOperand, createReferenceOperand } from '@/utils/operand'

const ref = (id, code, label) => createReferenceOperand({
  _varId: id,
  _refType: 'VARIABLE',
  varCode: code,
  varLabel: label,
  varType: 'NUMBER'
})

describe('expressionDisplay', () => {
  test('名单中文摘要复用配置词典，精确ID保留且未知模式不猜为默认命中', () => {
    const operand = { kind: 'LIST_QUERY', listIds: ['9007199254740993'], itemTypes: ['MOBILE', 'CUSTOM_TYPE'], combinationMode: 'ALL_FIELDS_ANY_LIST', matchMode: 'IN_LIST' }
    const original = JSON.stringify(operand)
    expect(formatExpressionFormula(operand, { humanReadable: true })).toBe('名单查询（名单ID 9007199254740993；全部内容各自命中任一名单；在名单内（精确匹配）；手机号、自定义类型 CUSTOM_TYPE）')
    expect(formatExpressionFormula({ ...operand, combinationMode: 'CUSTOM_MODE', matchMode: 'CUSTOM_MATCH' }, { humanReadable: true })).toContain('组合方式未识别；匹配方式未识别')
    expect(formatExpressionFormula(operand)).toContain('listQuery(')
    expect(JSON.stringify(operand)).toBe(original)
  })
  test('中文条件复用运算符来源状态、区间边界与结构化集合，不修改引用或原值', () => {
    const leftOperand = ref(19, 'amount', '申请金额')
    const between = { type: 'leaf', leftOperand, operator: 'between', rightOperand: { kind: 'LITERAL', valueType: 'NUMBER', value: '18,9007199254740993.123456789' } }
    const original = JSON.stringify(between)
    expect(formatConditionDescription(between)).toBe('申请金额 amount 介于 18 ～ 9007199254740993.123456789（含边界）')
    expect(JSON.stringify(between)).toBe(original)
    expect(formatConditionDescription({ ...between, rightOperand: { ...between.rightOperand, value: '18,' } })).toContain('条件未完成')
    expect(formatConditionDescription({ ...between, operator: 'source_no_data', leftOperand: { ...leftOperand, varSource: 'DB' } })).toBe('申请金额 amount 查询无数据')
    expect(formatConditionDescription({ ...between, operator: 'customRiskCondition' })).toContain('自定义条件')
  })

  test('未支持的表达式不伪造中文结论，布尔空值不误称false', () => {
    expect(formatExpressionFormula({ kind: 'CUSTOM_QL', value: 'riskAllowed()' }, { humanReadable: true })).toBe('自定义表达式（请查看原配置）')
    expect(formatExpressionFormula({ kind: 'CAST', targetType: 'CUSTOM', operand: ref(1, 'a', '金额') }, { humanReadable: true })).toContain('自定义转换')
    expect(formatExpressionFormula({ kind: 'LITERAL', valueType: 'BOOLEAN', value: null }, { humanReadable: true })).not.toContain('false')
    expect(formatExpressionFormula({ kind: 'LITERAL', valueType: 'BOOLEAN', value: 'unknown' }, { humanReadable: true })).toBe('布尔值配置未完成')
    expect(formatConditionTreeDescription({ type: 'group', op: 'XOR', children: [{}] })).toContain('自定义条件组')
  })
  test('业务公式只去掉最外层括号并显示中文和编码', () => {
    const root = createOperationOperand([
      { operand: ref(1, 'age', '年龄') },
      {
        operator: '+',
        operand: createFunctionOperand({ id: 8, funcCode: 'idCardAge', funcName: '身份证年龄' }, [
          ref(2, 'idcard_no', '身份证号'),
          createFunctionOperand({ id: 9, funcCode: 'currentDate', funcName: '当前日期' }, []),
          { kind: 'LITERAL', value: 'FULL', valueType: 'STRING' }
        ])
      }
    ])

    expect(formatExpressionFormula(root)).toBe('年龄 age + 身份证年龄 idCardAge(身份证号 idcard_no, 当前日期 currentDate(), "FULL")')
    expect(compileOperand(root)).toBe('(age + idCardAge(idcard_no, currentDate(), "FULL"))')
  })

  test('嵌套运算继续显示括号以表达人工配置的优先级', () => {
    const nested = createOperationOperand([
      { operand: ref(1, 'age', '年龄') },
      {
        operator: '+',
        operand: createOperationOperand([
          { operand: ref(2, 'risk_score', '风险分') },
          { operator: '*', operand: { kind: 'LITERAL', value: '0.5', valueType: 'NUMBER' } }
        ])
      }
    ])

    expect(formatExpressionFormula(nested)).toContain('(风险分 risk_score * 0.5)')
  })
})
