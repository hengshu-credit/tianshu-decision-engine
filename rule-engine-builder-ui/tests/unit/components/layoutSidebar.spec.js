import { mount } from '@vue/test-utils'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import { SIDEBAR_MENUS } from '@/layout/layoutState'

function mountSidebar(overrides = {}) {
  return mount(LayoutSidebar, {
    propsData: {
      width: 220,
      compact: false,
      activeMenu: '/project',
      menus: SIDEBAR_MENUS,
      loginEnabled: false,
      username: '',
      avatarInitial: 'U',
      ...overrides
    },
    stubs: {
      'el-dropdown': { template: '<div class="dropdown-stub"><slot /><slot name="dropdown" /></div>' },
      'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': { template: '<button><slot /></button>' }
    }
  })
}

describe('LayoutSidebar', () => {
  test('展开状态显示完整品牌、菜单名称和账号操作', () => {
    const wrapper = mountSidebar({
      loginEnabled: true,
      username: 'admin',
      avatarInitial: 'A'
    })

    expect(wrapper.find('.brand-copy').text()).toContain('天枢决策引擎')
    expect(wrapper.find('.brand-copy').text()).toContain('天工开物，枢衡定策')
    expect(wrapper.findAll('.menu-label')).toHaveLength(13)
    expect(wrapper.find('.account-name').text()).toBe('admin')
    expect(wrapper.find('.account-logout').exists()).toBe(true)
    wrapper.destroy()
  })

  test('窄栏只保留 Logo、菜单图标和头像', () => {
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
    expect(wrapper.find('.account-name').exists()).toBe(false)
    expect(wrapper.find('.account-logout').exists()).toBe(false)
    expect(wrapper.find('.account-avatar').text()).toBe('A')
    wrapper.destroy()
  })

  test('窄栏菜单使用原名称作为 Hover 提示', () => {
    const wrapper = mountSidebar({ compact: true, width: 64 })
    const items = wrapper.findAll('.sidebar-menu__item')

    expect(items.at(0).attributes('title')).toBe('项目管理')
    expect(items.at(12).attributes('title')).toBe('账单管理')
    wrapper.destroy()
  })

  test('活动菜单拥有可见状态并点击发送导航路径', async() => {
    const wrapper = mountSidebar({ activeMenu: '/rule' })
    const ruleItem = wrapper.find('[data-menu-path="/rule"]')

    expect(ruleItem.classes()).toContain('is-active')
    await ruleItem.trigger('click')
    expect(wrapper.emitted('navigate')[0]).toEqual(['/rule'])
    wrapper.destroy()
  })

  test('折叠按钮发送 toggle-collapse', async() => {
    const wrapper = mountSidebar()
    await wrapper.find('.sidebar-collapse').trigger('click')
    expect(wrapper.emitted('toggle-collapse')).toHaveLength(1)
    wrapper.destroy()
  })

  test('拖拽时按鼠标位移发送宽度并在结束时发送最终宽度', async() => {
    const wrapper = mountSidebar({ width: 220 })
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 280 }))
    window.dispatchEvent(new MouseEvent('mouseup', { clientX: 280 }))

    expect(wrapper.emitted('resize')[0]).toEqual([280])
    expect(wrapper.emitted('resize-end')[0]).toEqual([280])
    expect(wrapper.vm.resizing).toBe(false)
    wrapper.destroy()
  })

  test('拖拽结果由生产宽度规则约束', async() => {
    const wrapper = mountSidebar({ width: 220 })
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: -100 }))
    window.dispatchEvent(new MouseEvent('mouseup', { clientX: 1000 }))

    expect(wrapper.emitted('resize')[0]).toEqual([64])
    expect(wrapper.emitted('resize-end')[0]).toEqual([320])
    wrapper.destroy()
  })

  test('组件销毁时清理拖拽监听', async() => {
    const removeSpy = jest.spyOn(window, 'removeEventListener')
    const wrapper = mountSidebar()
    await wrapper.find('.sidebar-resizer').trigger('mousedown', { clientX: 220 })

    wrapper.destroy()

    expect(removeSpy).toHaveBeenCalledWith('mousemove', wrapper.vm.handleResize)
    expect(removeSpy).toHaveBeenCalledWith('mouseup', wrapper.vm.finishResize)
    removeSpy.mockRestore()
  })

  test('展开账号区和窄栏账号菜单都可发送退出事件', async() => {
    const expanded = mountSidebar({ loginEnabled: true, username: 'admin', avatarInitial: 'A' })
    await expanded.find('.account-logout').trigger('click')
    expect(expanded.emitted('logout')).toHaveLength(1)
    expanded.destroy()

    const compact = mountSidebar({
      compact: true,
      width: 64,
      loginEnabled: true,
      username: 'admin',
      avatarInitial: 'A'
    })
    compact.vm.handleAccountCommand('logout')
    expect(compact.emitted('logout')).toHaveLength(1)
    compact.destroy()
  })

  test('未启用登录时不显示账号区', () => {
    const wrapper = mountSidebar({ loginEnabled: false })
    expect(wrapper.find('.sidebar-account').exists()).toBe(false)
    wrapper.destroy()
  })
})
