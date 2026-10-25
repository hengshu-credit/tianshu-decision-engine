<template>
  <div
    class="layout-shell"
    :class="{ 'is-top-navigation': isTopNavigation }"
  >
    <layout-sidebar
      v-if="!isTopNavigation"
      :width="sidebarWidth"
      :compact="isSidebarCompact"
      :active-menu="activeMenuIndex"
      :menus="sidebarMenus"
      @navigate="navigateTo"
      @preload="preloadPage"
      @toggle-collapse="toggleSidebar"
      @resize="handleSidebarResize"
      @resize-end="handleSidebarResizeEnd"
    />
    <section class="layout-workspace">
      <header v-if="isTopNavigation" class="layout-primary-topbar">
        <div class="top-navigation__brand">
          <img
            :src="brand.logoUrl"
            :alt="brand.systemName"
            class="top-navigation__logo"
          />
          <strong>{{ brand.systemName }}</strong>
        </div>
        <nav class="top-navigation__menu" aria-label="主导航">
          <button
            v-for="menu in sidebarMenus"
            :key="menu.index"
            type="button"
            class="top-navigation__item"
            :class="{ 'is-active': activeMenuIndex === menu.index }"
            :data-menu-path="menu.index"
            :aria-label="menu.label"
            @click="navigateTo(menu.index)"
            @mouseenter="preloadPage(menu.index)"
            @focus="preloadPage(menu.index)"
          >
            <app-icon :name="menu.icon" />
            <span>{{ menu.label }}</span>
          </button>
        </nav>
      </header>
      <header class="layout-topbar">
        <workspace-tabs
          :tabs="workspaceTabs"
          :active-path="activeTabPath"
          :pending-path="pendingRoute ? pendingRoute.fullPath : ''"
          @activate="activateTab"
          @preload="preloadPage"
          @operate="handleTabOperation"
        />
      </header>
      <div class="layout-account">
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            popper-class="layout-account-dropdown layout-popup-menu"
            @command="handleAccountCommand"
          >
            <button
              type="button"
              class="layout-account-trigger"
              :aria-label="accountDisplayName + '的账户菜单'"
              :title="accountDisplayName"
            >
              <span class="layout-account-avatar" aria-hidden="true">{{
                avatarInitial
              }}</span>
              <span class="layout-account-name">{{ accountDisplayName }}</span>
              <app-icon
                name="ArrowDown"
                class="layout-account-arrow"
                aria-hidden="true"
              />
            </button>
            <template v-slot:dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-if="loginEnabled"
                  command="settings"
                  data-account-command="settings"
                >
                  <app-icon name="User" />
                  <span>用户设置</span>
                </el-dropdown-item>
                <el-dropdown-item
                  command="theme"
                  data-account-command="theme"
                >
                  <app-icon name="Setting" />
                  <span>主题设置</span>
                </el-dropdown-item>
                <el-dropdown-item
                  command="logout"
                  data-account-command="logout"
                  divided
                >
                  <app-icon name="SwitchButton" />
                  <span>退出账号</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      <project-context-bar />
      <main class="layout-main" :aria-busy="!!navigation.pendingRoute">
        <el-alert v-if="navigation.error && !navigation.pendingRoute" type="error" title="页面加载失败，请重试" show-icon @close="navigation.error = null">
          <el-button size="small" @click="navigateTo(navigation.error.fullPath)">重新加载</el-button>
        </el-alert>
        <router-view v-slot="{ Component }">
          <keep-alive :max="12">
            <component
              :is="Component"
              v-if="$route.meta.keepAlive"
              :key="currentViewKey"
            />
          </keep-alive>
          <component
            :is="Component"
            v-if="!$route.meta.keepAlive"
            :key="currentViewKey"
          />
        </router-view>
      </main>
      <footer class="layout-footer">
        <span class="layout-footer-copyright">
          © 2026
          <a href="https://hengshucredit.com/" target="_blank" rel="noopener noreferrer">上海衡枢真信科技有限公司</a>
          · ALL RIGHTS RESERVED
        </span>
        <span>
          开源地址：
          <a href="https://github.com/hengshu-credit" target="_blank" rel="noopener noreferrer">github.com/hengshu-credit</a>
        </span>
        <a href="https://github.com/itlubber" target="_blank" rel="noopener noreferrer">github.com/itlubber</a>
        <el-popover placement="top" :width="244" :trigger="['hover', 'focus']" :show-after="150" :persistent="false" title="微信：itlubber">
          <template #reference>
            <button type="button" class="layout-footer-contact">微信：itlubber</button>
          </template>
          <img class="layout-footer-qr-image" src="https://hengshucredit.com/images/itlubber.svg" alt="微信 itlubber 二维码" width="220" height="220" />
        </el-popover>
        <el-popover placement="top" :width="244" :trigger="['hover', 'focus']" :show-after="150" :persistent="false" title="公众号：itlubber_art">
          <template #reference>
            <button type="button" class="layout-footer-contact">公众号：itlubber_art</button>
          </template>
          <img class="layout-footer-qr-image" src="https://hengshucredit.com/images/itlubber_art.svg" alt="公众号 itlubber_art 二维码" width="220" height="220" />
        </el-popover>
      </footer>
    </section>
    <theme-settings-drawer
      v-model="themeDrawerOpen"
      :config="confirmedTheme"
      :saving="themeSaving"
      @preview="handleThemePreview"
      @save="saveTheme"
      @cancel="cancelThemePreview"
    />
    <designer-input-tooltip :enabled="$route.path.startsWith('/designer/')" />
  </div>
</template>

<script>
import { isNavigationFailure } from 'vue-router'
import { isHandledNavigationError, navigationState, preloadMenuRoutes, preloadRoute } from '@/router/navigationState'
import { systemBrand } from '@/system/systemBrand'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import ProjectContextBar from '@/components/ProjectContextBar.vue'
import ThemeSettingsDrawer from '@/components/theme/ThemeSettingsDrawer.vue'
import DesignerInputTooltip from '@/components/common/DesignerInputTooltip.vue'
import {
  SIDEBAR_COMPACT_THRESHOLD,
  SIDEBAR_MENUS,
  SIDEBAR_MIN_WIDTH,
  clampSidebarWidth,
  getActiveMenuIndex,
  getAvatarInitial,
  isWorkspaceRoute,
  readSidebarState,
  resolveCloseOperation,
  resolveWorkspaceShortcut,
  routeToTab,
  writeSidebarState,
} from '@/layout/layoutState'
import { confirmDesignerPathsCanClose } from '@/utils/designerLeaveGuard'
import { readWorkspaceTabs } from '@/store/modules/workspaceTabs'
import {
  getConsoleAuthConfig,
  consoleLogout,
  getConsoleMe,
  getConsoleThemePreference,
  saveConsoleThemePreference,
} from '@/api/auth'
import { getProject } from '@/api/project'
import {
  normalizeProjectId,
  routeSupportsProjectContext,
  withProjectContext,
} from '@/utils/projectContext'
import {
  clearCurrentUser,
  filterMenus,
  getCurrentUser,
  setCurrentUser,
} from '@/security/permissionState'
import {
  DEFAULT_THEME_CONFIG,
  normalizeThemeConfig,
} from '@/theme/themeConfig'
import {
  applyTheme,
  readLocalTheme,
  writeLocalTheme,
} from '@/theme/themeRuntime'

function browserSessionStorage() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null
  } catch (e) {
    return null
  }
}

function browserLocalStorage() {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch (e) {
    return null
  }
}

export default {
  name: 'Layout',
  components: {
    LayoutSidebar,
    WorkspaceTabs,
    ProjectContextBar,
    ThemeSettingsDrawer,
    DesignerInputTooltip,
  },
  data() {
    const sidebarState = readSidebarState(browserSessionStorage())
    const initialTheme = readLocalTheme(browserLocalStorage())
    return {
      navigation: navigationState,
      sidebarWidth: sidebarState.width,
      lastExpandedWidth: sidebarState.lastExpandedWidth,
      sidebarMenus: SIDEBAR_MENUS,
      loginEnabled: false,
      username: '',
      confirmedTheme: initialTheme,
      activeTheme: initialTheme,
      brand: systemBrand,
      themeDrawerOpen: false,
      themeSaving: false,
    }
  },
  computed: {
    pendingRoute() {
      const route = this.navigation.pendingRoute
      return route && isWorkspaceRoute(route) ? route : null
    },
    isTopNavigation() {
      return this.activeTheme.navigationLayout === 'TOP'
    },
    isSidebarCompact() {
      return this.sidebarWidth < SIDEBAR_COMPACT_THRESHOLD
    },
    activeMenuIndex() {
      return getActiveMenuIndex((this.pendingRoute || this.$route).path)
    },
    workspaceTabs() {
      const tabs = this.$store.getters['workspaceTabs/tabs'] || []
      const pending = this.pendingRoute
      // 只展示临时页签，导航成功后才写入工作区缓存。
      return pending && !tabs.some(tab => tab.fullPath === pending.fullPath)
        ? [...tabs, this.routeTab(pending)]
        : tabs
    },
    activeTabPath() {
      if (this.pendingRoute) return this.pendingRoute.fullPath
      return this.$store.getters['workspaceTabs/activePath'] || ''
    },
    currentViewKey() {
      const viewKey = this.$store.getters['workspaceTabs/viewKey']
      const baseKey = viewKey
        ? viewKey(this.$route.fullPath)
        : this.$route.fullPath
      const current = this.$store.state.currentProject
      const projectId = normalizeProjectId(this.$route.query?.projectId) || normalizeProjectId(current && current.id)
      return routeSupportsProjectContext(this.$route.path) && projectId
        ? `${baseKey}::project:${projectId}`
        : baseKey
    },
    avatarInitial() {
      return getAvatarInitial(this.accountDisplayName)
    },
    accountDisplayName() {
      return this.loginEnabled ? this.username || '用户' : '本地用户'
    },
  },
  watch: {
    '$route.fullPath'() {
      this.openCurrentRoute()
      this.syncProjectContext()
    },
  },
  created() {
    applyTheme(this.confirmedTheme)
    this.restoreWorkspaceTabs()
    this.cancelMenuPreload = preloadMenuRoutes(this.$router, filterMenus(SIDEBAR_MENUS).map(menu => menu.index))
  },
  async mounted() {
    window.addEventListener('keydown', this.handleWorkspaceShortcut, true)
    await this.refreshAuthBar()
    if (this.$.isUnmounted) return
    await this.syncProjectContext()
  },
  beforeUnmount() {
    this.cancelMenuPreload?.()
    window.removeEventListener('keydown', this.handleWorkspaceShortcut, true)
  },
  methods: {
    preloadPage(path) { return preloadRoute(this.$router, path) },
    routeTab(route) {
      const tab = routeToTab(route)
      if (!route || !['ExpressionEditor', 'ConfigurationExpressionEditor'].includes(route.name)) return tab
      const getter = this.$store.getters['expressionSessions/sessionById']
      const session =
        typeof getter === 'function' ? getter(route.params.sessionId) : null
      return {
        ...tab,
        ...(session && session.title ? { detailTitle: session.title } : {}),
      }
    },
    restoreWorkspaceTabs() {
      if (!isWorkspaceRoute(this.$route)) return
      const cached = readWorkspaceTabs(browserSessionStorage())
      const cachedTabs = cached.tabs
        .map((tab) => {
          try {
            const resolved = this.$router.resolve(tab.fullPath)
            return isWorkspaceRoute(resolved)
              ? { ...tab, ...this.routeTab(resolved) }
              : null
          } catch (e) {
            return null
          }
        })
        .filter(Boolean)
      this.$store.dispatch('workspaceTabs/restore', {
        cachedTabs,
        currentTab: this.routeTab(this.$route),
      })
    },
    openCurrentRoute() {
      if (!isWorkspaceRoute(this.$route)) return
      this.$store.dispatch('workspaceTabs/open', this.routeTab(this.$route))
    },
    async navigateTo(fullPath, options = {}) {
      if (!fullPath || (fullPath === this.$route.fullPath && !this.pendingRoute)) return
      if (
        !options.skipDesignerGuard &&
        !(await confirmDesignerPathsCanClose([this.$route.fullPath]))
      ) {
        return { cancelled: true }
      }
      const target = withProjectContext(
        fullPath,
        this.$store.state.currentProject && this.$store.state.currentProject.id
      )
      return this.$router.push(target).catch((error) => {
        if (isNavigationFailure(error)) return
        // router.onError 已展示可重试反馈，避免点击事件产生未处理的 rejection。
        if (isHandledNavigationError(error)) return { cancelled: true }
        throw error
      })
    },
    async syncProjectContext() {
      this.$store.commit('INVALIDATE_PROJECT_CONTEXT_REQUESTS')
      const generation = this.$store.state.projectContextGeneration
      if (this.$route.path === '/project') {
        if (this.$store.state.currentProject) {
          this.$store.commit('SET_CURRENT_PROJECT', null)
        }
        return
      }
      if (!routeSupportsProjectContext(this.$route.path)) return
      const explicitProjectId = normalizeProjectId(
        this.$route && this.$route.query && this.$route.query.projectId
      )
      const current = this.$store.state.currentProject
      const projectId =
        explicitProjectId || normalizeProjectId(current && current.id)
      if (!projectId) return
      try {
        const response = await getProject(projectId)
        if (generation !== this.$store.state.projectContextGeneration) return
        if (!response.data) throw new Error('项目不存在')
        this.$store.commit('SET_CURRENT_PROJECT', response.data)
      } catch (e) {
        if (generation !== this.$store.state.projectContextGeneration) return
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.$message.error(e.message || '加载项目上下文失败')
      }
    },
    async activateTab(fullPath) {
      if (!fullPath) return
      const result = await this.navigateTo(fullPath)
      if (result && (result.cancelled || isNavigationFailure(result))) return result
      this.$store.dispatch('workspaceTabs/activate', fullPath)
      return result
    },
    handleWorkspaceShortcut(event) {
      const command = resolveWorkspaceShortcut(
        event,
        this.workspaceTabs,
        this.activeTabPath
      )
      if (!command) return
      if (event && typeof event.preventDefault === 'function')
        event.preventDefault()
      if (command.type === 'activate')
        return this.activateTab(command.targetPath)
      return this.handleTabOperation(command)
    },
    async handleTabOperation({ operation, targetPath }) {
      if (this.pendingRoute) {
        // 加载中的页签尚未提交到工作区；关闭它只取消导航并恢复原页面。
        if (operation === 'current' && targetPath === this.pendingRoute.fullPath) {
          return this.$router.push(this.$route.fullPath)
        }
        return { cancelled: true }
      }
      if (operation === 'refresh') {
        const affectedPaths = targetPath === this.$route.fullPath
          ? [targetPath]
          : [targetPath, this.$route.fullPath]
        if (!(await confirmDesignerPathsCanClose(affectedPaths))) {
          return { cancelled: true }
        }
        if (targetPath !== this.$route.fullPath) {
          this.$store.dispatch('workspaceTabs/activate', targetPath)
          await this.navigateTo(targetPath, { skipDesignerGuard: true })
        }
        await this.$store.dispatch('workspaceTabs/refresh', targetPath)
        return
      }

      const preview = resolveCloseOperation(
        this.workspaceTabs,
        this.activeTabPath,
        targetPath,
        operation
      )
      const retainedPaths = new Set(
        preview.tabs.map((tab) => tab.fullPath)
      )
      const closingPaths = this.workspaceTabs
        .filter((tab) => !retainedPaths.has(tab.fullPath))
        .map((tab) => tab.fullPath)
      if (!(await confirmDesignerPathsCanClose(closingPaths))) {
        return { cancelled: true }
      }
      const result = await this.$store.dispatch('workspaceTabs/close', {
        operation,
        targetPath,
      })
      if (result.nextPath !== this.$route.fullPath) {
        await this.navigateTo(result.nextPath, { skipDesignerGuard: true })
      }
      return result
    },
    handleSidebarResize(width) {
      const nextWidth = clampSidebarWidth(width)
      this.sidebarWidth = nextWidth
      if (nextWidth >= SIDEBAR_COMPACT_THRESHOLD) {
        this.lastExpandedWidth = nextWidth
      }
    },
    handleSidebarResizeEnd(width) {
      this.handleSidebarResize(width)
      this.persistSidebarState()
    },
    toggleSidebar() {
      if (this.isSidebarCompact) {
        this.sidebarWidth = Math.max(
          SIDEBAR_COMPACT_THRESHOLD,
          this.lastExpandedWidth
        )
      } else {
        this.lastExpandedWidth = this.sidebarWidth
        this.sidebarWidth = SIDEBAR_MIN_WIDTH
      }
      this.persistSidebarState()
    },
    persistSidebarState() {
      writeSidebarState(browserSessionStorage(), {
        width: this.sidebarWidth,
        lastExpandedWidth: this.lastExpandedWidth,
      })
    },
    openThemeSettings() {
      this.themeDrawerOpen = true
    },
    handleAccountCommand(command) {
      if (command === 'theme') return this.openThemeSettings()
      if (command === 'settings' && this.loginEnabled) {
        return this.navigateTo('/account/profile')
      }
      if (command === 'logout') return this.doLogout()
    },
    handleThemePreview(theme) {
      this.activeTheme = applyTheme(theme)
    },
    cancelThemePreview(snapshot) {
      const restored = normalizeThemeConfig(snapshot || this.confirmedTheme)
      this.confirmedTheme = restored
      this.activeTheme = restored
      applyTheme(restored)
    },
    confirmTheme(theme) {
      const confirmed = normalizeThemeConfig(theme)
      this.confirmedTheme = confirmed
      this.activeTheme = confirmed
      applyTheme(confirmed)
      writeLocalTheme(browserLocalStorage(), confirmed)
      return confirmed
    },
    async saveTheme(theme) {
      if (this.themeSaving) return
      const candidate = normalizeThemeConfig(theme)
      this.themeSaving = true
      try {
        let saved = candidate
        if (this.loginEnabled && this.username) {
          const response = await saveConsoleThemePreference(candidate)
          saved = normalizeThemeConfig(response && response.data)
        }
        this.confirmTheme(saved)
        this.themeDrawerOpen = false
        this.$message.success('主题设置已保存')
      } catch (e) {
        this.$message.error(e.message || '保存主题设置失败')
      } finally {
        this.themeSaving = false
      }
    },
    async refreshAuthBar() {
      try {
        const cfg = await getConsoleAuthConfig()
        this.loginEnabled = !!(cfg.data && cfg.data.loginEnabled)
        if (!this.loginEnabled) {
          this.username = ''
          clearCurrentUser()
          this.sidebarMenus = filterMenus(SIDEBAR_MENUS)
          this.confirmTheme(readLocalTheme(browserLocalStorage()))
          return
        }
        const me = await getConsoleMe()
        this.username = (me.data && me.data.username) || ''
        if (me.data && Array.isArray(me.data.permissions)) {
          setCurrentUser(me.data)
        } else {
          clearCurrentUser()
        }
        this.sidebarMenus = filterMenus(SIDEBAR_MENUS, {
          hidePublicWhenEmpty: getCurrentUser()?.permissions?.length === 0
        })
        try {
          const theme = await getConsoleThemePreference()
          this.confirmTheme(theme && theme.data
            ? theme.data
            : DEFAULT_THEME_CONFIG)
        } catch (themeError) {
          this.confirmTheme(DEFAULT_THEME_CONFIG)
          this.$message.warning(
            themeError.message || '加载主题设置失败，已使用默认主题'
          )
        }
      } catch (e) {
        this.loginEnabled = false
        this.username = ''
        clearCurrentUser()
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.sidebarMenus = filterMenus(SIDEBAR_MENUS)
      }
    },
    async doLogout() {
      try {
        await consoleLogout()
      } finally {
        clearCurrentUser()
        this.$store.commit('SET_CURRENT_PROJECT', null)
        this.$router.replace({ path: '/login' })
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.layout-shell {
  display: flex;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-width: 0;
  overflow: hidden;
  background: var(--tianshu-bg-page);
}
.layout-workspace {
  --layout-account-width: 136px;
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
}
.layout-primary-topbar {
  position: relative;
  z-index: 11;
  display: flex;
  height: 60px;
  min-width: 0;
  padding-right: var(--layout-account-width);
  overflow: hidden;
  flex: none;
  align-items: stretch;
  color: var(--tianshu-sidebar-text);
  background: var(--tianshu-sidebar-bg);
  border-bottom: 1px solid var(--tianshu-sidebar-border);
  box-sizing: border-box;
  box-shadow: var(--tianshu-shadow-small, 0 2px 8px rgba(15, 23, 42, 0.08));
}
.top-navigation__brand {
  display: flex;
  width: 220px;
  padding: 0 18px;
  flex: none;
  align-items: center;
  gap: 10px;
  border-right: 1px solid var(--tianshu-sidebar-border);
  box-sizing: border-box;

  strong {
    overflow: hidden;
    color: var(--tianshu-sidebar-text-active);
    font-size: 16px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.top-navigation__logo {
  width: 34px;
  height: 34px;
  flex: none;
}
.top-navigation__menu {
  display: flex;
  min-width: 0;
  padding: 7px 10px 5px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  align-items: center;
  gap: 4px;
}
.top-navigation__item {
  position: relative;
  display: inline-flex;
  height: 40px;
  padding: 0 12px;
  flex: none;
  align-items: center;
  gap: 7px;
  color: var(--tianshu-sidebar-text);
  font: inherit;
  font-size: 13px;
  background: transparent;
  border: 0;
  border-radius: 7px;
  cursor: pointer;
  transition: color 160ms ease, background-color 160ms ease,
    box-shadow 160ms ease;

  &::after {
    position: absolute;
    right: 12px;
    bottom: 2px;
    left: 12px;
    height: 2px;
    content: '';
    background: transparent;
    border-radius: 999px;
  }

  &:hover,
  &:focus-visible {
    color: var(--tianshu-sidebar-text-active);
    background: var(--tianshu-sidebar-bg-hover);
    outline: none;
  }

  &.is-active {
    color: var(--tianshu-sidebar-active-foreground);
    font-weight: 600;
    background: var(--tianshu-sidebar-bg-active);
    box-shadow: inset 0 0 0 1px rgba(var(--el-color-primary-rgb), 0.22);

    &::after {
      background: var(--tianshu-scrollbar-thumb);
      box-shadow: 0 0 8px var(--tianshu-scrollbar-glow);
    }
  }
}
.layout-topbar {
  position: relative;
  z-index: 10;
  display: flex;
  flex: none;
  height: 52px;
  min-width: 0;
  padding-right: var(--layout-account-width);
  align-items: stretch;
  background: var(--tianshu-bg-surface);
}
.layout-account {
  position: absolute;
  z-index: 13;
  top: 0;
  right: 0;
  display: flex;
  width: var(--layout-account-width);
  height: calc(52px - 1px);
  flex: none;
  padding: 0 8px;
  align-items: center;
  justify-content: flex-end;
  background: var(--tianshu-bg-surface);
  box-sizing: border-box;

  &::before {
    position: absolute;
    top: 50%;
    left: 0;
    width: 1px;
    height: 20px;
    content: '';
    background: color-mix(in srgb, var(--tianshu-border-subtle) 55%, transparent);
    transform: translateY(-50%);
  }
}
.layout-shell.is-top-navigation {
  .layout-topbar {
    padding-right: 0;
  }

  .layout-account {
    height: calc(60px - 1px);
    color: var(--tianshu-sidebar-text-active);
    background: var(--tianshu-sidebar-bg);
    &::before {
      background: color-mix(in srgb, var(--tianshu-sidebar-border) 60%, transparent);
    }
  }

  .layout-account-trigger {
    color: var(--tianshu-sidebar-text-active);

    &:hover {
      color: var(--tianshu-sidebar-text-active);
      background: var(--tianshu-sidebar-bg-hover);
    }
  }

  .layout-account-arrow {
    color: var(--tianshu-sidebar-text);
  }
}
.layout-account-trigger {
  appearance: none;
  display: inline-flex;
  height: 36px;
  min-width: 0;
  max-width: 100%;
  padding: 0 6px;
  align-items: center;
  color: var(--tianshu-text-primary);
  font: inherit;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  gap: 6px;
  transition: color 160ms ease, background-color 160ms ease;

  &:hover {
    color: var(--el-color-primary);
    background: var(--tianshu-bg-soft);
  }

  &:focus-visible {
    outline: 2px solid var(--tianshu-focus-ring, rgba(38, 57, 233, 0.3));
    outline-offset: 2px;
  }
}
.layout-account-avatar,
.layout-account-name {
  height: 24px;
  line-height: 24px;
}
.layout-account-avatar {
  display: inline-flex;
  flex: none;
  width: 24px;
  align-items: center;
  justify-content: center;
  color: var(--tianshu-brand-foreground, #ffffff);
  font-size: 12px;
  font-weight: 600;
  background: var(--tianshu-brand-background, #2639e9);
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}
.layout-account-name {
  min-width: 0;
  max-width: 80px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.layout-account-arrow {
  flex: none;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  transition: transform 160ms ease;
}
.layout-footer {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 12px;
  padding: 0 16px 12px;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  overflow-wrap: anywhere;
  background: var(--tianshu-bg-page);

  > * {
    max-width: 100%;
  }

  a,
  .layout-footer-contact {
    color: var(--tianshu-text-secondary);
    text-decoration: none;

    &:hover,
    &:focus-visible {
      color: var(--el-color-primary);
      text-decoration: underline;
    }
  }
}
.layout-footer-contact {
  padding: 0;
  border: 0;
  font: inherit;
  line-height: inherit;
  white-space: nowrap;
  background: transparent;
  cursor: pointer;
}
.layout-footer-qr-image {
  display: block;
  width: 220px;
  max-width: 100%;
  height: auto;
  padding: 8px;
  // 二维码保持白色底衬，避免暗色主题影响扫码对比度。
  background: var(--el-color-white);
}
.layout-main {
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  flex: 1;
  box-sizing: border-box;
  background: var(--tianshu-bg-page);

  > :deep(.uiue-list-page) {
    min-height: 100%;
  }
}

@media (max-width: 1120px) {
  .layout-workspace {
    --layout-account-width: 120px;
  }

  .top-navigation__brand {
    width: 184px;
  }

  .layout-account-name {
    max-width: 52px;
  }
}
</style>
