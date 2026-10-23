import { mount } from '@test-utils'
import * as monaco from 'monaco-editor'

vi.unmock('@/components/common/ScriptPanel.vue')

const ScriptPanel = (
  await vi.importActual('@/components/common/ScriptPanel.vue')
).default

function mountPanel(compileResult = null) {
  const messages = {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  }
  const wrapper = mount(ScriptPanel, {
    props: { compileResult },
    mocks: { $message: messages },
  })
  return { wrapper, messages }
}

describe('ScriptPanel', () => {
  test('历史源码标明所选版本，保留原始文本且只读', () => {
    const script = '// version 3\nreturn 42;\n'
    const { wrapper } = mountPanel({ compileSuccess: true, compiledScript: script, saved: true, sourceLabel: '版本 3' })
    expect(wrapper.find('textarea').element.value).toBe(script)
    expect(wrapper.find('textarea').element.readOnly).toBe(true)
    expect(wrapper.text()).toContain('显示版本 3已保存的编译源码')
    expect(wrapper.text()).toContain('已保存源码')
    wrapper.unmount()
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('编译失败结果只展示诊断且不再提供第二个保存编译入口', () => {
    const { wrapper } = mountPanel({
      compileSuccess: false,
      compileMessage: '脚本解析失败',
      issues: [{ code: 'QL_PARSE_ERROR' }],
    })

    expect(wrapper.text()).toContain('脚本生成失败')
    expect(wrapper.text()).toContain('脚本解析失败')
    expect(wrapper.findAll('button').map((button) => button.text()))
      .not.toContain('保存并编译')
    expect(wrapper.vm.handleCompile).toBeUndefined()
    wrapper.unmount()
  })

  test('保存并检查成功后刷新只读脚本预览', async() => {
    const { wrapper } = mountPanel()

    await wrapper.setProps({
      compileResult: {
        compileSuccess: true,
        revision: { compiledScript: 'result = input.score;' },
        issues: [],
      },
    })

    expect(wrapper.text()).toContain('脚本已生成')
    expect(wrapper.vm.editScript).toBe('result = input.score;')
    expect(wrapper.find('textarea').element.readOnly).toBe(true)
    wrapper.unmount()
  })

  test('未检查状态提示从顶部唯一入口生成脚本', () => {
    const { wrapper } = mountPanel()

    expect(wrapper.text()).toContain('由顶部“编译”生成')
    expect(wrapper.text()).not.toContain('保存并编译仅更新草稿')
    expect(wrapper.find('[data-testid="designer-lifecycle-guidance"]').exists())
      .toBe(false)
    expect(wrapper.find('textarea').attributes('placeholder'))
      .toBe('请先点击顶部“编译”生成脚本')
    wrapper.unmount()
  })

  test('空脚本复制提示回到统一检查流程', () => {
    const { wrapper, messages } = mountPanel()

    wrapper.vm.copyScript()

    expect(messages.warning).toHaveBeenCalledWith(
      '暂无脚本，请先编译'
    )
    wrapper.unmount()
  })

  test('编译脚本预览把字段目录和函数目录交给公共编辑器解析', () => {
    const references = [{ refCode: 'age', varLabel: '年龄', varType: 'INTEGER', _varId: 1, _refType: 'VARIABLE' }]
    const functions = [{ funcCode: 'risk', funcName: '风险计算', id: 2 }]
    window.monaco = monaco
    const wrapper = mount(ScriptPanel, {
      props: { compileResult: { compileSuccess: true, compiledScript: 'return risk(age);' }, references, functions },
      mocks: { $message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } },
    })
    const editor = wrapper.findComponent({ name: 'MonacoEditor' })
    expect(editor.props('references')).toEqual(references)
    expect(editor.props('functions')).toEqual(functions)
    wrapper.unmount()
    delete window.monaco
  })
})
