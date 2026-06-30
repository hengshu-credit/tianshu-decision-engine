jest.unmock('@/components/flow/ActionBlockEditor.vue')

const ActionBlockEditor = require('@/components/flow/ActionBlockEditor.vue').default

function createEditorContext(blocks) {
  const ctx = {
    blocks,
    emitted: [],
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
})
