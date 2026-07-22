import { mount } from '@test-utils'
import * as monaco from 'monaco-editor'
import MonacoEditor from '@/components/MonacoEditor.vue'

describe('MonacoEditor', () => {
  let editor

  beforeEach(() => {
    editor = {
      getValue: vi.fn(() => ''),
      getModel: vi.fn(() => ({ setValue: vi.fn() })),
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

    wrapper.unmount()
  })
})
