import ScriptEditor from '@/views/designer/ScriptEditor.vue'

describe('ScriptEditor stable references for Unicode identifiers', () => {
  function context(script, refCode) {
    return {
      script,
      scriptVarRefs: [{ refCode, varId: 101, refType: 'DATA_OBJECT' }],
      escapeRegex: ScriptEditor.methods.escapeRegex,
    }
  }

  test.each(['申请信息.手机号', '申请信息.申请人姓名', '$申请人', 'customer.name'])(
    'preview and save preserve the selected stable ID for %s',
    (refCode) => {
      const value = context(`return ${refCode};`, refCode)
      const expected = [...value.scriptVarRefs]
      expect(JSON.parse(ScriptEditor.methods.serializeDesignerDraft.call(value)).scriptVarRefs).toEqual(expected)
      ScriptEditor.methods.syncScriptVarRefsFromScript.call(value)
      expect(value.scriptVarRefs).toEqual(expected)
    }
  )

  test.each([
    ['其他申请信息.手机号', '申请信息.手机号'],
    ['申请信息.手机号扩展', '申请信息.手机号'],
    ['billingAmount', 'amount'],
    ['customer.name2', 'customer.name'],
    ['return 1;', '申请信息.手机号'],
  ])('preview and save discard a reference not independently used in %s', (script, refCode) => {
    const value = context(script, refCode)
    expect(JSON.parse(ScriptEditor.methods.serializeDesignerDraft.call(value)).scriptVarRefs).toEqual([])
    ScriptEditor.methods.syncScriptVarRefsFromScript.call(value)
    expect(value.scriptVarRefs).toEqual([])
  })
})
