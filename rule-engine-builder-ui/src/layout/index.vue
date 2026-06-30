<template>
  <el-container style="height: 100vh;">
    <el-header height="50px" class="layout-header">
      <div class="header-title">
        <img src="/images/hengshucredit_animated.svg" alt="logo" class="header-logo">
        <div class="header-text">
          <span class="header-main-text">天枢决策引擎</span>
          <span class="header-sub-text">天工开物, 枢衡定策</span>
        </div>
      </div>
      <div v-if="loginEnabled" class="header-actions">
        <span class="user-label">{{ username }}</span>
        <el-button type="text" @click="doLogout">退出</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside :width="sideBarWidth + 'px'" class="layout-aside">
        <el-menu
          :default-active="activeMenuIndex"
          router
          :background-color="menuBg"
          :text-color="menuText"
          :active-text-color="menuActiveText"
        >
          <el-menu-item index="/project">
            <i class="el-icon-folder" />
            <span>项目管理</span>
          </el-menu-item>
          <el-menu-item index="/rule">
            <i class="el-icon-document" />
            <span>规则管理</span>
          </el-menu-item>
          <el-menu-item index="/variable">
            <i class="el-icon-collection-tag" />
            <span>变量管理</span>
          </el-menu-item>
          <el-menu-item index="/list">
            <i class="el-icon-tickets" />
            <span>名单管理</span>
          </el-menu-item>
          <el-menu-item index="/datasource">
            <i class="el-icon-link" />
            <span>外数管理</span>
          </el-menu-item>
          <el-menu-item index="/database">
            <i class="el-icon-set-up" />
            <span>数据库管理</span>
          </el-menu-item>
          <el-menu-item index="/model">
            <i class="el-icon-cpu" />
            <span>模型管理</span>
          </el-menu-item>
          <el-menu-item index="/function">
            <i class="el-icon-s-operation" />
            <span>函数管理</span>
          </el-menu-item>
          <el-menu-item index="/test">
            <i class="el-icon-video-play" />
            <span>规则测试</span>
          </el-menu-item>
          <el-menu-item index="/log">
            <i class="el-icon-document" />
            <span>执行日志</span>
          </el-menu-item>
          <el-menu-item index="/billing">
            <i class="el-icon-coin" />
            <span>账单管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import variables from '@/styles/variables.scss'
import { getConsoleAuthConfig, consoleLogout, getConsoleMe } from '@/api/auth'

export default {
  name: 'Layout',
  data() {
    return {
      loginEnabled: false,
      username: ''
    }
  },
  computed: {
    sideBarWidth() { return parseInt(variables.sideBarWidth) },
    menuBg() { return variables.menuBg },
    menuText() { return variables.menuText },
    menuActiveText() { return variables.menuActiveText },
    /**
     * 根据当前路由路径返回菜单激活状态。
     * - 设计师页面 (/designer/*) → 高亮"规则管理"
     * - 项目详情 (/project/:id) → 高亮"项目管理"
     * - 其他路由直接匹配菜单 index
     */
    activeMenuIndex() {
      const path = this.$route.path
      if (path.startsWith('/designer/')) return '/rule'
      if (/^\/project\/\d+$/.test(path)) return '/project'
      if (/^\/list\/\d+$/.test(path)) return '/list'
      return path
    }
  },
  async mounted() {
    await this.refreshAuthBar()
  },
  methods: {
    /**
     * 根据后端配置决定是否展示登录用户与退出按钮。
     */
    async refreshAuthBar() {
      try {
        const cfg = await getConsoleAuthConfig()
        this.loginEnabled = !!(cfg.data && cfg.data.loginEnabled)
        if (!this.loginEnabled) return
        const me = await getConsoleMe()
        this.username = (me.data && me.data.username) || ''
      } catch (e) {
        this.loginEnabled = false
        this.username = ''
      }
    },
    /**
     * 调用登出接口并回到登录页。
     */
    async doLogout() {
      try {
        await consoleLogout()
      } finally {
        this.$router.replace({ path: '/login' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-header {
  background: $menuBg;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-title {
    display: flex;
    align-items: center;
    gap: 8px;
    transform: scale(0.8);
    transform-origin: center left;

    .header-logo {
      height: 40px;
      width: 40px;
    }

    .header-text {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: flex-start;
    }

    .header-main-text {
      font-size: 18px;
      font-weight: bold;
      color: #FFFFFF;
      line-height: 1.25;
      white-space: nowrap;
    }

    .header-sub-text {
      font-size: 12px;
      color: #22d3ee;
      line-height: 1;
      white-space: nowrap;
      margin-top: 2px;
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 14px;
    color: #94A3B8;
  }

  .user-label {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.layout-aside {
  background: $menuBg;
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

::v-deep .el-menu {
  border-right: none;

  .el-menu-item {
    color: $menuText;

    &:hover {
      background: $menuHover;
    }

    &.is-active {
      color: $menuActiveText;
      background: rgba($--color-primary, 0.25);
      border-right: 3px solid $--color-primary;
    }
  }
}

.layout-main {
  background: #F3F4F6;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;
}
</style>
