import ScriptEditor from '@/views/designer/ScriptEditor.vue'

describe('ScriptEditor Monaco layout', () => {
  test('设计器沿用 Monaco 自动布局，容器变化后编辑区不会停留在旧尺寸', () => {
    const options = ScriptEditor.computed.editorOptions.call({})
    expect(options.automaticLayout).toBe(true)
  })

  test('折叠变量面板时不触发 Monaco 运行期重建', () => {
    const context = { varPanelCollapsed: false }

    ScriptEditor.methods.toggleVarPanel.call(context)

    expect(context.varPanelCollapsed).toBe(true)
  })

  test('设计器不提供运行期 Monaco 尺寸刷新入口', () => {
    expect(ScriptEditor.methods.scheduleEditorRefresh).toBeUndefined()
  })
})

describe('ScriptEditor variable insertion readiness', () => {
  test('函数选择后持久化函数 ID，保存时不因调用括号丢失引用', () => {
    const tree = ScriptEditor.computed.varTree.call({
      varPickerOptions: [],
      projectFunctions: [{ id: 77, funcCode: 'versioned', funcName: '版本函数' }],
    })
    const context = {
      monacoEditor: { getSelection: () => ({}), executeEdits: vi.fn(), focus: vi.fn() },
      scriptVarRefs: [],
      script: 'return versioned(1);',
      $nextTick: (callback) => callback(),
      escapeRegex: ScriptEditor.methods.escapeRegex,
    }
    ScriptEditor.methods.insertVar.call(context, tree[0].children[0])
    const saved = JSON.parse(ScriptEditor.methods.serializeDesignerDraft.call(context))
    expect(saved.scriptVarRefs).toEqual([{ refCode: 'versioned', varId: 77, refType: 'FUNCTION' }])
    context.script = 'return 1;'
    expect(JSON.parse(ScriptEditor.methods.serializeDesignerDraft.call(context)).scriptVarRefs).toEqual([])
  })

  test('Monaco 就绪前双击变量会在编辑器就绪后完成插入', () => {
    const executeEdits = vi.fn()
    const focus = vi.fn()
    const variable = {
      varCode: 'age',
      _varId: '101',
      _refType: 'VARIABLE',
    }
    const context = {
      monacoEditor: null,
      pendingVarInsertions: [],
      scriptVarRefs: [],
      $nextTick: (callback) => callback(),
      insertVar: ScriptEditor.methods.insertVar,
    }

    ScriptEditor.methods.insertVar.call(context, variable)

    expect(context.pendingVarInsertions).toEqual([variable])

    ScriptEditor.methods.onEditorReady.call(context, {
      getSelection: () => ({ startLineNumber: 1, startColumn: 1 }),
      executeEdits,
      focus,
    })

    expect(executeEdits).toHaveBeenCalledWith('insert-var', [
      expect.objectContaining({ text: 'age' }),
    ])
    expect(context.pendingVarInsertions).toEqual([])
    expect(context.scriptVarRefs).toEqual([
      { refCode: 'age', varId: '101', refType: 'VARIABLE' },
    ])
    expect(focus).toHaveBeenCalled()
  })
})
