import {
  collectVarCodesFromConditionTree,
  compileConditionTreeExpression,
  createEmptyActionItem,
  createEmptyGroup,
  createEmptyLeaf,
  hasUsableConditionLeaf,
  migrateRuleConditionsToTree,
  walkConditionLeaves
} from '@/utils/decisionConditionTree'

const ref = (code, refId = null, refType = '') => ({
  kind: 'REFERENCE', value: code, code, label: code, valueType: 'NUMBER',
  refId, refType, resolved: refId != null && !!refType
})
const literal = (value, valueType = 'NUMBER') => ({ kind: 'LITERAL', value, valueType })

describe('decisionConditionTree', () => {
  test('创建空条件组', () => {
    expect(createEmptyGroup()).toEqual({ type: 'group', op: 'AND', children: [] })
    expect(createEmptyGroup('OR').op).toBe('OR')
  })

  test('创建空条件叶节点只包含左右操作数', () => {
    expect(createEmptyLeaf()).toEqual({
      type: 'leaf', leftOperand: null, operator: '==', rightOperand: null
    })
  })

  test('动作默认结构使用统一 Operand', () => {
    expect(createEmptyActionItem()).toEqual({
      targetOperand: null, valueOperand: null
    })
  })

  test('列定义迁移为引用操作数，旧值迁移为阈值', () => {
    const tree = migrateRuleConditionsToTree(
      [{ operator: '>', value: '18' }],
      [{ varCode: 'age', varLabel: '年龄', varType: 'INTEGER', _varId: 9, _refType: 'VARIABLE' }]
    )

    expect(tree.children[0]).toMatchObject({
      operator: '>',
      leftOperand: { kind: 'REFERENCE', code: 'age', refId: 9, refType: 'VARIABLE' },
      rightOperand: { kind: 'LITERAL', value: '18', valueType: 'INTEGER' }
    })
  })

  test('迁移时按较长列表补齐叶节点', () => {
    const tree = migrateRuleConditionsToTree([], [{ varCode: 'a' }, { varCode: 'b' }])
    expect(tree.children).toHaveLength(2)
    expect(tree.children[1].leftOperand.code).toBe('b')
    expect(tree.children[1].rightOperand.value).toBe('')
  })

  test('递归收集左右操作数依赖并去重', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', leftOperand: ref('amount', 1, 'VARIABLE'), operator: '>', rightOperand: ref('threshold', 2, 'VARIABLE') },
        { type: 'leaf', leftOperand: ref('amount', 1, 'VARIABLE'), operator: '>=', rightOperand: literal('100') }
      ]
    }
    expect(Array.from(collectVarCodesFromConditionTree(tree))).toEqual(['amount', 'threshold'])
  })

  test('外部 Set 会被追加而不是替换', () => {
    const out = new Set(['existing'])
    collectVarCodesFromConditionTree({ type: 'leaf', leftOperand: ref('newField') }, out)
    expect(Array.from(out)).toEqual(['existing', 'newField'])
  })

  test('遍历嵌套组中的全部叶节点', () => {
    const leaves = []
    const tree = { type: 'group', op: 'AND', children: [
      { type: 'leaf', leftOperand: ref('a') },
      { type: 'group', op: 'OR', children: [{ type: 'leaf', leftOperand: ref('b') }] }
    ] }
    walkConditionLeaves(tree, leaf => leaves.push(leaf.leftOperand.code))
    expect(leaves).toEqual(['a', 'b'])
  })

  test('阈值条件和无右值条件均可用', () => {
    expect(hasUsableConditionLeaf({ type: 'leaf', leftOperand: ref('amount'), operator: '>=', rightOperand: literal('100') })).toBe(true)
    expect(hasUsableConditionLeaf({ type: 'leaf', leftOperand: ref('tags'), operator: 'not_empty', rightOperand: null })).toBe(true)
    expect(hasUsableConditionLeaf(createEmptyGroup())).toBe(false)
  })

  test('编译统一操作数条件', () => {
    expect(compileConditionTreeExpression({
      type: 'leaf', operator: '>=',
      leftOperand: { kind: 'PATH', value: 'request.age' },
      rightOperand: literal('18')
    })).toBe('request.age >= 18')
  })

  test('函数和引用可以作为条件操作数', () => {
    expect(compileConditionTreeExpression({
      type: 'leaf', operator: '>',
      leftOperand: {
        kind: 'FUNCTION', functionId: 1, functionCode: 'max', args: [ref('scoreA'), ref('scoreB')]
      },
      rightOperand: literal('600')
    })).toBe('max(scoreA, scoreB) > 600')
  })

  test('编译嵌套与或条件并转义字符串', () => {
    const tree = { type: 'group', op: 'AND', children: [
      { type: 'leaf', leftOperand: { ...ref('name'), valueType: 'STRING' }, operator: '==', rightOperand: literal('金"卡', 'STRING') },
      { type: 'group', op: 'OR', children: [
        { type: 'leaf', leftOperand: ref('amount'), operator: '>=', rightOperand: literal('1000') },
        { type: 'leaf', leftOperand: ref('score'), operator: '>=', rightOperand: ref('threshold') }
      ] }
    ] }
    expect(compileConditionTreeExpression(tree)).toBe('(name == "金\\"卡" && (amount >= 1000 || score >= threshold))')
  })

  test('空树编译为 true', () => {
    expect(compileConditionTreeExpression(null)).toBe('true')
    expect(compileConditionTreeExpression(createEmptyGroup())).toBe('true')
  })
})
