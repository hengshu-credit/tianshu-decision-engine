vi.unmock('@/layout/index.vue')

vi.mock('@/api/auth', () => ({
  getConsoleAuthConfig: vi.fn(),
  getConsoleMe: vi.fn(),
  consoleLogout: vi.fn()
}))

import { nextTick } from 'vue'
import { createStore } from 'vuex'
import { shallowMount } from '@test-utils'
import * as authApi from '@/api/auth'
import Layout from '@/layout/index.vue'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import expressionSessions from '@/store/modules/expressionSessions'
import workspaceTabs from '@/store/modules/workspaceTabs'
import {
  SIDEBAR_COMPACT_THRESHOLD,
  SIDEBAR_DEFAULT_WIDTH,
  SIDEBAR_MAX_WIDTH,
  SIDEBAR_MENUS,
  SIDEBAR_MIN_WIDTH,
  clampSidebarWidth,
  getActiveMenuIndex,
  getAvatarInitial,
  isEditableShortcutTarget,
  isWorkspaceRoute,
  readSidebarState,
  resolveCloseOperation,
  resolveTabSwitchPath,
  resolveWorkspaceShortcut,
  routeToTab,
  writeSidebarState
} from '@/layout/layoutState'
describe('Layout — 菜单与路由归属', () => {
  test('包含 13 个路由唯一、图标唯一的菜单项', () => {
    expect(SIDEBAR_MENUS).toHaveLength(13)
    expect(new Set(SIDEBAR_MENUS.map(item => item.index)).size).toBe(13)
    expect(new Set(SIDEBAR_MENUS.map(item => item.icon)).size).toBe(13)
  })

  test('菜单使用确认后的语义图标映射', () => {
    expect(SIDEBAR_MENUS).toEqual([
      { index: '/project', label: '项目管理', icon: 'FolderOpened' },
      { index: '/rule', label: '规则管理', icon: 'Operation' },
      { index: '/variable', label: '变量管理', icon: 'Collection' },
      { index: '/list', label: '名单管理', icon: 'Notebook' },
      { index: '/datasource', label: '外数管理', icon: 'Connection' },
      { index: '/database', label: '数据库管理', icon: 'Coin' },
      { index: '/model', label: '模型管理', icon: 'Cpu' },
      { index: '/function', label: '函数管理', icon: 'ScaleToOriginal' },
      { index: '/test', label: '规则测试', icon: 'VideoPlay' },
      { index: '/lineage', label: '血缘分析', icon: 'Share' },
      { index: '/experiment', label: '分流实验', icon: 'Flag' },
      { index: '/log', label: '执行日志', icon: 'DocumentChecked' },
      { index: '/billing', label: '账单管理', icon: 'Wallet' }
    ])
  })

  test.each([
    ['/designer/table/1', '/rule'],
    ['/designer/expression/1/session-1', '/rule'],
    ['/project/1', '/project'],
    ['/rule/1', '/rule'],
    ['/list/1', '/list'],
    ['/model/1', '/model'],
    ['/function/version/1', '/function'],
    ['/datasource/api/1', '/datasource'],
    ['/database/1', '/database'],
    ['/experiment/detail/1', '/experiment'],
    ['/billing', '/billing']
  ])('%s 高亮 %s', (path, menu) => {
    expect(getActiveMenuIndex(path)).toBe(menu)
  })
})

describe('Layout — 侧栏状态', () => {
  test('宽度常量符合约定', () => {
    expect(SIDEBAR_MIN_WIDTH).toBe(64)
    expect(SIDEBAR_MAX_WIDTH).toBe(320)
    expect(SIDEBAR_DEFAULT_WIDTH).toBe(220)
    expect(SIDEBAR_COMPACT_THRESHOLD).toBe(168)
  })

  test('宽度限制在 64 到 320 且非法值回退默认值', () => {
    expect(clampSidebarWidth(20)).toBe(64)
    expect(clampSidebarWidth(200.4)).toBe(200)
    expect(clampSidebarWidth(500)).toBe(320)
    expect(clampSidebarWidth('bad')).toBe(220)
  })

  test('正常读写本次会话中的宽度与上次展开宽度', () => {
    const storage = {
      value: null,
      getItem: vi.fn(function() { return this.value }),
      setItem: vi.fn(function(key, value) { this.value = value })
    }

    writeSidebarState(storage, { width: 64, lastExpandedWidth: 248 })

    expect(readSidebarState(storage)).toEqual({ width: 64, lastExpandedWidth: 248 })
  })

  test.each([null, '{bad', '{"width":999,"lastExpandedWidth":"bad"}'])(
    '缓存为 %s 时回退或约束到安全值',
    value => {
      const storage = { getItem: vi.fn(() => value) }
      const state = readSidebarState(storage)
      expect(state.width).toBeGreaterThanOrEqual(64)
      expect(state.width).toBeLessThanOrEqual(320)
      expect(state.lastExpandedWidth).toBeGreaterThanOrEqual(168)
      expect(state.lastExpandedWidth).toBeLessThanOrEqual(320)
    }
  )

  test('存储不可用时保持默认状态且不抛错', () => {
    const storage = {
      getItem: vi.fn(() => { throw new Error('blocked') }),
      setItem: vi.fn(() => { throw new Error('blocked') })
    }
    expect(readSidebarState(storage)).toEqual({ width: 220, lastExpandedWidth: 220 })
    expect(() => writeSidebarState(storage, { width: 180, lastExpandedWidth: 180 })).not.toThrow()
  })
})

describe('Layout — 用户头像', () => {
  test.each([
    ['admin', 'A'],
    ['  bob', 'B'],
    ['8risk', '8'],
    ['张三', 'Z'],
    ['李雷', 'L'],
    ['', 'U'],
    ['   ', 'U']
  ])('%s 显示为 %s', (username, expected) => {
    expect(getAvatarInitial(username)).toBe(expected)
  })
})

describe('Layout — 页签规范化与关闭决策', () => {
  const tabs = ['/a', '/b', '/c'].map(fullPath => ({ fullPath }))

  test('路由转换为稳定页签，标题按 meta、name、path 依次回退', () => {
    expect(routeToTab({
      fullPath: '/rule?page=1',
      path: '/rule',
      name: 'RuleList',
      meta: { title: '规则管理' }
    })).toEqual({ fullPath: '/rule?page=1', path: '/rule', name: 'RuleList', title: '规则管理' })
    expect(routeToTab({ fullPath: '/x', path: '/x', name: 'X', meta: {} }).title).toBe('X')
    expect(routeToTab({ fullPath: '/y', path: '/y', meta: {} }).title).toBe('/y')
  })

  test('仅布局下的非登录路由可恢复为工作区页签', () => {
    expect(isWorkspaceRoute({ path: '/rule', matched: [{ path: '' }, { path: 'rule' }] })).toBe(true)
    expect(isWorkspaceRoute({ path: '/login', matched: [{ path: '/login' }] })).toBe(false)
    expect(isWorkspaceRoute({ path: '/missing', matched: [] })).toBe(false)
  })

  test('关闭活动页优先选择右侧相邻页', () => {
    expect(resolveCloseOperation(tabs, '/b', '/b', 'current')).toEqual({
      tabs: [{ fullPath: '/a' }, { fullPath: '/c' }],
      nextPath: '/c'
    })
  })

  test('关闭末尾活动页选择左侧相邻页', () => {
    expect(resolveCloseOperation(tabs, '/c', '/c', 'current').nextPath).toBe('/b')
  })

  test('关闭非活动页保持当前页', () => {
    expect(resolveCloseOperation(tabs, '/a', '/c', 'current').nextPath).toBe('/a')
  })

  test.each([
    ['left', ['/b', '/c']],
    ['right', ['/a', '/b']],
    ['others', ['/b']],
    ['all', []]
  ])('%s 批量操作返回正确页签集合', (operation, expected) => {
    const result = resolveCloseOperation(tabs, '/b', '/b', operation)
    expect(result.tabs.map(item => item.fullPath)).toEqual(expected)
  })

  test('关闭全部时建议回到项目管理', () => {
    expect(resolveCloseOperation(tabs, '/b', '/b', 'all').nextPath).toBe('/project')
  })

  test('关闭操作不修改原始数组', () => {
    resolveCloseOperation(tabs, '/b', '/b', 'left')
    expect(tabs.map(item => item.fullPath)).toEqual(['/a', '/b', '/c'])
  })
})

describe('Layout — 页签快捷键', () => {
  const tabs = ['/a', '/b', '/c'].map(fullPath => ({ fullPath }))

  test('Ctrl+W 与 Ctrl+R 映射到当前页签操作', () => {
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'w' }, tabs, '/b')).toEqual({
      type: 'operate', operation: 'current', targetPath: '/b'
    })
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'r' }, tabs, '/b')).toEqual({
      type: 'operate', operation: 'refresh', targetPath: '/b'
    })
  })

  test('Ctrl+Tab 与 Ctrl+Shift+Tab 在首尾循环', () => {
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'Tab' }, tabs, '/c')).toEqual({
      type: 'activate', targetPath: '/a'
    })
    expect(resolveWorkspaceShortcut({ ctrlKey: true, shiftKey: true, key: 'Tab' }, tabs, '/a')).toEqual({
      type: 'activate', targetPath: '/c'
    })
  })

  test('Ctrl+左右键不循环且边界无操作', () => {
    expect(resolveTabSwitchPath(tabs, '/b', -1, false)).toBe('/a')
    expect(resolveTabSwitchPath(tabs, '/b', 1, false)).toBe('/c')
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'ArrowLeft' }, tabs, '/a')).toBeNull()
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'ArrowRight' }, tabs, '/c')).toBeNull()
  })

  test('左右切换不抢占输入控件、可编辑元素和代码编辑器', () => {
    const input = document.createElement('input')
    const editable = document.createElement('div')
    editable.setAttribute('contenteditable', 'true')
    const monaco = document.createElement('div')
    monaco.className = 'monaco-editor'
    const monacoChild = document.createElement('span')
    monaco.appendChild(monacoChild)

    expect(isEditableShortcutTarget(input)).toBe(true)
    expect(isEditableShortcutTarget(editable)).toBe(true)
    expect(isEditableShortcutTarget(monacoChild)).toBe(true)
    expect(resolveWorkspaceShortcut({ ctrlKey: true, key: 'ArrowRight', target: input }, tabs, '/a')).toBeNull()
  })

  test('无 Ctrl 或含 Alt、Meta 的组合不接管', () => {
    expect(resolveWorkspaceShortcut({ key: 'w' }, tabs, '/b')).toBeNull()
    expect(resolveWorkspaceShortcut({ ctrlKey: true, altKey: true, key: 'w' }, tabs, '/b')).toBeNull()
    expect(resolveWorkspaceShortcut({ ctrlKey: true, metaKey: true, key: 'w' }, tabs, '/b')).toBeNull()
  })
})

function createRoute(fullPath, title = '规则管理') {
  const path = fullPath.split('?')[0]
  const expressionMatch = path.match(/^\/designer\/expression\/([^/]+)\/([^/]+)$/)
  return {
    fullPath,
    path,
    name: expressionMatch ? 'ExpressionEditor' : (path === '/project' ? 'ProjectList' : 'RuleList'),
    params: expressionMatch ? { ruleId: expressionMatch[1], sessionId: expressionMatch[2] } : {},
    meta: { title },
    matched: [{ path: '' }, { path: path.slice(1) }]
  }
}

function mountLayout(route = createRoute('/rule')) {
  const store = createStore({ modules: { expressionSessions, workspaceTabs } })
  const router = {
    push: vi.fn().mockResolvedValue(undefined),
    replace: vi.fn().mockResolvedValue(undefined),
    resolve: vi.fn(fullPath => {
      const title = fullPath === '/project' ? '项目管理' : '规则管理'
      return createRoute(fullPath, title)
    })
  }
  const wrapper = shallowMount(Layout, {
    plugins: [store],
    mocks: { $route: route, $router: router },
    stubs: {
      'router-view': true,
      'keep-alive': { template: '<div><slot /></div>' }
    }
  })
  return { wrapper, store, router }
}

describe('Layout — 全局布局集成', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: false } })
    authApi.getConsoleMe.mockResolvedValue({ data: { username: 'admin' } })
    authApi.consoleLogout.mockResolvedValue({ data: true })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  test('首次进入布局路由时创建并激活工作区页签', async() => {
    const { wrapper, store } = mountLayout(createRoute('/rule', '规则管理'))
    await nextTick()

    expect(wrapper.findComponent(LayoutSidebar).exists()).toBe(true)
    expect(wrapper.findComponent(WorkspaceTabs).exists()).toBe(true)
    expect(store.getters['workspaceTabs/tabs']).toEqual([
      { fullPath: '/rule', path: '/rule', name: 'RuleList', title: '规则管理' }
    ])
    expect(store.getters['workspaceTabs/activePath']).toBe('/rule')
    wrapper.unmount()
  })

  test('设计器页面继续高亮规则管理', () => {
    const route = createRoute('/designer/table/8', '决策表设计器')
    const { wrapper } = mountLayout(route)

    expect(wrapper.vm.activeMenuIndex).toBe('/rule')
    wrapper.unmount()
  })

  test('表达式路由使用会话来源标题且会话缺失时回退静态标题', async() => {
    const route = createRoute('/designer/expression/7/session-1', '配置表达式')
    const { wrapper, store } = mountLayout(route)

    expect(wrapper.vm.routeTab(route).title).toBe('配置表达式')
    await store.dispatch('expressionSessions/openSession', {
      sessionId: 'session-1',
      ruleId: 7,
      sourceKey: 'picker-1',
      title: '决策表 · 右操作数',
      draft: null
    })

    expect(wrapper.vm.routeTab(route).title).toBe('决策表 · 右操作数')
    wrapper.unmount()
  })

  test('关闭全部页签后回到项目管理', async() => {
    const { wrapper, router } = mountLayout()
    await nextTick()

    await wrapper.vm.handleTabOperation({ operation: 'all', targetPath: '/rule' })

    expect(router.push).toHaveBeenCalledWith('/project')
    wrapper.unmount()
  })

  test('刷新页签只改变当前路由视图 key', async() => {
    const { wrapper } = mountLayout()
    await nextTick()
    expect(wrapper.vm.currentViewKey).toBe('/rule::0')

    await wrapper.vm.handleTabOperation({ operation: 'refresh', targetPath: '/rule' })

    expect(wrapper.vm.currentViewKey).toBe('/rule::1')
    wrapper.unmount()
  })

  test('全局快捷键阻止默认行为并刷新当前业务页签', async() => {
    const { wrapper } = mountLayout()
    await nextTick()
    const event = { ctrlKey: true, key: 'r', preventDefault: vi.fn() }

    await wrapper.vm.handleWorkspaceShortcut(event)

    expect(event.preventDefault).toHaveBeenCalled()
    expect(wrapper.vm.currentViewKey).toBe('/rule::1')
    wrapper.unmount()
  })

  test('布局销毁时清理全局快捷键监听', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const { wrapper } = mountLayout()

    wrapper.unmount()

    expect(removeSpy).toHaveBeenCalledWith('keydown', wrapper.vm.handleWorkspaceShortcut, true)
    removeSpy.mockRestore()
  })

  test('折叠后为 64px，再展开恢复上次宽度并写入会话缓存', async() => {
    const { wrapper } = mountLayout()
    wrapper.vm.sidebarWidth = 248
    wrapper.vm.lastExpandedWidth = 248

    wrapper.vm.toggleSidebar()
    expect(wrapper.vm.sidebarWidth).toBe(64)
    expect(wrapper.vm.isSidebarCompact).toBe(true)

    wrapper.vm.toggleSidebar()
    expect(wrapper.vm.sidebarWidth).toBe(248)
    expect(readSidebarState(window.sessionStorage)).toEqual({
      width: 248,
      lastExpandedWidth: 248
    })
    wrapper.unmount()
  })

  test('拖拽越过阈值自动切换为窄栏并保留上次展开宽度', () => {
    const { wrapper } = mountLayout()
    wrapper.vm.handleSidebarResize(240)
    wrapper.vm.handleSidebarResize(120)

    expect(wrapper.vm.sidebarWidth).toBe(120)
    expect(wrapper.vm.isSidebarCompact).toBe(true)
    expect(wrapper.vm.lastExpandedWidth).toBe(240)
    wrapper.unmount()
  })

  test('登录启用后展示用户名并生成头像首字母', async() => {
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: true } })
    authApi.getConsoleMe.mockResolvedValue({ data: { username: '张三' } })
    const { wrapper } = mountLayout()

    await wrapper.vm.refreshAuthBar()

    expect(wrapper.vm.loginEnabled).toBe(true)
    expect(wrapper.vm.username).toBe('张三')
    expect(wrapper.vm.avatarInitial).toBe('Z')
    wrapper.unmount()
  })

  test('退出接口失败仍跳转登录页', async() => {
    authApi.consoleLogout.mockRejectedValue(new Error('logout failed'))
    const { wrapper, router } = mountLayout()

    await expect(wrapper.vm.doLogout()).rejects.toThrow('logout failed')
    expect(router.replace).toHaveBeenCalledWith({ path: '/login' })
    wrapper.unmount()
  })

  test('快速连续导航产生的 Vue Router 取消不会成为未处理异常', async() => {
    const { wrapper, router } = mountLayout(createRoute('/project', '项目管理'))
    const cancelled = Object.assign(new Error('Navigation cancelled'), {
      _isRouter: true,
      type: 8
    })
    router.push.mockResolvedValue(cancelled)

    await expect(wrapper.vm.navigateTo('/rule')).resolves.toBe(cancelled)
    wrapper.unmount()
  })

  test('非路由导航异常继续向调用方暴露', async() => {
    const { wrapper, router } = mountLayout(createRoute('/project', '项目管理'))
    router.push.mockRejectedValue(new Error('unexpected'))

    await expect(wrapper.vm.navigateTo('/rule')).rejects.toThrow('unexpected')
    wrapper.unmount()
  })
})
