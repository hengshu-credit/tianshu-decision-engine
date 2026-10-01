import { mount } from '@test-utils'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import { SIDEBAR_MENUS } from '@/layout/layoutState'

function mountSidebar(overrides = {}) {
  return mount(LayoutSidebar, {
    props: {
      width: 220,
      compact: false,
      activeMenu: '/project',
      menus: SIDEBAR_MENUS,
      ...overrides
    }
  })
}

describe('LayoutSidebar', () => {
  test('展开状态显示完整品牌和菜单且不再承载账号信息', () => {
    const wrapper = mountSidebar({
      loginEnabled: true,
      username: 'admin',
      avatarInitial: 'A'
    })

    expect(wrapper.find('.brand-copy').text()).toContain('天枢决策引擎')
    expect(wrapper.find('.brand-copy').text()).toContain('天工开物，枢衡定策')
    expect(wrapper.findAll('.menu-label')).toHaveLength(SIDEBAR_MENUS.length)
    expect(wrapper.find('.sidebar-account').exists()).toBe(false)
    wrapper.unmount()
  })

  test('窄栏只保留 Logo 和菜单图标', () => {
    const wrapper = mountSidebar({
      width: 64,
      compact: true,
      loginEnabled: true,
      username: 'admin',
      avatarInitial: 'A'
    })

    expect(wrapper.find('.sidebar-brand__logo').exists()).toBe(true)
    expect(wrapper.find('.brand-copy').exists()).toBe(false)
    expect(wrapper.findAll('.menu-label')).toHaveLength(0)
    expect(wrapper.find('.sidebar-account').exists()).toBe(false)
    wrapper.unmount()
  })

  test('窄栏菜单使用原名称作为 Hover 提示', () => {
    const wrapper = mountSidebar({ compact: true, width: 64 })
    const items = wrapper.findAll('.sidebar-menu__item')

    expect(items.at(0).attributes('title')).toBe('数据看板')
    expect(items.at(13).attributes('title')).toBe('账单管理')
    wrapper.unmount()
  })

  test('活动菜单拥有可见状态并点击发送导航路径', async() => {
    const wrapper = mountSidebar({ activeMenu: '/rule' })
    const ruleItem = wrapper.find('[data-menu-path="/rule"]')

    expect(ruleItem.classes()).toContain('is-active')
    await ruleItem.trigger('click')
    expect(wrapper.emitted('navigate')[0]).toEqual(['/rule'])
    wrapper.unmount()
  })

  // 侧边栏折叠按钮当前不启用，对应交互用例随组件模板一并停用。
  // test('折叠按钮发送 toggle-collapse', async() => {
  //   const wrapper = mountSidebar()
  //   await wrapper.find('.sidebar-collapse').trigger('click')
  //   expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)
  //   wrapper.unmount()
  // })

  test('拖拽时按鼠标位移发送宽度并在结束时发送最终宽度', async() => {
    const wrapper = mountSidebar({ width: 220 })
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 280 }))
    window.dispatchEvent(new MouseEvent('mouseup', { clientX: 280 }))

    expect(wrapper.emitted('resize')[0]).toEqual([280])
    expect(wrapper.emitted('resize-end')[0]).toEqual([280])
    expect(wrapper.vm.resizing).toBe(false)
    wrapper.unmount()
  })

  test('拖拽结果由生产宽度规则约束', async() => {
    const wrapper = mountSidebar({ width: 220 })
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: -100 }))
    window.dispatchEvent(new MouseEvent('mouseup', { clientX: 1000 }))

    expect(wrapper.emitted('resize')[0]).toEqual([64])
    expect(wrapper.emitted('resize-end')[0]).toEqual([320])
    wrapper.unmount()
  })

  test('组件销毁时清理拖拽监听', async() => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const wrapper = mountSidebar()
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    wrapper.unmount()

    expect(removeSpy).toHaveBeenCalledWith('mousemove', wrapper.vm.handleResize)
    expect(removeSpy).toHaveBeenCalledWith('mouseup', wrapper.vm.finishResize)
    removeSpy.mockRestore()
  })

})
