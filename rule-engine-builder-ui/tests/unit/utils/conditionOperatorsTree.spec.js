import {
  compileConditionTreeExpression,
  createEmptyGroup,
  hasUsableConditionLeaf,
  normalizeConditionLeafOperator
} from '@/utils/decisionConditionTree'

describe('condition tree typed operators', () => {
  test('编译字符串包含和数字区间操作符', () => {
    const tree = {
      type: 'group',
      op: 'AND',
      children: [
        { type: 'leaf', varCode: 'name', varType: 'STRING', operator: 'contains', valueKind: 'CONST', value: 'VIP' },
        { type: 'leaf', varCode: 'age', varType: 'NUMBER', operator: 'between', valueKind: 'CONST', value: '18,60' }
      ]
    }

    expect(compileConditionTreeExpression(tree)).toBe('(containsValue(name, "VIP") && (age >= 18 && age <= 60))')
  })

  test('无右值操作符可以构成有效条件', () => {
    const tree = createEmptyGroup()
    tree.children.push({ type: 'leaf', varCode: 'tags', varType: 'LIST', operator: 'not_empty', valueKind: 'CONST', value: '' })

    expect(hasUsableConditionLeaf(tree)).toBe(true)
    expect(compileConditionTreeExpression(tree)).toBe('(isNotBlank(tags))')
  })

  test('字段类型变化后会重置不适用的操作符', () => {
    const leaf = { type: 'leaf', varType: 'BOOLEAN', operator: '>' }

    normalizeConditionLeafOperator(leaf)

    expect(leaf.operator).toBe('==')
  })
})
