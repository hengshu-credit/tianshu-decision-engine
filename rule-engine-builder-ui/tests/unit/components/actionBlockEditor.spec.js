jest.unmock('@/components/flow/ActionBlockEditor.vue')

const ActionBlockEditor = require('@/components/flow/ActionBlockEditor.vue').default

function createEditorContext(blocks, extra = {}) {
  const ctx = {
    blocks,
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
  test('selectVar writes field-level target reference', () => {
    const ctx = createEditorContext([{ type: 'assign', target: '', value: '1' }])

    ctx.selectVar(ctx.blocks[0], 'target', { varCode: 'decision', _varId: 2, _refType: 'VARIABLE' })

    const payload = ctx.emitted[0].payload[0]
    expect(payload.target).toBe('decision')
    expect(payload._targetVarId).toBe(2)
    expect(payload._targetRefType).toBe('VARIABLE')
    expect(payload._varId).toBe(2)
  })

  test('selectVar keeps target and condVar references separate', () => {
    const ctx = createEditorContext([{ type: 'ternary', target: '', condVar: '', condOp: '>=', condValue: '60', trueValue: '"PASS"', falseValue: '"REJECT"' }])
    const block = ctx.blocks[0]

    ctx.selectVar(block, 'target', { varCode: 'decision', _varId: 2, _refType: 'VARIABLE' })
    ctx.selectVar(block, 'condVar', { varCode: 'score', _varId: 1, _refType: 'VARIABLE' })

    const payload = ctx.emitted[1].payload[0]
    expect(payload.target).toBe('decision')
    expect(payload._targetVarId).toBe(2)
    expect(payload.condVar).toBe('score')
    expect(payload._condVarId).toBe(1)
    expect(payload._varId).toBeUndefined()
  })

  test('selectArgVar writes function argument reference', () => {
    const ctx = createEditorContext([{ type: 'func-call', target: '', funcName: 'max', args: [''] }])
    const block = ctx.blocks[0]

    ctx.selectArgVar(block, 0, { varCode: 'score', _varId: 1, _refType: 'VARIABLE' })

    const payload = ctx.emitted[0].payload[0]
    expect(payload.args).toEqual(['score'])
    expect(payload._argRefs).toEqual([{ _varId: 1, _refType: 'VARIABLE' }])

    ctx.setArgValue(block, 0, 'score + 1')
    const nextPayload = ctx.emitted[1].payload[0]
    expect(nextPayload.args).toEqual(['score + 1'])
    expect(nextPayload._argRefs).toEqual([null])
  })

  test('onRuleSelect writes called rule metadata', async () => {
    const ctx = createEditorContext([{ type: 'rule-call', ruleCode: '', outputField: 'old' }], {
      rules: [
        { id: 8, ruleCode: 'score_card', ruleName: '评分卡', modelType: 'SCORE', outputFieldsJson: [{ scriptName: 'score' }] }
      ]
    })
    const block = ctx.blocks[0]

    ctx.$set(block, 'ruleCode', 'score_card')
    await ctx.onRuleSelect(block, 'score_card')

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

    await ctx.onRuleSelect(ctx.blocks[0], 'flow_a')

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
    ctx.$set(block, 'ruleCode', 'new_rule')
    await ctx.onRuleSelect(block, 'new_rule')

    const lastPayload = ctx.emitted[ctx.emitted.length - 1].payload[0]
    expect(validateRuleCallCycle).toHaveBeenCalledWith(block)
    expect(ctx.$message.warning).toHaveBeenCalledWith('规则调用存在环路: A -> B -> A')
    expect(lastPayload.ruleId).toBe(1)
    expect(lastPayload.ruleCode).toBe('old_rule')
    expect(lastPayload.ruleName).toBe('旧规则')
    expect(lastPayload.outputField).toBe('result')
  })
})
