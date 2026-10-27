import ScriptEditor from '@/views/designer/ScriptEditor.vue'
import { mount } from '@test-utils'

describe('ScriptEditor Monaco layout', () => {
  test('切换当前资源后展示全部字段，清除空的已选分类和旧搜索', () => {
    const context = { scriptPickerCategory: 'selected', varSearchKey: '旧字段', scriptPickerSelection: 'VARIABLE:99', resetReferenceVersions: vi.fn() }
    ScriptEditor.watch.referenceCatalogMode.call(context, 'LIVE')
    expect(context.scriptPickerCategory).toBe('all')
    expect(context.varSearchKey).toBe('')
    expect(context.scriptPickerSelection).toBe('')
    expect(context.resetReferenceVersions).toHaveBeenCalledOnce()
  })
  test('脚本输入复用引用目录，资源名称变化后才重建并同步标注', async () => {
    const wrapper = mount({
      data: () => ({ script: 'age', projectRefs: [{ id: 1, refCode: 'age', refLabel: { label: '年龄' } }],
        varPickerOptions: [{ _varId: 1, _refType: 'VARIABLE', varCode: 'age', varLabelText: '年龄' }], projectFunctions: [], scriptVarRefs: [], scriptVersionReferences: [] }),
      computed: {
        qlReferenceItems: ScriptEditor.computed.qlReferenceItems,
        scriptReferenceCatalog: ScriptEditor.computed.scriptReferenceCatalog,
        scriptReferences: ScriptEditor.computed.scriptReferences,
      },
      methods: { qlIntelligenceRefs: ScriptEditor.methods.qlIntelligenceRefs },
      template: '<div>{{ scriptReferences.length }}</div>',
    })
    try {
      const refs = wrapper.vm.qlReferenceItems, catalog = wrapper.vm.scriptReferenceCatalog
      await wrapper.setData({ script: 'age + 2' })
      expect(wrapper.vm.qlReferenceItems).toBe(refs)
      expect(wrapper.vm.scriptReferenceCatalog).toBe(catalog)
      expect(wrapper.vm.scriptReferences[0].option._varId).toBe(1)
      wrapper.vm.projectRefs[0].refLabel.label = '客户年龄'
      wrapper.vm.varPickerOptions[0].varLabelText = '客户年龄'
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.qlReferenceItems).not.toBe(refs)
      expect(wrapper.vm.qlReferenceItems[0].varLabel).toBe('客户年龄')
      expect(wrapper.vm.scriptReferences[0].label).toBe('客户年龄')
      expect(wrapper.vm.scriptReferences[0].option._varId).toBe(1)
    } finally { wrapper.unmount() }
  })
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
    const items = ScriptEditor.computed.scriptPickerItems.call({
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
    ScriptEditor.methods.insertVar.call(context, items[0])
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
