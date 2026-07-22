vi.unmock('@/components/common/ScriptPanel.vue')

const definitionApi = await vi.importMock('@/api/definition')
const ScriptPanel = (await vi.importActual('@/components/common/ScriptPanel.vue')).default

function createPanelContext(onBeforeCompile) {
  const ctx = {
    definitionId: 12,
    isScriptMode: false,
    onBeforeCompile,
    compiling: false,
    editScript: '',
    content: {},
    $message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn()
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
    vi.clearAllMocks()
  })

  test('前置保存返回 false 时停止编译', async () => {
    const onBeforeCompile = vi.fn().mockResolvedValue(false)
    const ctx = createPanelContext(onBeforeCompile)

    await ctx.handleCompile()

    expect(onBeforeCompile).toHaveBeenCalledTimes(1)
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(ctx.compiling).toBe(false)
  })

  test('前置保存失败时停止编译', async () => {
    const onBeforeCompile = vi.fn().mockRejectedValue(new Error('save failed'))
    const ctx = createPanelContext(onBeforeCompile)

    await ctx.handleCompile()

    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    expect(ctx.compiling).toBe(false)
  })
})
