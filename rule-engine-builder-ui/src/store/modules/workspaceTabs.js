import { resolveCloseOperation } from '@/layout/layoutState'

export const WORKSPACE_TABS_STORAGE_KEY = 'tianshu:layout:workspace-tabs'

function emptyCache() {
  return { tabs: [], activePath: '' }
}

function sessionStorageRef() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null
  } catch (e) {
    return null
  }
}

function workspaceTabKey(tab) {
  const path = tab && typeof tab.path === 'string' ? tab.path : ''
  if (/^\/designer\/(?!expression\/)[^/]+\/[^/]+$/.test(path)) return path
  return tab && tab.fullPath
}

function workspaceViewKey(fullPath) {
  const value = String(fullPath || '')
  const path = value.split('?')[0]
  return /^\/designer\/(?!expression\/)[^/]+\/[^/]+$/.test(path)
    ? path
    : value
}

function uniqueTabs(tabs) {
  const result = []
  const indexes = new Map()
  ;(Array.isArray(tabs) ? tabs : []).forEach(tab => {
    if (!tab || typeof tab.fullPath !== 'string' || !tab.fullPath) return
    const key = workspaceTabKey(tab)
    if (indexes.has(key)) {
      result[indexes.get(key)] = { ...result[indexes.get(key)], ...tab }
      return
    }
    indexes.set(key, result.length)
    result.push(tab)
  })
  return result
}

export function readWorkspaceTabs(storage = sessionStorageRef()) {
  if (!storage || typeof storage.getItem !== 'function') return emptyCache()
  try {
    const raw = storage.getItem(WORKSPACE_TABS_STORAGE_KEY)
    if (!raw) return emptyCache()
    const saved = JSON.parse(raw)
    if (!saved || !Array.isArray(saved.tabs)) return emptyCache()
    const tabs = uniqueTabs(saved.tabs)
    const activePath = typeof saved.activePath === 'string' ? saved.activePath : ''
    return { tabs, activePath }
  } catch (e) {
    return emptyCache()
  }
}

export function writeWorkspaceTabs(state, storage = sessionStorageRef()) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(WORKSPACE_TABS_STORAGE_KEY, JSON.stringify({
      tabs: state.tabs,
      activePath: state.activePath
    }))
  } catch (e) {
    // 浏览器禁用存储时，页签仍在当前页面内正常工作。
  }
}

const state = () => ({
  tabs: [],
  activePath: '',
  refreshVersions: {}
})

const getters = {
  tabs: state => state.tabs,
  activePath: state => state.activePath,
  viewKey: state => fullPath =>
    `${workspaceViewKey(fullPath)}::${state.refreshVersions[fullPath] || 0}`
}

const mutations = {
  RESTORE(state, payload) {
    const currentTab = payload.currentTab
    const tabs = uniqueTabs([...(payload.cachedTabs || []), currentTab])
    state.tabs = tabs
    state.activePath = currentTab.fullPath
    state.refreshVersions = {}
  },
  OPEN(state, tab) {
    const tabKey = workspaceTabKey(tab)
    const index = state.tabs.findIndex(item => workspaceTabKey(item) === tabKey)
    if (index < 0) {
      state.tabs = [...state.tabs, tab]
    } else {
      state.tabs = state.tabs.map((item, itemIndex) => itemIndex === index ? { ...item, ...tab } : item)
    }
    state.activePath = tab.fullPath
  },
  ACTIVATE(state, fullPath) {
    if (state.tabs.some(tab => tab.fullPath === fullPath)) state.activePath = fullPath
  },
  UPDATE_DETAIL_TITLE(state, payload) {
    const key = workspaceTabKey(payload)
    state.tabs = state.tabs.map(tab => workspaceTabKey(tab) === key
      ? { ...tab, detailTitle: payload.detailTitle }
      : tab)
  },
  REPLACE(state, payload) {
    state.tabs = payload.tabs
    state.activePath = payload.nextPath
    const retained = {}
    payload.tabs.forEach(tab => {
      if (state.refreshVersions[tab.fullPath]) {
        retained[tab.fullPath] = state.refreshVersions[tab.fullPath]
      }
    })
    state.refreshVersions = retained
  },
  REFRESH(state, fullPath) {
    if (!state.tabs.some(tab => tab.fullPath === fullPath)) return
    state.refreshVersions = {
      ...state.refreshVersions,
      [fullPath]: (state.refreshVersions[fullPath] || 0) + 1
    }
  }
}

function persist(state) {
  writeWorkspaceTabs(state)
}

const actions = {
  restore({ commit, state }, payload) {
    commit('RESTORE', payload)
    persist(state)
  },
  open({ commit, state }, tab) {
    commit('OPEN', tab)
    persist(state)
  },
  activate({ commit, state }, fullPath) {
    commit('ACTIVATE', fullPath)
    persist(state)
  },
  updateDetailTitle({ commit, state }, payload) {
    commit('UPDATE_DETAIL_TITLE', payload)
    persist(state)
  },
  close({ commit, state }, payload) {
    const result = resolveCloseOperation(
      state.tabs,
      state.activePath,
      payload.targetPath,
      payload.operation
    )
    commit('REPLACE', result)
    persist(state)
    return result
  },
  refresh({ commit, state }, fullPath) {
    commit('REFRESH', fullPath)
    persist(state)
  }
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions
}
