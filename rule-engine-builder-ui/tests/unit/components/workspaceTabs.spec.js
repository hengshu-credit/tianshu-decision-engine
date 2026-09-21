import { mount } from '@test-utils'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import { SIDEBAR_MENUS } from '@/layout/layoutState'

const TABS = [
  { fullPath: '/project', path: '/project', name: 'ProjectList', title: '项目管理' },
  { fullPath: '/rule', path: '/rule', name: 'RuleList', title: '规则管理' },
  { fullPath: '/variable', path: '/variable', name: 'VariableList', title: '变量管理' }
]

function mountTabs(overrides = {}) {
  return mount(WorkspaceTabs, {
    props: {
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
  test('业务页签同时显示名称和具体标题，并将完整内容交给悬停提示', () => {
    const title = '授信准入规则'.repeat(12)
    const wrapper = mountTabs({ tabs: [{ ...TABS[1], path: '/rule/8', fullPath: '/rule/8', title: '规则详情', detailTitle: title }] })
    const fullTitle = `规则 · ${title}`

    expect(wrapper.find('.workspace-tab__title').text()).toBe(fullTitle)
    expect(wrapper.find('.workspace-tab__main').attributes('aria-label')).toBe(fullTitle)
    expect(wrapper.find('.workspace-tab__close').attributes('aria-label')).toBe(`关闭${fullTitle}`)
    expect(wrapper.find('el-tooltip-stub').attributes('content')).toBe(fullTitle)
    wrapper.unmount()
  })

  test.each([
    ['项目详情', '授信项目', '项目 · 授信项目'],
    ['决策表设计器', '准入规则', '决策表 · 准入规则'],
    ['QL脚本编辑器', '计算额度', 'QL脚本 · 计算额度'],
    ['外数数据源详情', '征信服务', '外数 · 征信服务'],
    ['外数 API 详情', '风险查询', 'API · 风险查询'],
    ['数据库数据源详情', '风控只读库', '数据库 · 风控只读库'],
    ['配置表达式', '决策表 · 左操作数', '表达式 · 决策表 · 左操作数'],
  ])('页签名称 %s 简化后保留具体业务标题', (title, detailTitle, expected) => {
    const wrapper = mountTabs({ tabs: [{ ...TABS[0], path: '/project/8', fullPath: '/project/8', title, detailTitle }] })
    expect(wrapper.find('.workspace-tab__title').text()).toBe(expected)
    wrapper.unmount()
  })

  test.each(SIDEBAR_MENUS)('一级页面 $label 保留完整菜单名称，包括带查询参数的缓存页签', ({ index, label }) => {
    const wrapper = mountTabs({ tabs: [{ path: index, fullPath: index + '?projectId=3', title: label }] })
    expect(wrapper.get('.workspace-tab__title').text()).toBe(label)
    expect(wrapper.get('.workspace-tab__main').attributes('aria-label')).toBe(label)
    expect(wrapper.get('.workspace-tab__close').attributes('aria-label')).toBe('关闭' + label)
    wrapper.unmount()
  })

  test('渲染所有页签并标记活动页', () => {
    const wrapper = mountTabs()

    expect(wrapper.findAll('.workspace-tab')).toHaveLength(3)
    expect(wrapper.find('[data-tab="/rule"]').classes()).toContain('is-active')
    expect(wrapper.find('[data-tab="/project"]').classes()).not.toContain('is-active')
    wrapper.unmount()
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
    wrapper.unmount()
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
    wrapper.unmount()
  })

  test('首个页签禁用关闭左侧，末尾页签禁用关闭右侧', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/project"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="left"]').attributes('disabled')).toBe('')
    expect(wrapper.find('[data-operation="right"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('[data-tab="/variable"]').trigger('contextmenu')
    expect(wrapper.find('[data-operation="right"]').attributes('disabled')).toBe('')
    wrapper.unmount()
  })

  test('只有一个页签时禁用关闭其他', async() => {
    const wrapper = mountTabs({ tabs: [TABS[0]], activePath: '/project' })
    await wrapper.find('[data-tab="/project"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="others"]').attributes('disabled')).toBe('')
    wrapper.unmount()
  })

  test('右键菜单可刷新目标页签', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/variable"]').trigger('contextmenu')
    await wrapper.find('[data-operation="refresh"]').trigger('click')

    expect(wrapper.emitted('operate')[0]).toEqual([{
      operation: 'refresh',
      targetPath: '/variable'
    }])
    wrapper.unmount()
  })

  test('关闭和刷新操作展示对应快捷键', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu')

    expect(wrapper.find('[data-operation="refresh"] .workspace-tab-operation__shortcut').text()).toBe('Ctrl+R')
    expect(wrapper.find('[data-operation="current"] .workspace-tab-operation__shortcut').text()).toBe('Ctrl+W')
    expect(wrapper.find('[data-operation="others"] .workspace-tab-operation__shortcut').exists()).toBe(false)
    wrapper.unmount()
  })

  test('移除页签省略号入口且保留右键操作能力', async() => {
    const wrapper = mountTabs()
    expect(wrapper.find('.workspace-tabs__more').exists()).toBe(false)

    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu')
    expect(wrapper.find('[data-operation="all"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('点击页签区域外关闭右键菜单', async() => {
    const wrapper = mountTabs()
    await wrapper.find('[data-tab="/rule"]').trigger('contextmenu')
    expect(wrapper.vm.contextMenu.visible).toBe(true)

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.contextMenu.visible).toBe(false)
    wrapper.unmount()
  })

  test('组件销毁时清理全局监听', () => {
    const removeDocumentSpy = vi.spyOn(document, 'removeEventListener')
    const removeWindowSpy = vi.spyOn(window, 'removeEventListener')
    const wrapper = mountTabs()

    wrapper.unmount()

    expect(removeDocumentSpy).toHaveBeenCalledWith('click', wrapper.vm.closeContextMenu)
    expect(removeWindowSpy).toHaveBeenCalledWith('blur', wrapper.vm.closeContextMenu)
    removeDocumentSpy.mockRestore()
    removeWindowSpy.mockRestore()
  })
})
