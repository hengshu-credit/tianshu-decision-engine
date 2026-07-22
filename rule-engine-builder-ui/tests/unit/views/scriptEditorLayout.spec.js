import ScriptEditor from '@/views/designer/ScriptEditor.vue'

describe('ScriptEditor Monaco layout', () => {
  test('折叠变量面板时不触发 Monaco 运行期重建', () => {
    const context = { varPanelCollapsed: false }

    ScriptEditor.methods.toggleVarPanel.call(context)

    expect(context.varPanelCollapsed).toBe(true)
  })

  test('设计器不提供运行期 Monaco 尺寸刷新入口', () => {
    expect(ScriptEditor.methods.scheduleEditorRefresh).toBeUndefined()
  })
})
