vi.unmock('@/components/flow/ActionBlockEditor.vue')

import { shallowMount } from '@test-utils'

const ActionBlockEditor = (await vi.importActual('@/components/flow/ActionBlockEditor.vue')).default
const ActionAssignmentRow = (await vi.importActual('@/components/flow/ActionAssignmentRow.vue')).default

function createEditorContext(blocks, extra = {}) {
  const ctx = {
    blocks,
    ruleSelectionRequestId: 0,
    vars: extra.vars || [],
    selectedVars: extra.selectedVars || [],
    functions: extra.functions || [],
    rules: extra.rules || [],
    currentRuleId: extra.currentRuleId == null ? null : extra.currentRuleId,
    currentRuleCode: extra.currentRuleCode || '',
    validateRuleCallCycle: extra.validateRuleCallCycle || null,
    emitted: [],
    $message: { warning: vi.fn() },
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
  test('函数按 ID 选择，切换零参数函数及清除会去掉旧参数，重复选择保留编辑内容', () => {
    const ctx = { sync: vi.fn() }
    const select = ActionBlockEditor.methods.onFunctionResourceSelect.bind(ctx)
    const block = { functionId: '1', functionCode: 'same', args: [{ kind: 'LITERAL', value: '保留' }] }
    select(block, { id: '1', funcCode: 'same', params: [{ type: 'STRING' }] })
    expect(block.args[0].value).toBe('保留')
    select(block, { id: '2', funcCode: 'same', params: [] })
    expect(block).toMatchObject({ functionId: '2', args: [] })
    select(block, { id: '3', funcCode: 'third', params: [{ type: 'STRING' }, { type: 'NUMBER' }] })
    expect(block.args).toEqual([null, null])
    select(block, null)
    expect(block).toMatchObject({ functionId: null, functionCode: '', args: [] })
  })
  test('冻结包同号子规则不是当前环境自身，当前目录仍保留自身调用保护', async () => {
    const child = { id: '8', ruleCode: 'SourceChild', modelType: 'SCORE', inputFields: [], outputFields: [] }
    const wrapper = shallowMount(ActionBlockEditor, {
      props: { actionData: [{ type: 'rule-call', ruleId: '8', ruleCode: 'SourceChild' }], currentRuleId: 8, rules: [child] },
      provide: { getRuleCallCatalog: () => ({ mode: 'FROZEN', key: 'source:81' }) }
    })
    try {
      expect(wrapper.vm.isCurrentRule(child)).toBe(false)
      expect(wrapper.findComponent({ name: 'RuleExecutionSelector' }).props('currentRuleId')).toBeNull()
      const live = createEditorContext([], { currentRuleId: 8 })
      expect(live.isCurrentRule(child)).toBe(true)
    } finally { wrapper.unmount() }
  })
  test('较早选择的环路错误晚到时不撤销用户后来选中的规则', async () => {
    let resolveOld
    const validateRuleCallCycle = vi.fn().mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve })).mockResolvedValueOnce(true)
    const block = { type: 'rule-call', ruleId: 1 }
    const rules = [{ id: 2, ruleCode: 'first' }, { id: 3, ruleCode: 'second' }]
    const ctx = createEditorContext([block], { rules, validateRuleCallCycle })
    ctx.rememberRuleCallSnapshot(block, true)
    const old = ctx.onRuleSelect(block, rules[0])
    ctx.rememberRuleCallSnapshot(block, true)
    await ctx.onRuleSelect(block, rules[1])
    resolveOld('旧选择存在环路')
    await old
    expect(block.ruleId).toBe(3)
    expect(ctx.emitted.at(-1).payload[0].ruleId).toBe(3)
    expect(ctx.$message.warning).not.toHaveBeenCalled()
  })
  test('同一调用规则重新选择且发布字段仍在加载时不清除用户映射', async () => {
    const block = { type: 'rule-call', ruleId: 8, outputField: 'published_score', targetOperand: { kind: 'REFERENCE', refId: 9 } }
    const rule = { id: 8, ruleCode: 'child', modelType: 'SCORE', outputFields: [{ scriptName: 'draft_score' }] }
    const ctx = createEditorContext([block], { rules: [rule] })
    await ctx.onRuleSelect(block, rule)
    expect(block.outputField).toBe('published_score')
    expect(block.targetOperand).toEqual({ kind: 'REFERENCE', refId: 9 })
  })
  test('调用结果选项只来自相应发布版本，不能回退当前工作区字段', () => {
    const rule = { id: '8', outputFields: [{ scriptName: 'draft_score' }], executionVersions: [
      { bindingId: '81', version: 1, available: true, outputFields: [{ scriptName: 'old_score' }] },
      { bindingId: '82', version: 2, available: true, outputFields: [{ scriptName: 'published_score' }] }
    ] }
    const ctx = createEditorContext([], { rules: [rule] })
    ctx.ruleVersionFields = {}
    expect(ctx.ruleOutputFields({ ruleId: '8' })).toEqual([{ scriptName: 'published_score' }])
    expect(ctx.ruleOutputFields({ ruleId: '8', versionMode: 'FIXED', versionBindingId: '81' })).toEqual([{ scriptName: 'old_score' }])
    delete rule.executionVersions
    expect(ctx.ruleOutputFields({ ruleId: '8' })).toEqual([])
  })
  test('赋值右值默认类型跟随数字目标字段', () => {
    const targetOperand = {
      kind: 'REFERENCE', code: 'amount', valueType: 'NUMBER',
      refId: 7, refType: 'VARIABLE', resolved: true,
    }
    const actionEditor = {
      vars: [], selectedVars: [], functions: [], writeKinds: [], valueKinds: [],
      operandType: ActionBlockEditor.methods.operandType,
      setOperand: vi.fn(),
    }
    const wrapper = shallowMount(ActionAssignmentRow, {
      props: { action: { type: 'assign', targetOperand, valueOperand: null } },
      global: { provide: { actionEditor } },
      stubs: {
        OperandPicker: {
          name: 'OperandPicker',
          props: ['value', 'expectedType'],
          template: '<div class="operand-picker-stub" />',
        },
      },
    })

    const pickers = wrapper.findAllComponents({ name: 'OperandPicker' })
    expect(pickers[1].props('expectedType')).toBe('NUMBER')
    wrapper.unmount()
  })

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
    const validateRuleCallCycle = vi.fn().mockResolvedValue('规则调用存在环路: A -> B -> A')
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

  test('额外字段映射开关可以显式开启', () => {
    const ctx = createEditorContext([])
    const block = { enableOutputMapping: false, outputField: '', targetOperand: null }

    ctx.toggleRuleOutputMapping(block, true)

    expect(block.enableOutputMapping).toBe(true)
    expect(ctx.hasRuleOutputMapping(block)).toBe(true)
    expect(ctx.emitted).toHaveLength(1)
  })

  test('关闭额外字段映射时保留输出字段和目标字段', () => {
    const ctx = createEditorContext([])
    const block = {
      enableOutputMapping: true,
      outputField: 'score',
      targetOperand: { kind: 'REFERENCE', refId: 9, refType: 'VARIABLE', code: 'risk_score' }
    }

    ctx.toggleRuleOutputMapping(block, false)

    expect(block.enableOutputMapping).toBe(false)
    expect(block.outputField).toBe('score')
    expect(block.targetOperand.code).toBe('risk_score')
    expect(ctx.emitted).toHaveLength(1)
  })

  test('一键清空只清除映射字段并保持开关开启', () => {
    const ctx = createEditorContext([])
    const block = {
      enableOutputMapping: true,
      outputField: 'score',
      targetOperand: { kind: 'REFERENCE', refId: 9, refType: 'VARIABLE', code: 'risk_score' }
    }

    ctx.clearRuleOutputMapping(block)

    expect(block.enableOutputMapping).toBe(true)
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

  test('动作条件按 API 来源展示缓存状态并清空右值', () => {
    const ctx = createEditorContext([])
    const leaf = {
      leftOperand: {
        kind: 'REFERENCE', code: 'apiResult', valueType: 'STRING', refId: 9,
        refType: 'VARIABLE', varSource: 'API', sourceType: 'variable'
      },
      operator: 'source_origin_stale_cache',
      rightOperand: { kind: 'LITERAL', value: 'unused', valueType: 'STRING' }
    }

    expect(ctx.operatorGroups(leaf).map(group => group.label)).toEqual(['值判断', '来源状态'])
    ctx.onConditionOperatorChange(leaf)

    expect(leaf.operator).toBe('source_origin_stale_cache')
    expect(leaf.rightOperand).toBeNull()
  })
})
