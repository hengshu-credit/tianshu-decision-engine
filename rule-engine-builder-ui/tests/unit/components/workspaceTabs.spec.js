import { mount } from '@vue/test-utils'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'

const TABS = [
  { fullPath: '/project', path: '/project', name: 'ProjectList', title: '项目管理' },
  { fullPath: '/rule', path: '/rule', name: 'RuleList', title: '规则管理' },
  { fullPath: '/variable', path: '/variable', name: 'VariableList', title: '变量管理' }
]

function mountTabs(overrides = {}) {
  return mount(WorkspaceTabs, {
    propsData: {
      tabs: TABS,
      activePath: '/rule',
      ...overrides
    },
    stubs: {
      'el-dropdown': { template: '<div class="dropdown-stub"><slot /><slot name="dropdown" /></div>' },
      'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': {
        props: ['command', 'disabled', 'divided'],
        template: '<button :disabled="disabled"><slot /></button>'
      }
    }
  })
}

describe('WorkspaceTabs', () => {
  test('渲染所有页签并标记活动页', () => {
    const wrapper = mountTabs()

    expect(wrapper.findAll('.workspace-tab')).toHaveLength(3)
    expect(wrapper.find('[data-tab="/rule"]').classes()).toContain('is-active')
    expect(wrapper.find('[data-tab="/project"]').classes()).not.toContain('is-active')
    wrapper.destroy()
  })

  test('点击页签和关闭按钮发送不同意图', async() => {
    const wrapper = mountTabs()

    await wrapper.find('[data-path="/project"]').trigger('click')
    await wrapper.find('[data-close="/project"]').trigger('click')

    expect(wrapper.emitted('activate')[0]).toEqual(['/project'])
    expect(wrapper.emitted('operate')[0]).toEqual([{
      operation: 'current',
      targetPath: '/project'
    }])
    wrapper.destroy()
  })

  test('右键页签打开定位菜单并可关闭其他页签', async() => {
    const wrapper = mountTabs()

    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu', {
      clientX: 80,
      clientY: 40
    })

    expect(wrapper.find('.workspace-tabs__context-menu').exists()).toBe(true)
    expect(wrapper.find('.workspace-tabs__context-menu').attributes('style')).toContain('left: 80px')
    await wrapper.find('[data-operation="others"]').trigger('click')
    expect(wrapper.emitted('operate')[0]).toEqual([{
      operation: 'others',
      targetPath: '/rule'
    }])
    expect(wrapper.find('.workspace-tabs__context-menu').exists()).toBe(false)
    wrapper.destroy()
  })

  test('首个页签禁用关闭左侧，末尾页签禁用关闭右侧', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/project"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="left"]').attributes('disabled')).toBe('disabled')
    expect(wrapper.find('[data-operation="right"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-tab="/variable"]').trigger('contextmenu')
    expect(wrapper.find('[data-operation="right"]').attributes('disabled')).toBe('disabled')
    wrapper.destroy()
  })

  test('只有一个页签时禁用关闭其他', async() => {
    const wrapper = mountTabs({ tabs: [TABS[0]], activePath: '/project' })
    await wrapper.find('[data-tab="/project"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="others"]').attributes('disabled')).toBe('disabled')
    wrapper.destroy()
  })

  test('右键菜单可刷新目标页签', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/variable"]').trigger('contextmenu')
    await wrapper.find('[data-operation="refresh"]').trigger('click')

    expect(wrapper.emitted('operate')[0]).toEqual([{
      operation: 'refresh',
      targetPath: '/variable'
    }])
    wrapper.destroy()
  })

  test('关闭和刷新操作展示对应快捷键', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="refresh"] .workspace-tab-operation__shortcut').text()).toBe('Ctrl+R')
    expect(wrapper.find('[data-operation="current"] .workspace-tab-operation__shortcut').text()).toBe('Ctrl+W')
    expect(wrapper.find('[data-operation="others"] .workspace-tab-operation__shortcut').exists()).toBe(false)
    wrapper.destroy()
  })

  test('统一操作入口作用于当前活动页签', () => {
    const wrapper = mountTabs()
    wrapper.vm.handleOverflowCommand('all')

    expect(wrapper.emitted('operate')[0]).toEqual([{
      operation: 'all',
      targetPath: '/rule'
    }])
    wrapper.destroy()
  })

  test('点击页签区域外关闭右键菜单', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu')
    expect(wrapper.vm.contextMenu.visible).toBe(true)

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.contextMenu.visible).toBe(false)
    wrapper.destroy()
  })

  test('组件销毁时清理全局监听', () => {
    const removeDocumentSpy = jest.spyOn(document, 'removeEventListener')
    const removeWindowSpy = jest.spyOn(window, 'removeEventListener')
    const wrapper = mountTabs()

    wrapper.destroy()

    expect(removeDocumentSpy).toHaveBeenCalledWith('click', wrapper.vm.closeContextMenu)
    expect(removeWindowSpy).toHaveBeenCalledWith('blur', wrapper.vm.closeContextMenu)
    removeDocumentSpy.mockRestore()
    removeWindowSpy.mockRestore()
  })
})
