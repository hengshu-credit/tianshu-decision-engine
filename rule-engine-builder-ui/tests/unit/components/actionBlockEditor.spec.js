jest.unmock('@/components/flow/ActionBlockEditor.vue')

const ActionBlockEditor = require('@/components/flow/ActionBlockEditor.vue').default

function createEditorContext(blocks, extra = {}) {
  const ctx = {
    blocks,
    vars: extra.vars || [],
    selectedVars: extra.selectedVars || [],
    functions: extra.functions || [],
    rules: extra.rules || [],
    currentRuleId: extra.currentRuleId == null ? null : extra.currentRuleId,
    currentRuleCode: extra.currentRuleCode || '',
    validateRuleCallCycle: extra.validateRuleCallCycle || null,
    emitted: [],
    $message: { warning: jest.fn() },
    $set(target, key, value) {
      target[key] = value
    },
    $delete(target, key) {
      delete target[key]
    },
    $emit(name, payload) {
      this.emitted.push({ name, payload })
    }
  }

  Object.keys(ActionBlockEditor.methods).forEach(name => {
    ctx[name] = ActionBlockEditor.methods[name].bind(ctx)
  })

  return ctx
}

describe('ActionBlockEditor', () => {
  test('setOperand 原样写入目标 Operand 并触发更新', () => {
    const ctx = createEditorContext([{ type: 'assign', targetOperand: null, valueOperand: null }])
    const operand = { kind: 'REFERENCE', code: 'decision', refId: 2, refType: 'VARIABLE' }

    ctx.setOperand(ctx.blocks[0], 'targetOperand', operand)

    expect(ctx.emitted[0].payload[0].targetOperand).toEqual(operand)
  })

  test('目标、条件左右值保持独立 Operand', () => {
    const ctx = createEditorContext([{ type: 'ternary', targetOperand: null, leftOperand: null, operator: '>=', rightOperand: null, trueOperand: null, falseOperand: null }])
    const block = ctx.blocks[0]
    const target = { kind: 'REFERENCE', code: 'decision', refId: 2, refType: 'VARIABLE' }
    const left = { kind: 'REFERENCE', code: 'score', refId: 1, refType: 'VARIABLE' }

    ctx.setOperand(block, 'targetOperand', target)
    ctx.setOperand(block, 'leftOperand', left)

    const payload = ctx.emitted[1].payload[0]
    expect(payload.targetOperand).toEqual(target)
    expect(payload.leftOperand).toEqual(left)
  })

  test('函数参数数组只保存 Operand', () => {
    const ctx = createEditorContext([{ type: 'func-call', targetOperand: null, functionCode: 'max', args: [null] }])
    const block = ctx.blocks[0]
    const operand = { kind: 'PATH', value: 'request.score', code: 'request.score' }

    ctx.setArrayOperand(block.args, 0, operand)

    const payload = ctx.emitted[0].payload[0]
    expect(payload.args).toEqual([operand])
    expect(payload._argRefs).toBeUndefined()
  })

  test('onRuleSelect writes called rule metadata', async () => {
    const ctx = createEditorContext([{ type: 'rule-call', ruleCode: '', outputField: 'old' }], {
      rules: [
        { id: 8, ruleCode: 'score_card', ruleName: '评分卡', modelType: 'SCORE', outputFieldsJson: [{ scriptName: 'score' }] }
      ]
    })
    const block = ctx.blocks[0]

    await ctx.onRuleSelect(block, ctx.rules[0])

    const payload = ctx.emitted[0].payload[0]
    expect(payload.ruleId).toBe(8)
    expect(payload.ruleName).toBe('评分卡')
    expect(payload.modelType).toBe('SCORE')
    expect(payload.outputField).toBe('')
  })

  test('onRuleSelect rejects current rule self call', async () => {
    const ctx = createEditorContext([{ type: 'rule-call', ruleCode: 'flow_a' }], {
      currentRuleId: 3,
      rules: [
        { id: 3, ruleCode: 'flow_a', ruleName: '当前流', modelType: 'FLOW' }
      ]
    })

    await ctx.onRuleSelect(ctx.blocks[0], ctx.rules[0])

    const payload = ctx.emitted[0].payload[0]
    expect(ctx.$message.warning).toHaveBeenCalled()
    expect(payload.ruleCode).toBe('')
    expect(payload.ruleId).toBeNull()
  })

  test('onRuleSelect restores previous rule when cycle validation fails', async () => {
    const validateRuleCallCycle = jest.fn().mockResolvedValue('规则调用存在环路: A -> B -> A')
    const ctx = createEditorContext([{ type: 'rule-call', ruleId: 1, ruleCode: 'old_rule', ruleName: '旧规则', modelType: 'TABLE', outputField: 'result' }], {
      validateRuleCallCycle,
      rules: [
        { id: 2, ruleCode: 'new_rule', ruleName: '新规则', modelType: 'FLOW' }
      ]
    })
    const block = ctx.blocks[0]

    ctx.rememberRuleCallSnapshot(block, true)
    await ctx.onRuleSelect(block, ctx.rules[0])

    const lastPayload = ctx.emitted[ctx.emitted.length - 1].payload[0]
    expect(validateRuleCallCycle).toHaveBeenCalledWith(block)
    expect(ctx.$message.warning).toHaveBeenCalledWith('规则调用存在环路: A -> B -> A')
    expect(lastPayload.ruleId).toBe(1)
    expect(lastPayload.ruleCode).toBe('old_rule')
    expect(lastPayload.ruleName).toBe('旧规则')
    expect(lastPayload.outputField).toBe('result')
  })

  test('规则调用默认共享全部输出，不启用额外字段映射', () => {
    const ctx = createEditorContext([])

    expect(ctx.hasRuleOutputMapping({ outputField: '', targetOperand: null })).toBe(false)
  })

  test('关闭额外字段映射时成对清空输出字段和目标字段', () => {
    const ctx = createEditorContext([])
    const block = {
      outputField: 'score',
      targetOperand: { kind: 'REFERENCE', refId: 9, refType: 'VARIABLE', code: 'risk_score' }
    }

    ctx.toggleRuleOutputMapping(block, false)

    expect(block.outputField).toBe('')
    expect(block.targetOperand).toBeNull()
    expect(ctx.emitted).toHaveLength(1)
  })

  test('动作条件按左表达式类型提供完整操作符并支持无右值', () => {
    const ctx = createEditorContext([])
    const leaf = {
      leftOperand: { kind: 'REFERENCE', code: 'name', valueType: 'STRING', refId: 1, refType: 'VARIABLE' },
      operator: 'is_null',
      rightOperand: { kind: 'LITERAL', value: 'unused', valueType: 'STRING' }
    }

    expect(ctx.operatorOptions(leaf).map(item => item.value)).toEqual(expect.arrayContaining(['regex_match', 'contains', 'in_list']))
    expect(ctx.operatorRequiresValue(leaf)).toBe(false)
    ctx.onConditionOperatorChange(leaf)
    expect(leaf.rightOperand).toBeNull()
  })

  test('动作条件名单操作符使用专用名单配置上下文', () => {
    const ctx = createEditorContext([])
    const leaf = {
      leftOperand: { kind: 'REFERENCE', code: 'mobile', valueType: 'STRING', refId: 1, refType: 'VARIABLE' },
      operator: 'in_list',
      rightOperand: null
    }

    expect(ctx.rightAllowedKinds(leaf)).toEqual(['LIST_QUERY'])
    expect(ctx.rightContext(leaf)).toBe('LIST_QUERY_CONFIG')
  })
})
