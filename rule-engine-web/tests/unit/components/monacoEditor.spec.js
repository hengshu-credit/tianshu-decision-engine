import { mount } from '@test-utils'
import { isProxy } from 'vue'
import * as monaco from 'monaco-editor'
import MonacoEditor from '@/components/MonacoEditor.vue'

describe('MonacoEditor', () => {
  test('加载 Monaco 期间卸载组件会取消等待，不留下计时器或创建编辑器', async () => {
    vi.useFakeTimers()
    delete window.monaco
    const wrapper = mount(MonacoEditor)
    try {
      expect(wrapper.vm.loading).toBe(true)
      expect(vi.getTimerCount()).toBeGreaterThan(0)
      wrapper.unmount()
      expect(vi.getTimerCount()).toBe(0)
      window.monaco = monaco
      await vi.runAllTimersAsync()
      expect(monaco.editor.create).not.toHaveBeenCalled()
    } finally {
      window.monaco = monaco
      await vi.runAllTimersAsync()
      vi.useRealTimers()
    }
  })

  let editor

  beforeEach(() => {
    editor = {
      getValue: vi.fn(() => ''),
      getModel: vi.fn(() => ({ setValue: vi.fn() })),
      deltaDecorations: vi.fn(() => ['reference-decoration']),
      layout: vi.fn(),
      dispose: vi.fn(),
      onDidChangeModelContent: vi.fn(),
      addCommand: vi.fn(),
      updateOptions: vi.fn(),
    }
    monaco.editor.create.mockReset().mockReturnValue(editor)
    window.monaco = monaco
  })

  afterEach(() => {
    delete window.monaco
    vi.clearAllMocks()
  })

  test('依赖 automaticLayout，不在挂载后重复执行无尺寸 layout', async () => {
    const wrapper = mount(MonacoEditor, {
      props: { value: 'result = true;' },
    })

    await wrapper.vm.$nextTick()

    expect(monaco.editor.create).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        value: 'result = true;',
        automaticLayout: true,
      })
    )
    expect(editor.layout).not.toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
    expect(isProxy(wrapper.vm.editor)).toBe(false)

    wrapper.unmount()
  })

  test('editor-ready 中插入排队内容时，内容监听已就绪并同步父组件', async () => {
    let changed
    editor.onDidChangeModelContent.mockImplementation(callback => { changed = callback })
    editor.getValue.mockReturnValue('age')
    const wrapper = mount(MonacoEditor, {
      props: { value: '', onEditorReady: () => { changed?.() } },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('input').at(-1)).toEqual(['age'])
    expect(wrapper.emitted('update:value')).toEqual([['age']])
    wrapper.unmount()
  })

  test('按编辑器实例注册 QL 引擎引用提示，并返回匹配的内容说明', async () => {
    const model = {
      getLanguageId: vi.fn(() => 'ql'),
      getLineContent: vi.fn(() => 'riskScore'),
      getWordUntilPosition: vi.fn(() => ({ startColumn: 1, endColumn: 6 })),
    }
    editor.getModel.mockReturnValue(model)
    monaco.languages.registerCompletionItemProvider.mockReturnValue({ dispose: vi.fn() })
    monaco.languages.registerHoverProvider.mockReturnValue({ dispose: vi.fn() })
    monaco.languages.registerSignatureHelpProvider.mockReturnValue({ dispose: vi.fn() })

    const wrapper = mount(MonacoEditor, {
      props: {
        language: 'ql',
        references: [{ refCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER', _varId: 8, _refType: 'VARIABLE' }],
        functions: [{ funcCode: 'between', funcName: '区间判断', paramsJson: '[{"name":"value","type":"NUMBER"}]' }],
      },
    })
    await wrapper.vm.$nextTick()

    const provider = monaco.languages.registerCompletionItemProvider.mock.calls.at(-1)[1]
    const result = await provider.provideCompletionItems(model, { lineNumber: 1, column: 6 })
    expect(result.suggestions.map(item => item.label)).toContain('riskScore')
    expect(result.suggestions[0].documentation).toEqual(expect.objectContaining({ value: expect.stringContaining('NUMBER') }))
    expect(result.suggestions[0].detail).toBe('变量 · 数值 / NUMBER · 风险分 · riskScore · —')
    await wrapper.setProps({ referenceVersions: { 'variable:8': { version: 3 } } })
    const updated = await provider.provideCompletionItems(model, { lineNumber: 1, column: 6 })
    expect(updated.suggestions[0].detail).toBe('变量 · 数值 / NUMBER · 风险分 · riskScore · v3')

    const hoverProvider = monaco.languages.registerHoverProvider.mock.calls.at(-1)[1]
    const hover = await hoverProvider.provideHover(model, { lineNumber: 1, column: 4 })
    expect(hover.contents.join ? hover.contents.join('') : hover.contents).toBeTruthy()
    wrapper.unmount()
  })

  test('通用编辑器按类型、编码、名称、版本绘制引擎引用标注', async () => {
    const model = {
      getLanguageId: vi.fn(() => 'ql'),
      getPositionAt: vi.fn((offset) => ({ lineNumber: 1, column: offset + 1 })),
    }
    editor.getModel.mockReturnValue(model)
    editor.getValue.mockReturnValue('return riskScore;')
    monaco.languages.registerCompletionItemProvider.mockReturnValue({ dispose: vi.fn() })

    const wrapper = mount(MonacoEditor, {
      props: {
        language: 'ql',
        value: 'return riskScore;',
        showReferenceDecorations: true,
        references: [{ refCode: 'riskScore', varLabel: '风险分', varType: 'NUMBER', _varId: 8, _refType: 'VARIABLE', varObj: { version: 4 } }],
      },
    })
    await wrapper.vm.$nextTick()

    const decorations = editor.deltaDecorations.mock.calls.at(-1)[1]
    expect(decorations.map(item => item.options.description)).toEqual([
      'engine-content-source', 'engine-content-type', 'engine-content-name', 'engine-content-code', 'engine-content-version',
    ])
    expect(decorations[0].options.before.content.trim()).toBe('变量')
    expect(decorations[1].options.before.content.trim()).toBe('N')
    expect(decorations[1].options.before.inlineClassName).toContain('content-ref__type--number')
    expect(decorations[2].options.before.content.trim()).toBe('风险分')
    expect(decorations[4].options.after.content.trim()).toBe('v4')
    wrapper.unmount()
  })

  test('连续输入即时同步源码，同一帧只更新一次标注并复用目录与提示服务', async () => {
    let changed, frame
    const frameSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(callback => { frame = callback; return 7 })
    const cancelSpy = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    editor.onDidChangeModelContent.mockImplementation(callback => { changed = callback })
    editor.getModel.mockReturnValue({ getPositionAt: offset => ({ lineNumber: 1, column: offset + 1 }) })
    editor.getValue.mockReturnValue('age')
    const wrapper = mount(MonacoEditor, { props: {
      value: 'age', language: 'ql', references: [{ refCode: 'age', varLabel: '年龄', varType: 'NUMBER', _varId: 1, _refType: 'VARIABLE' }],
    } })
    try {
      const catalog = wrapper.vm.referenceCatalog
      const normalized = vi.spyOn(wrapper.vm, 'normalizedReferenceItems')
      const registrations = monaco.languages.registerCompletionItemProvider.mock.calls.length
      editor.deltaDecorations.mockClear()
      for (const value of [' age', '  age', '  age + 1']) {
        editor.getValue.mockReturnValue(value)
        changed()
        await wrapper.setProps({ value })
      }
      expect(wrapper.emitted('update:value').map(event => event[0])).toEqual([' age', '  age', '  age + 1'])
      expect(editor.deltaDecorations).not.toHaveBeenCalled()
      expect(frameSpy).toHaveBeenCalledTimes(1)
      frame(0)
      expect(editor.deltaDecorations).toHaveBeenCalledTimes(1)
      expect(editor.deltaDecorations.mock.calls[0][1].find(item => item.options.description === 'engine-content-code').range.startColumn).toBe(3)
      expect(wrapper.vm.referenceCatalog).toBe(catalog)
      expect(normalized).not.toHaveBeenCalled()
      expect(monaco.languages.registerCompletionItemProvider.mock.calls.length).toBe(registrations)
      changed()
      wrapper.unmount()
      expect(cancelSpy).toHaveBeenCalledWith(7)
    } finally { if (wrapper.exists()) wrapper.unmount(); frameSpy.mockRestore(); cancelSpy.mockRestore() }
  })
})
