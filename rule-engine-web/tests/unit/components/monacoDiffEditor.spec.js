import { mount } from '@test-utils'
import { isProxy } from 'vue'
import * as monaco from 'monaco-editor'
import MonacoDiffEditor from '@/components/rule/versionDiff/MonacoDiffEditor.vue'

describe('MonacoDiffEditor', () => {
  let originalModel
  let modifiedModel
  let diffEditor

  beforeEach(() => {
    originalModel = { getValue: vi.fn(() => 'a = 1;'), setValue: vi.fn(), dispose: vi.fn() }
    modifiedModel = { getValue: vi.fn(() => 'a = 2;'), setValue: vi.fn(), dispose: vi.fn() }
    diffEditor = { setModel: vi.fn(), layout: vi.fn(), dispose: vi.fn() }
    monaco.editor.createModel
      .mockReset()
      .mockReturnValueOnce(originalModel)
      .mockReturnValueOnce(modifiedModel)
    monaco.editor.createDiffEditor.mockReset().mockReturnValue(diffEditor)
    window.monaco = monaco
  })

  afterEach(() => {
    delete window.monaco
    vi.clearAllMocks()
  })

  test('创建只读 QL diff 并绑定左右模型', async() => {
    const wrapper = mount(MonacoDiffEditor, {
      props: { original: 'a = 1;', modified: 'a = 2;', language: 'ql' }
    })
    await wrapper.vm.$nextTick()

    expect(monaco.editor.createModel).toHaveBeenNthCalledWith(1, 'a = 1;', 'ql')
    expect(monaco.editor.createModel).toHaveBeenNthCalledWith(2, 'a = 2;', 'ql')
    expect(monaco.editor.createDiffEditor).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ readOnly: true, renderSideBySide: true, automaticLayout: true })
    )
    expect(diffEditor.setModel).toHaveBeenCalledWith({ original: originalModel, modified: modifiedModel })
    expect(wrapper.find('.monaco-diff-editor-container').exists()).toBe(true)
    expect(isProxy(wrapper.vm.diffEditor)).toBe(false)
    expect(isProxy(wrapper.vm.originalModel)).toBe(false)
    expect(isProxy(wrapper.vm.modifiedModel)).toBe(false)
  })

  test('属性变化只更新对应模型内容', async() => {
    const wrapper = mount(MonacoDiffEditor, {
      props: { original: 'a = 1;', modified: 'a = 2;', language: 'ql' }
    })
    await wrapper.vm.$nextTick()

    await wrapper.setProps({ original: 'a = 0;', modified: 'a = 3;' })

    expect(originalModel.setValue).toHaveBeenCalledWith('a = 0;')
    expect(modifiedModel.setValue).toHaveBeenCalledWith('a = 3;')
    expect(monaco.editor.createDiffEditor).toHaveBeenCalledTimes(1)
  })

  test('组件销毁时释放 diff editor 和两个模型', async() => {
    const wrapper = mount(MonacoDiffEditor, {
      props: { original: 'a = 1;', modified: 'a = 2;', language: 'ql' }
    })
    await wrapper.vm.$nextTick()

    wrapper.unmount()

    expect(diffEditor.dispose).toHaveBeenCalled()
    expect(originalModel.dispose).toHaveBeenCalled()
    expect(modifiedModel.dispose).toHaveBeenCalled()
  })
})
