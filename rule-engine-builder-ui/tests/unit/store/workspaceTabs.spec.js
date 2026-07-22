import { createStore } from 'vuex'
import workspaceTabs, {
  WORKSPACE_TABS_STORAGE_KEY,
  readWorkspaceTabs
} from '@/store/modules/workspaceTabs'

function createTestStore() {
  return createStore({ modules: { workspaceTabs } })
}

function tab(fullPath, title) {
  return {
    fullPath,
    path: fullPath.split('?')[0],
    name: title || fullPath,
    title: title || fullPath
  }
}

beforeEach(() => {
  window.sessionStorage.clear()
})

describe('workspaceTabs store', () => {
  test('打开相同 fullPath 只激活而不重复', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/open', tab('/rule', '规则管理'))
    await store.dispatch('workspaceTabs/open', tab('/rule', '规则管理'))

    expect(store.getters['workspaceTabs/tabs']).toHaveLength(1)
    expect(store.getters['workspaceTabs/activePath']).toBe('/rule')
  })

  test('同一路径的不同查询参数保留为不同页签', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/open', tab('/rule?page=1'))
    await store.dispatch('workspaceTabs/open', tab('/rule?page=2'))

    expect(store.getters['workspaceTabs/tabs'].map(item => item.fullPath)).toEqual([
      '/rule?page=1',
      '/rule?page=2'
    ])
  })

  test('打开和关闭后把页签与活动路径写入 sessionStorage', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/open', tab('/project', '项目管理'))
    await store.dispatch('workspaceTabs/open', tab('/rule', '规则管理'))
    await store.dispatch('workspaceTabs/close', {
      operation: 'current',
      targetPath: '/rule'
    })

    expect(JSON.parse(window.sessionStorage.getItem(WORKSPACE_TABS_STORAGE_KEY))).toEqual({
      tabs: [tab('/project', '项目管理')],
      activePath: '/project'
    })
  })

  test('恢复有效缓存并始终加入当前浏览器路由', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/restore', {
      cachedTabs: [tab('/project', '项目管理'), tab('/rule', '规则管理')],
      currentTab: tab('/variable', '变量管理')
    })

    expect(store.getters['workspaceTabs/tabs'].map(item => item.fullPath)).toEqual([
      '/project',
      '/rule',
      '/variable'
    ])
    expect(store.getters['workspaceTabs/activePath']).toBe('/variable')
  })

  test('恢复时去除重复和缺少 fullPath 的缓存项', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/restore', {
      cachedTabs: [tab('/rule'), tab('/rule'), { title: '无效' }],
      currentTab: tab('/rule')
    })

    expect(store.getters['workspaceTabs/tabs']).toEqual([tab('/rule')])
  })

  test.each([
    ['current', '/b', ['/a', '/c'], '/c'],
    ['left', '/b', ['/b', '/c'], '/b'],
    ['right', '/b', ['/a', '/b'], '/b'],
    ['others', '/b', ['/b'], '/b'],
    ['all', '/b', [], '/project']
  ])('%s 操作更新页签并返回下一路径', async(operation, targetPath, expectedTabs, nextPath) => {
    const store = createTestStore()
    for (const path of ['/a', '/b', '/c']) {
      await store.dispatch('workspaceTabs/open', tab(path))
    }
    await store.dispatch('workspaceTabs/activate', '/b')

    const result = await store.dispatch('workspaceTabs/close', { operation, targetPath })

    expect(store.getters['workspaceTabs/tabs'].map(item => item.fullPath)).toEqual(expectedTabs)
    expect(store.getters['workspaceTabs/activePath']).toBe(nextPath)
    expect(result.nextPath).toBe(nextPath)
  })

  test('刷新只增加目标页签的视图版本', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/open', tab('/rule'))
    await store.dispatch('workspaceTabs/open', tab('/project'))
    await store.dispatch('workspaceTabs/refresh', '/rule')

    expect(store.getters['workspaceTabs/viewKey']('/rule')).toBe('/rule::1')
    expect(store.getters['workspaceTabs/viewKey']('/project')).toBe('/project::0')
  })

  test('刷新版本不写入会话缓存', async() => {
    const store = createTestStore()
    await store.dispatch('workspaceTabs/open', tab('/rule'))
    await store.dispatch('workspaceTabs/refresh', '/rule')

    const cached = JSON.parse(window.sessionStorage.getItem(WORKSPACE_TABS_STORAGE_KEY))
    expect(cached).not.toHaveProperty('refreshVersions')
  })

  test.each([null, '', '{bad', '{"tabs":"bad"}'])(
    '读取损坏缓存 %s 时返回空状态',
    value => {
      const storage = { getItem: vi.fn(() => value) }
      expect(readWorkspaceTabs(storage)).toEqual({ tabs: [], activePath: '' })
    }
  )

  test('存储不可用时页签行为仍正常', async() => {
    const original = window.sessionStorage.setItem
    window.sessionStorage.setItem = vi.fn(() => { throw new Error('blocked') })
    const store = createTestStore()

    await expect(store.dispatch('workspaceTabs/open', tab('/rule'))).resolves.toBeUndefined()
    expect(store.getters['workspaceTabs/activePath']).toBe('/rule')
    window.sessionStorage.setItem = original
  })
})
