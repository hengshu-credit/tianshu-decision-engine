jest.unmock('@/components/common/ScriptPanel.vue')

const definitionApi = require('@/api/definition')
const ScriptPanel = require('@/components/common/ScriptPanel.vue').default

function createPanelContext(onBeforeCompile) {
  const ctx = {
    definitionId: 12,
    isScriptMode: false,
    onBeforeCompile,
    compiling: false,
    editScript: '',
    content: {},
    $message: {
      success: jest.fn(),
      error: jest.fn(),
      warning: jest.fn()
    },
    async loadContent() {}
  }

  Object.keys(ScriptPanel.methods).forEach(name => {
    ctx[name] = ScriptPanel.methods[name].bind(ctx)
  })
  return ctx
}

describe('ScriptPanel', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('前置保存返回 false 时停止编译', async () => {
    const onBeforeCompile = jest.fn().mockResolvedValue(false)
    const ctx = createPanelContext(onBeforeCompile)

    await ctx.handleCompile()

    expect(onBeforeCompile).toHaveBeenCalledTimes(1)
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(ctx.compiling).toBe(false)
  })

  test('前置保存失败时停止编译', async () => {
    const onBeforeCompile = jest.fn().mockRejectedValue(new Error('save failed'))
    const ctx = createPanelContext(onBeforeCompile)

    await ctx.handleCompile()

    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(ctx.compiling).toBe(false)
  })
})
