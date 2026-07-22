import { mount } from '@test-utils'
import * as monaco from 'monaco-editor'
import MonacoEditor from '@/components/MonacoEditor.vue'

describe('MonacoEditor', () => {
  let editor

  beforeEach(() => {
    editor = {
      getValue: jest.fn(() => ''),
      getModel: jest.fn(() => ({ setValue: jest.fn() })),
      layout: jest.fn(),
      dispose: jest.fn(),
      onDidChangeModelContent: jest.fn(),
      addCommand: jest.fn(),
      updateOptions: jest.fn(),
    }
    monaco.editor.create.mockReset().mockReturnValue(editor)
    window.monaco = monaco
  })

  afterEach(() => {
    delete window.monaco
    jest.clearAllMocks()
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
