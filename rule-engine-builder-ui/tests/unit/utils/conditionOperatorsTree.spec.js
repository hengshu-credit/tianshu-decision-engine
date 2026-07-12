import { compileConditionTreeExpression, createEmptyGroup, hasUsableConditionLeaf } from '@/utils/decisionConditionTree'

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
})
