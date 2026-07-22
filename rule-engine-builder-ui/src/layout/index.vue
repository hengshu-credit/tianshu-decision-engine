<template>
  <div class="layout-shell">
    <layout-sidebar
      :width="sidebarWidth"
      :compact="isSidebarCompact"
      :active-menu="activeMenuIndex"
      :menus="sidebarMenus"
      :login-enabled="loginEnabled"
      :username="username"
      :avatar-initial="avatarInitial"
      @navigate="navigateTo"
      @toggle-collapse="toggleSidebar"
      @resize="handleSidebarResize"
      @resize-end="handleSidebarResizeEnd"
      @logout="doLogout"
    />
    <section class="layout-workspace">
      <workspace-tabs
        :tabs="workspaceTabs"
        :active-path="activeTabPath"
        @activate="activateTab"
        @operate="handleTabOperation"
      />
      <main class="layout-main">
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
    </section>
  </div>
</template>

<script>
import { isNavigationFailure } from 'vue-router'
import LayoutSidebar from '@/layout/components/LayoutSidebar.vue'
import WorkspaceTabs from '@/layout/components/WorkspaceTabs.vue'
import {
  SIDEBAR_COMPACT_THRESHOLD,
  SIDEBAR_MENUS,
  SIDEBAR_MIN_WIDTH,
  clampSidebarWidth,
  getActiveMenuIndex,
  getAvatarInitial,
  isWorkspaceRoute,
  readSidebarState,
  resolveWorkspaceShortcut,
  routeToTab,
  writeSidebarState,
} from '@/layout/layoutState'
import { readWorkspaceTabs } from '@/store/modules/workspaceTabs'
import { getConsoleAuthConfig, consoleLogout, getConsoleMe } from '@/api/auth'

function browserSessionStorage() {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null
  } catch (e) {
    return null
  }
}

export default {
  name: 'Layout',
  components: { LayoutSidebar, WorkspaceTabs },
  data() {
    const sidebarState = readSidebarState(browserSessionStorage())
    return {
      sidebarWidth: sidebarState.width,
      lastExpandedWidth: sidebarState.lastExpandedWidth,
      sidebarMenus: SIDEBAR_MENUS,
      loginEnabled: false,
      username: '',
    }
  },
  computed: {
    isSidebarCompact() {
      return this.sidebarWidth < SIDEBAR_COMPACT_THRESHOLD
    },
    activeMenuIndex() {
      return getActiveMenuIndex(this.$route.path)
    },
    workspaceTabs() {
      return this.$store.getters['workspaceTabs/tabs'] || []
    },
    activeTabPath() {
      return this.$store.getters['workspaceTabs/activePath'] || ''
    },
    currentViewKey() {
      const viewKey = this.$store.getters['workspaceTabs/viewKey']
      return viewKey ? viewKey(this.$route.fullPath) : this.$route.fullPath
    },
    avatarInitial() {
      return getAvatarInitial(this.username)
    },
  },
  watch: {
    '$route.fullPath'() {
      this.openCurrentRoute()
    },
  },
  created() {
    this.restoreWorkspaceTabs()
  },
  async mounted() {
    window.addEventListener('keydown', this.handleWorkspaceShortcut, true)
    await this.refreshAuthBar()
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.handleWorkspaceShortcut, true)
  },
  methods: {
    routeTab(route) {
      const tab = routeToTab(route)
      if (!route || route.name !== 'ExpressionEditor') return tab
      const getter = this.$store.getters['expressionSessions/sessionById']
      const session =
        typeof getter === 'function' ? getter(route.params.sessionId) : null
      return {
        ...tab,
        title: session && session.title ? session.title : tab.title,
      }
    },
    restoreWorkspaceTabs() {
      if (!isWorkspaceRoute(this.$route)) return
      const cached = readWorkspaceTabs(browserSessionStorage())
      const cachedTabs = cached.tabs
        .map((tab) => {
          try {
            const resolved = this.$router.resolve(tab.fullPath)
            return isWorkspaceRoute(resolved) ? this.routeTab(resolved) : null
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
    navigateTo(fullPath) {
      if (!fullPath || fullPath === this.$route.fullPath) return
      return this.$router.push(fullPath).catch((error) => {
        if (isNavigationFailure(error)) return
        throw error
      })
    },
    activateTab(fullPath) {
      if (!fullPath) return
      this.$store.dispatch('workspaceTabs/activate', fullPath)
      return this.navigateTo(fullPath)
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
      if (operation === 'refresh') {
        if (targetPath !== this.$route.fullPath) {
          this.$store.dispatch('workspaceTabs/activate', targetPath)
          await this.navigateTo(targetPath)
        }
        await this.$store.dispatch('workspaceTabs/refresh', targetPath)
        return
      }

      const result = await this.$store.dispatch('workspaceTabs/close', {
        operation,
        targetPath,
      })
      if (result.nextPath !== this.$route.fullPath) {
        await this.navigateTo(result.nextPath)
      }
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
    async refreshAuthBar() {
      try {
        const cfg = await getConsoleAuthConfig()
        this.loginEnabled = !!(cfg.data && cfg.data.loginEnabled)
        if (!this.loginEnabled) {
          this.username = ''
          return
        }
        const me = await getConsoleMe()
        this.username = (me.data && me.data.username) || ''
      } catch (e) {
        this.loginEnabled = false
        this.username = ''
      }
    },
    async doLogout() {
      try {
        await consoleLogout()
      } finally {
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
  min-width: 0;
  overflow: hidden;
  background: #f3f4f6;
}
.layout-workspace {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  flex-direction: column;
}
.layout-main {
  min-width: 0;
  min-height: 0;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  flex: 1;
  box-sizing: border-box;
  background: #f3f4f6;
}
</style>
