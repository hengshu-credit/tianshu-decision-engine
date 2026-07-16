import { formatExpressionFormula } from '@/utils/expressionDisplay'
import { compileOperand, createFunctionOperand, createOperationOperand, createReferenceOperand } from '@/utils/operand'

const ref = (id, code, label) => createReferenceOperand({
  _varId: id,
  _refType: 'VARIABLE',
  varCode: code,
  varLabel: label,
  varType: 'NUMBER'
})

describe('expressionDisplay', () => {
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
