// tests/unit/utils/decisionConditionTree.spec.js
import {
  createEmptyGroup,
  createEmptyActionItem,
  createEmptyLeaf,
  migrateRuleConditionsToTree,
  collectVarCodesFromConditionTree,
  walkConditionLeaves,
  hasUsableConditionLeaf,
  compileConditionTreeExpression
} from '@/utils/decisionConditionTree'

// ─── createEmptyGroup ──────────────────────────────────────
describe('createEmptyGroup', () => {
  test('默认 op 为 AND', () => {
    const g = createEmptyGroup()
    expect(g.type).toBe('group')
    expect(g.op).toBe('AND')
    expect(g.children).toBeInstanceOf(Array)
    expect(g.children.length).toBe(0)
  })

  test('可指定 op 为 OR', () => {
    const g = createEmptyGroup('OR')
    expect(g.op).toBe('OR')
  })

  test('children 为可修改的空数组', () => {
    const g = createEmptyGroup()
    g.children.push({ type: 'leaf' })
    expect(g.children.length).toBe(1)
  })
})

// ─── createEmptyActionItem ──────────────────────────────────
describe('createEmptyActionItem', () => {
  test('返回正确的默认字段', () => {
    const a = createEmptyActionItem()
    expect(a).toEqual({
      varCode: '',
      varLabel: '',
      varType: 'STRING',
      enumOptions: '',
      value: ''
    })
  })

  test('所有字段初始为空或默认值', () => {
    const a = createEmptyActionItem()
    expect(a.varCode).toBe('')
    expect(a.varType).toBe('STRING')
    expect(a.value).toBe('')
  })
})

// ─── createEmptyLeaf ─────────────────────────────────────────
describe('createEmptyLeaf', () => {
  test('返回正确的默认字段', () => {
    const l = createEmptyLeaf()
    expect(l.type).toBe('leaf')
    expect(l.varCode).toBe('')
    expect(l.operator).toBe('==')
    expect(l.valueKind).toBe('CONST')
    expect(l.value).toBe('')
  })

  test('type 固定为 leaf', () => {
    const l = createEmptyLeaf()
    expect(l.type).toBe('leaf')
  })
})

// ─── migrateRuleConditionsToTree ──────────────────────────────
describe('migrateRuleConditionsToTree', () => {
  test('旧版规则转为 conditionRoot', () => {
    const legacyConds = [
      { operator: '>', value: '18' },
      { operator: '<=', value: '60' }
    ]
    const colDefs = [
      { varCode: 'age', varLabel: '年龄', varType: 'Integer', _varId: 1 },
      { varCode: 'income', varLabel: '收入', varType: 'Double', _varId: 2 }
    ]
    const tree = migrateRuleConditionsToTree(legacyConds, colDefs)
    expect(tree.type).toBe('group')
    expect(tree.op).toBe('AND')
    expect(tree.children.length).toBe(2)
    expect(tree.children[0].varCode).toBe('age')
    expect(tree.children[0].operator).toBe('>')
    expect(tree.children[0].value).toBe('18')
    expect(tree.children[1].varCode).toBe('income')
    expect(tree.children[1]._varId).toBe(2)
  })

  test('colDefs 为空时 children 为空', () => {
    const tree = migrateRuleConditionsToTree([], [])
    expect(tree.children.length).toBe(0)
  })

  test('colDefs 长度大于 legacyConds 时，缺少的 cond 使用默认值', () => {
    const legacyConds = [{ operator: '==', value: '1' }]
    const colDefs = [
      { varCode: 'a', varLabel: 'A', varType: 'String' },
      { varCode: 'b', varLabel: 'B', varType: 'String' }
    ]
    const tree = migrateRuleConditionsToTree(legacyConds, colDefs)
    expect(tree.children.length).toBe(2)
    expect(tree.children[1].varCode).toBe('b')
    expect(tree.children[1].operator).toBe('==')
    expect(tree.children[1].value).toBe('')
  })

  test('legacyConds 为 null/undefined 时不报错', () => {
    const tree = migrateRuleConditionsToTree(null, [])
    expect(tree.children.length).toBe(0)
  })

  test('_varId 正确迁移', () => {
    const colDefs = [{ varCode: 'x', _varId: 99 }]
    const tree = migrateRuleConditionsToTree([], colDefs)
    expect(tree.children[0]._varId).toBe(99)
  })

  test('valueKind 固定为 CONST', () => {
    const tree = migrateRuleConditionsToTree([{ value: 'test' }], [{ varCode: 'v' }])
    expect(tree.children[0].valueKind).toBe('CONST')
  })
})

// ─── collectVarCodesFromConditionTree ───────────────────────
describe('collectVarCodesFromConditionTree', () => {
  test('收集叶节点的 varCode', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', varCode: 'age', value: '30' },
        { type: 'leaf', varCode: 'score', value: '85' }
      ]
    }
    const result = collectVarCodesFromConditionTree(tree)
    expect(result.has('age')).toBe(true)
    expect(result.has('score')).toBe(true)
  })

  test('valueKind=VAR 时同时收集 varCode 和 value 作为变量编码', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', varCode: 'result', valueKind: 'VAR', value: 'baseScore' }
      ]
    }
    const result = collectVarCodesFromConditionTree(tree)
    expect(result.has('baseScore')).toBe(true)
    // varCode 始终被收集，不受 valueKind 影响
    expect(result.has('result')).toBe(true)
  })

  test('嵌套 group 递归收集', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        {
          type: 'group', op: 'OR', children: [
            { type: 'leaf', varCode: 'a' },
            { type: 'leaf', varCode: 'b' }
          ]
        },
        { type: 'leaf', varCode: 'c' }
      ]
    }
    const result = collectVarCodesFromConditionTree(tree)
    expect(result.has('a')).toBe(true)
    expect(result.has('b')).toBe(true)
    expect(result.has('c')).toBe(true)
  })

  test('空节点不报错，返回空 Set', () => {
    const result = collectVarCodesFromConditionTree(null)
    expect(result.size).toBe(0)
  })

  test('重复 varCode 去重', () => {
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', varCode: 'x' },
        { type: 'leaf', varCode: 'x' }
      ]
    }
    const result = collectVarCodesFromConditionTree(tree)
    expect(result.size).toBe(1)
  })

  test('接受外部 Set 并追加', () => {
    const existing = new Set(['predefined'])
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', varCode: 'newVar' }
      ]
    }
    const result = collectVarCodesFromConditionTree(tree, existing)
    expect(result.has('predefined')).toBe(true)
    expect(result.has('newVar')).toBe(true)
  })
})

// ─── walkConditionLeaves ───────────────────────────────────
describe('walkConditionLeaves', () => {
  test('遍历所有叶节点', () => {
    const leaves = []
    const tree = {
      type: 'group', op: 'AND', children: [
        { type: 'leaf', varCode: 'a', value: '1' },
        { type: 'leaf', varCode: 'b', value: '2' }
      ]
    }
    walkConditionLeaves(tree, l => leaves.push(l))
    expect(leaves.length).toBe(2)
    expect(leaves[0].varCode).toBe('a')
    expect(leaves[1].varCode).toBe('b')
  })

  test('嵌套 group 递归遍历', () => {
    const leaves = []
    const tree = {
      type: 'group', op: 'AND', children: [
        {
          type: 'group', op: 'OR', children: [
            { type: 'leaf', varCode: 'nested' }
          ]
        }
      ]
    }
    walkConditionLeaves(tree, l => leaves.push(l))
    expect(leaves.length).toBe(1)
    expect(leaves[0].varCode).toBe('nested')
  })

  test('空节点不报错', () => {
    expect(() => walkConditionLeaves(null, () => {})).not.toThrow()
  })

  test('undefined 节点不报错', () => {
    expect(() => walkConditionLeaves(undefined, () => {})).not.toThrow()
  })

  test('非条件树节点不报错', () => {
    expect(() => walkConditionLeaves({ type: 'unknown' }, () => {})).not.toThrow()
  })
})

describe('hasUsableConditionLeaf', () => {
  test('空树没有有效条件', () => {
    expect(hasUsableConditionLeaf(createEmptyGroup())).toBe(false)
  })

  test('有左变量和常量值时认为有效', () => {
    const tree = createEmptyGroup()
    tree.children.push({ type: 'leaf', varCode: 'amount', operator: '>=', valueKind: 'CONST', value: '100' })
    expect(hasUsableConditionLeaf(tree)).toBe(true)
  })

  test('任意运算符不作为有效条件', () => {
    const tree = createEmptyGroup()
    tree.children.push({ type: 'leaf', varCode: 'amount', operator: '*', valueKind: 'CONST', value: '' })
    expect(hasUsableConditionLeaf(tree)).toBe(false)
  })
})

describe('compileConditionTreeExpression', () => {
  test('编译字符串常量并转义引号', () => {
    const tree = {
      type: 'group',
      op: 'AND',
      children: [
        { type: 'leaf', varCode: 'customerLevel', varType: 'STRING', operator: '==', valueKind: 'CONST', value: '金"卡' }
      ]
    }

    expect(compileConditionTreeExpression(tree)).toBe('(customerLevel == "金\\"卡")')
  })

  test('编译嵌套与或条件', () => {
    const tree = {
      type: 'group',
      op: 'AND',
      children: [
        { type: 'leaf', varCode: 'amount', varType: 'NUMBER', operator: '>=', valueKind: 'CONST', value: '1000' },
        {
          type: 'group',
          op: 'OR',
          children: [
            { type: 'leaf', varCode: 'level', varType: 'ENUM', operator: '==', valueKind: 'CONST', value: 'A' },
            { type: 'leaf', varCode: 'score', operator: '>=', valueKind: 'VAR', value: 'threshold' }
          ]
        }
      ]
    }

    expect(compileConditionTreeExpression(tree)).toBe('(amount >= 1000 && (level == "A" || score >= threshold))')
  })

  test('空条件编译为 true', () => {
    expect(compileConditionTreeExpression(null)).toBe('true')
    expect(compileConditionTreeExpression(createEmptyGroup())).toBe('true')
  })
})
