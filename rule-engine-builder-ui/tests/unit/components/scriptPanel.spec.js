import { mount } from '@test-utils'

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
})
