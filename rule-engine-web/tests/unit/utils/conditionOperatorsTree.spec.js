import { compileConditionTreeExpression, createEmptyGroup, hasUsableConditionLeaf } from '@/utils/decisionConditionTree'
import { compileConditionOperands } from '@/utils/conditionOperand'

const ref = (code, valueType) => ({ kind: 'REFERENCE', value: code, code, valueType })
const literal = (value, valueType) => ({ kind: 'LITERAL', value, valueType })

describe('condition tree typed operators', () => {
  test('编译字符串包含和数字区间操作符', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', leftOperand: ref('name', 'STRING'), operator: 'contains', rightOperand: literal('VIP', 'STRING') },
        { type: 'leaf', leftOperand: ref('age', 'NUMBER'), operator: 'between', rightOperand: literal('18,60', 'NUMBER') }
      ]
    }

    expect(compileConditionTreeExpression(tree)).toBe('(containsValue(name, "VIP") && (age >= 18 && age <= 60))')
  })

  test('无右值操作符可以构成有效条件', () => {
    const tree = createEmptyGroup()
    tree.children.push({ type: 'leaf', leftOperand: ref('tags', 'LIST'), operator: 'not_empty', rightOperand: null })

    expect(hasUsableConditionLeaf(tree)).toBe(true)
    expect(compileConditionTreeExpression(tree)).toBe('(isNotBlank(tags))')
  })

  test('空数字左值不能把完整区间变成无条件表达式', () => {
    const tree = { type: 'leaf', operator: 'between', leftOperand: literal('', 'NUMBER'), rightOperand: literal('18,60', 'NUMBER') }
    expect(() => compileConditionTreeExpression(tree)).toThrow(/区间.*字段/)
  })

  test('逐项集合使用原in语义，文本含逗号、空白和空串不切分或改写', () => {
    const values = { kind: 'ARRAY', valueType: 'LIST', items: ['A,B', ' padded ', ''].map(value => literal(value, 'STRING')) }
    expect(compileConditionOperands(ref('value', 'STRING'), 'in', values)).toBe('value in ["A,B", " padded ", ""]')
    expect(compileConditionOperands(ref('value', 'STRING'), 'not_in', values)).toBe('(!isBlank(value) && !(value in ["A,B", " padded ", ""]))')
  })

  test('集合精确数字沿用CAST字符串转换，不经JS Number', () => {
    const values = { kind: 'ARRAY', valueType: 'LIST', items: [{ kind: 'CAST', targetType: 'NUMBER', operand: literal('9007199254740993.123456789', 'STRING') }] }
    expect(compileConditionOperands(ref('amount', 'NUMBER'), 'in', values)).toBe('amount in [toNumberValue("9007199254740993.123456789")]')
  })

  test('新逐项集合空项列表或非法数字不能生成有效条件', () => {
    for (const items of [[], [literal('', 'NUMBER')], [literal('1 + 2', 'BIGDECIMAL')], [{ kind: 'CAST', targetType: 'NUMBER', operand: literal('1 + 2', 'STRING') }]]) {
      expect(() => compileConditionOperands(ref('value', 'NUMBER'), 'in', { kind: 'ARRAY', items })).toThrow(/集合/)
    }
  })
})
