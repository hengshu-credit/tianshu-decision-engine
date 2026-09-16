vi.unmock('@/layout/index.vue')

vi.mock('@/api/auth', () => ({
  getConsoleAuthConfig: vi.fn(),
  getConsoleMe: vi.fn(),
  consoleLogout: vi.fn(),
  getConsoleThemePreference: vi.fn(),
  saveConsoleThemePreference: vi.fn()
}))

import { nextTick } from 'vue'
import { createStore } from 'vuex'
import { flushPromises, shallowMount } from '@test-utils'
import * as authApi from '@/api/auth'
import * as projectApi from '@/api/project'
import Layout from '@/layout/index.vue'

test('显式项目路由不因项目元信息晚到而重建设计或表单页面', () => {
  const context = {
    $route: { path: '/rule', fullPath: '/rule?projectId=1', query: { projectId: '1' } },
    $store: { getters: {}, state: { currentProject: null } },
  }
  const before = Layout.computed.currentViewKey.call(context)
  context.$store.state.currentProject = { id: 1 }
  expect(Layout.computed.currentViewKey.call(context)).toBe(before)
})
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import expressionSessions from '@/store/modules/expressionSessions'
import workspaceTabs from '@/store/modules/workspaceTabs'
import { DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'
import { readLocalTheme, writeLocalTheme } from '@/theme/themeRuntime'
import {
  clearDesignerLeaveGuards,
  registerDesignerLeaveGuard,
} from '@/utils/designerLeaveGuard'
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
  test('包含 16 个路由唯一、图标唯一的平铺一级菜单项', () => {
    expect(SIDEBAR_MENUS).toHaveLength(16)
    expect(new Set(SIDEBAR_MENUS.map(item => item.index)).size).toBe(16)
    expect(new Set(SIDEBAR_MENUS.map(item => item.icon)).size).toBe(16)
    expect(SIDEBAR_MENUS[0].permission).toBe('')
  })

  test('菜单使用确认后的语义图标映射', () => {
    expect(SIDEBAR_MENUS).toEqual([
      { index: '/dashboard', label: '数据看板', icon: 'DataAnalysis', permission: '' },
      { index: '/project', label: '项目管理', icon: 'FolderOpened', permission: 'project:view' },
      { index: '/rule', label: '规则管理', icon: 'Operation', permission: 'rule:view' },
      { index: '/variable', label: '变量管理', icon: 'Collection', permission: 'field:view' },
      { index: '/list', label: '名单管理', icon: 'Notebook', permission: 'field:view' },
      { index: '/datasource', label: '外数管理', icon: 'Connection', permission: 'datasource:view' },
      { index: '/database', label: '数据库管理', icon: 'Coin', permission: 'database:view' },
      { index: '/model', label: '模型管理', icon: 'Cpu', permission: 'model:view' },
      { index: '/function', label: '函数管理', icon: 'ScaleToOriginal', permission: 'function:view' },
      { index: '/test', label: '规则测试', icon: 'VideoPlay', permission: 'rule:view' },
      { index: '/lineage', label: '血缘分析', icon: 'Share', permission: 'approval:view' },
      { index: '/experiment', label: '分流实验', icon: 'Flag', permission: 'experiment:view' },
      { index: '/log', label: '执行日志', icon: 'DocumentChecked', permission: 'rule:view' },
      { index: '/billing', label: '账单管理', icon: 'Wallet', permission: 'project:view' },
      { index: '/approval', label: '审批管理', icon: 'Stamp', permission: 'approval:view' },
      { index: '/account', label: '账户管理', icon: 'User', permission: 'account:view' }
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
    ['/billing', '/billing'],
    ['/approval/1', '/approval'],
    ['/account', '/account']
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

  test('关闭全部时建议回到数据看板', () => {
    expect(resolveCloseOperation(tabs, '/b', '/b', 'all').nextPath).toBe('/dashboard')
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
  const [path, queryText = ''] = fullPath.split('?')
  const query = Object.fromEntries(new URLSearchParams(queryText).entries())
  const expressionMatch = path.match(/^\/designer\/expression\/([^/]+)\/([^/]+)$/)
  return {
    fullPath,
    path,
    query,
    name: expressionMatch ? 'ExpressionEditor' : (path === '/project' ? 'ProjectList' : 'RuleList'),
    params: expressionMatch ? { ruleId: expressionMatch[1], sessionId: expressionMatch[2] } : {},
    meta: { title },
    matched: [{ path: '' }, { path: path.slice(1) }]
  }
}

function mountLayout(route = createRoute('/rule')) {
  const store = createStore({
    state: { currentProject: null, projectContextGeneration: 0 },
    mutations: {
      INVALIDATE_PROJECT_CONTEXT_REQUESTS(state) {
        state.projectContextGeneration += 1
      },
      SET_CURRENT_PROJECT(state, project) {
        state.projectContextGeneration += 1
        state.currentProject = project
      }
    },
    modules: { expressionSessions, workspaceTabs }
  })
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
      'el-dropdown': {
        name: 'ElDropdown',
        emits: ['command'],
        template: '<div class="dropdown-stub"><slot /><slot name="dropdown" /></div>'
      },
      'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': {
        props: ['command', 'disabled', 'divided'],
        template: '<button :data-account-command="command" :disabled="disabled"><slot /></button>'
      },
      'router-view': true,
      'keep-alive': { template: '<div><slot /></div>' }
    }
  })
  return { wrapper, store, router }
}

describe('Layout — 全局布局集成', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-sidebar-theme')
    document.documentElement.className = ''
    document.documentElement.removeAttribute('style')
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: false } })
    authApi.getConsoleMe.mockResolvedValue({ data: { username: 'admin' } })
    authApi.consoleLogout.mockResolvedValue({ data: true })
    authApi.getConsoleThemePreference.mockResolvedValue({
      data: { ...DEFAULT_THEME_CONFIG }
    })
    authApi.saveConsoleThemePreference.mockImplementation(config =>
      Promise.resolve({ data: config })
    )
  })

  afterEach(() => {
    vi.clearAllMocks()
    clearDesignerLeaveGuards()
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

    expect(wrapper.vm.routeTab(route)).toMatchObject({
      title: '配置表达式', detailTitle: '决策表 · 右操作数'
    })
    wrapper.unmount()
  })

  test('关闭全部页签后回到数据看板', async() => {
    const { wrapper, router } = mountLayout()
    await nextTick()

    await wrapper.vm.handleTabOperation({ operation: 'all', targetPath: '/rule' })

    expect(router.push).toHaveBeenCalledWith('/dashboard')
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

  test('项目上下文变化会重建支持项目范围的业务页面', async() => {
    const { wrapper, store } = mountLayout(createRoute('/experiment', '分流实验'))
    await nextTick()
    expect(wrapper.vm.currentViewKey).toBe('/experiment::0')

    store.commit('SET_CURRENT_PROJECT', { id: 3, projectName: '风险项目' })
    await nextTick()
    expect(wrapper.vm.currentViewKey).toBe('/experiment::0::project:3')

    store.commit('SET_CURRENT_PROJECT', null)
    await nextTick()
    expect(wrapper.vm.currentViewKey).toBe('/experiment::0')
    wrapper.unmount()
  })

  test('退出项目范围会使尚未完成的项目加载请求失效', async() => {
    let resolveProject
    projectApi.getProject.mockImplementationOnce(
      () => new Promise((resolve) => { resolveProject = resolve })
    )
    const { wrapper, store } = mountLayout(
      createRoute('/rule?projectId=9', '规则管理')
    )
    const loading = wrapper.vm.syncProjectContext()
    store.commit('SET_CURRENT_PROJECT', null)
    resolveProject({ data: { id: 9, projectCode: 'STALE' } })
    await loading

    expect(store.state.currentProject).toBeNull()
    wrapper.unmount()
  })

  test('缓存中的同 ID 项目仍会从后端重新校验并刷新展示信息', async() => {
    projectApi.getProject.mockResolvedValueOnce({
      data: { id: 9, projectCode: 'RISK', projectName: '新名称', status: 1 }
    })
    const { wrapper, store } = mountLayout(
      createRoute('/rule?projectId=9', '规则管理')
    )
    store.commit('SET_CURRENT_PROJECT', {
      id: 9,
      projectCode: 'RISK',
      projectName: '旧名称'
    })

    await wrapper.vm.syncProjectContext()

    expect(projectApi.getProject).toHaveBeenCalledWith(9)
    expect(store.state.currentProject.projectName).toBe('新名称')
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
    expect(wrapper.find('.layout-account-name').exists()).toBe(true)
    expect(wrapper.find('.layout-account-name').text()).toBe('张三')
    expect(wrapper.find('.layout-account-avatar').text()).toBe('Z')
    expect(wrapper.find('[data-account-command="settings"]').exists()).toBe(true)
    expect(wrapper.find('[data-account-command="theme"]').exists()).toBe(true)
    expect(wrapper.find('[data-account-command="logout"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('刷新布局恢复非活动详情页时保留具体标题', async() => {
    window.sessionStorage.setItem('tianshu:layout:workspace-tabs', JSON.stringify({
      tabs: [{ ...createRoute('/project/1', '项目详情'), detailTitle: '企业授信' }],
      activePath: '/project/1'
    }))
    const { wrapper, store } = mountLayout()
    await nextTick()
    expect(store.state.workspaceTabs.tabs.find(tab => tab.path === '/project/1').detailTitle).toBe('企业授信')
    wrapper.unmount()
  })

  test('工作区关闭操作在脏设计器拒绝离开时不先删除页签', async() => {
    const { wrapper, router, store } = mountLayout()
    await nextTick()
    const confirmLeave = vi.fn().mockResolvedValue(false)
    registerDesignerLeaveGuard('/rule', confirmLeave)

    const result = await wrapper.vm.handleTabOperation({
      operation: 'all',
      targetPath: '/rule'
    })

    expect(result).toEqual({ cancelled: true })
    expect(confirmLeave).toHaveBeenCalledTimes(1)
    expect(store.getters['workspaceTabs/tabs']).toHaveLength(1)
    expect(router.push).not.toHaveBeenCalledWith('/dashboard')
    wrapper.unmount()
  })

  test('一级菜单导航在脏设计器拒绝离开时不跳转', async() => {
    const { wrapper, router } = mountLayout()
    await nextTick()
    const confirmLeave = vi.fn().mockResolvedValue(false)
    registerDesignerLeaveGuard('/rule', confirmLeave)

    const result = await wrapper.vm.navigateTo('/variable')

    expect(result).toEqual({ cancelled: true })
    expect(confirmLeave).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('工作区页签切换在脏设计器拒绝离开时不提前激活目标页签', async() => {
    const { wrapper, router, store } = mountLayout()
    await nextTick()
    const dispatchSpy = vi.spyOn(store, 'dispatch')
    const confirmLeave = vi.fn().mockResolvedValue(false)
    registerDesignerLeaveGuard('/rule', confirmLeave)

    const result = await wrapper.vm.activateTab('/variable')

    expect(result).toEqual({ cancelled: true })
    expect(confirmLeave).toHaveBeenCalledTimes(1)
    expect(dispatchSpy).not.toHaveBeenCalledWith(
      'workspaceTabs/activate',
      '/variable'
    )
    expect(router.push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('顶部菜单保持全部一级入口平铺且账户区仍位于右上角', async() => {
    writeLocalTheme(window.localStorage, {
      ...DEFAULT_THEME_CONFIG,
      navigationLayout: 'TOP'
    })
    const { wrapper } = mountLayout()
    await nextTick()

    expect(wrapper.findComponent(LayoutSidebar).exists()).toBe(false)
    expect(wrapper.find('.layout-primary-topbar').exists()).toBe(true)
    expect(wrapper.findAll('.top-navigation__item')).toHaveLength(16)
    expect(wrapper.find('.top-navigation__more').exists()).toBe(false)
    expect(wrapper.find('.layout-account').exists()).toBe(true)
    expect(wrapper.findComponent(WorkspaceTabs).exists()).toBe(true)
    wrapper.unmount()
  })

  test('关闭登录时显示本地用户并保留主题设置和退出账号', async() => {
    const { wrapper } = mountLayout()

    await wrapper.vm.refreshAuthBar()
    await nextTick()

    expect(wrapper.find('.layout-account-name').exists()).toBe(true)
    expect(wrapper.find('.layout-account-name').text()).toBe('本地用户')
    expect(wrapper.find('[data-account-command="settings"]').exists()).toBe(false)
    expect(wrapper.find('[data-account-command="theme"]').exists()).toBe(true)
    expect(wrapper.find('[data-account-command="logout"]').exists()).toBe(true)
    expect(wrapper.find('.theme-settings-trigger').exists()).toBe(false)
    wrapper.unmount()
  })

  test('账户菜单的用户设置进入现有账户管理页面', async() => {
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: true } })
    authApi.getConsoleMe.mockResolvedValue({ data: { username: 'admin' } })
    const { wrapper, router } = mountLayout()
    await wrapper.vm.refreshAuthBar()

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    expect(dropdown.exists()).toBe(true)
    dropdown.vm.$emit('command', 'settings')
    await nextTick()

    expect(router.push).toHaveBeenCalledWith('/account')
    wrapper.unmount()
  })

  test('账户菜单的主题设置打开统一主题侧边栏', async() => {
    const { wrapper } = mountLayout()

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    expect(dropdown.exists()).toBe(true)
    dropdown.vm.$emit('command', 'theme')
    await nextTick()

    expect(wrapper.vm.themeDrawerOpen).toBe(true)
    wrapper.unmount()
  })

  test('账户菜单的退出账号执行原有退出流程', async() => {
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: true } })
    authApi.getConsoleMe.mockResolvedValue({ data: { username: 'admin' } })
    const { wrapper, router } = mountLayout()
    await wrapper.vm.refreshAuthBar()

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    expect(dropdown.exists()).toBe(true)
    dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(authApi.consoleLogout).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith({ path: '/login' })
    wrapper.unmount()
  })

  test('本地用户也可从账户菜单执行退出流程', async() => {
    const { wrapper, store, router } = mountLayout()
    await wrapper.vm.refreshAuthBar()
    store.commit('SET_CURRENT_PROJECT', { id: 1, projectName: '测试项目' })

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    expect(dropdown.exists()).toBe(true)
    dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(authApi.consoleLogout).toHaveBeenCalledTimes(1)
    expect(store.state.currentProject).toBeNull()
    expect(router.replace).toHaveBeenCalledWith({ path: '/login' })
    wrapper.unmount()
  })

  test('登录用户使用服务端主题并覆盖匿名浏览器配置', async() => {
    writeLocalTheme(window.localStorage, {
      ...DEFAULT_THEME_CONFIG,
      accentPreset: 'PEACH_PINK'
    })
    const serverTheme = {
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      accentPreset: 'NEBULA_GRADIENT'
    }
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: true } })
    authApi.getConsoleMe.mockResolvedValue({
      data: { userId: 9, username: 'alice', permissions: [] }
    })
    authApi.getConsoleThemePreference.mockResolvedValue({ data: serverTheme })
    const { wrapper } = mountLayout()

    await wrapper.vm.refreshAuthBar()

    expect(wrapper.vm.confirmedTheme).toEqual(serverTheme)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.style.getPropertyValue('--tianshu-brand-gradient'))
      .toContain('#E14ECA')
    wrapper.unmount()
  })

  test('关闭登录时使用浏览器保存的主题且不请求用户主题接口', async() => {
    const localTheme = { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' }
    writeLocalTheme(window.localStorage, localTheme)
    authApi.getConsoleAuthConfig.mockResolvedValue({ data: { loginEnabled: false } })
    const { wrapper } = mountLayout()

    await wrapper.vm.refreshAuthBar()

    expect(wrapper.vm.confirmedTheme).toEqual(localTheme)
    expect(authApi.getConsoleThemePreference).not.toHaveBeenCalled()
    expect(document.documentElement.dataset.theme).toBe('dark')
    wrapper.unmount()
  })

  test('抽屉预览立即应用主题和菜单位置且取消恢复已保存快照', async() => {
    const { wrapper } = mountLayout()
    await flushPromises()
    const preview = {
      ...DEFAULT_THEME_CONFIG,
      colorScheme: 'DARK',
      navigationLayout: 'TOP'
    }

    wrapper.vm.handleThemePreview(preview)
    await nextTick()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(wrapper.vm.activeTheme.navigationLayout).toBe('TOP')
    expect(wrapper.find('.layout-primary-topbar').exists()).toBe(true)

    wrapper.vm.cancelThemePreview({ ...DEFAULT_THEME_CONFIG })
    await nextTick()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(wrapper.vm.confirmedTheme).toEqual(DEFAULT_THEME_CONFIG)
    expect(wrapper.vm.activeTheme).toEqual(DEFAULT_THEME_CONFIG)
    expect(wrapper.findComponent(LayoutSidebar).exists()).toBe(true)
    wrapper.unmount()
  })

  test('登录用户保存成功后更新权威配置并镜像到浏览器', async() => {
    const { wrapper } = mountLayout()
    const darkTheme = { ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' }
    wrapper.vm.loginEnabled = true
    wrapper.vm.username = 'alice'
    wrapper.vm.themeDrawerOpen = true

    await wrapper.vm.saveTheme(darkTheme)

    expect(authApi.saveConsoleThemePreference).toHaveBeenCalledWith(darkTheme)
    expect(wrapper.vm.confirmedTheme).toEqual(darkTheme)
    expect(readLocalTheme(window.localStorage)).toEqual(darkTheme)
    expect(wrapper.vm.themeDrawerOpen).toBe(false)
    wrapper.unmount()
  })

  test('主题保存失败保留抽屉和已保存配置', async() => {
    authApi.saveConsoleThemePreference.mockRejectedValueOnce(
      new Error('保存主题失败')
    )
    const { wrapper } = mountLayout()
    wrapper.vm.loginEnabled = true
    wrapper.vm.username = 'alice'
    wrapper.vm.themeDrawerOpen = true

    await wrapper.vm.saveTheme({ ...DEFAULT_THEME_CONFIG, colorScheme: 'DARK' })

    expect(wrapper.vm.confirmedTheme).toEqual(DEFAULT_THEME_CONFIG)
    expect(wrapper.vm.themeDrawerOpen).toBe(true)
    expect(wrapper.vm.themeSaving).toBe(false)
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith('保存主题失败')
    wrapper.unmount()
  })

  test('退出接口失败仍跳转登录页', async() => {
    authApi.consoleLogout.mockRejectedValue(new Error('logout failed'))
    const { wrapper, router, store } = mountLayout()
    store.commit('SET_CURRENT_PROJECT', { id: 9, projectCode: 'RISK' })

    await expect(wrapper.vm.doLogout()).rejects.toThrow('logout failed')
    expect(store.state.currentProject).toBeNull()
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
