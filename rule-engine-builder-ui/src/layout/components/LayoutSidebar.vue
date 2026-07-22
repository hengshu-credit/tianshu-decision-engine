<template>
  <aside
    class="layout-sidebar"
    :class="{ 'is-compact': compact, 'is-resizing': resizing }"
    :style="{ width: width + 'px' }"
  >
    <div class="sidebar-brand">
      <img
        src="/images/hengshucredit_animated.svg"
        alt="天枢决策引擎"
        class="sidebar-brand__logo"
      />
      <div v-if="!compact" class="brand-copy">
        <strong>天枢决策引擎</strong>
        <span>天工开物，枢衡定策</span>
      </div>
    </div>

    <nav class="sidebar-menu" aria-label="主导航">
      <button
        v-for="menu in menus"
        :key="menu.index"
        type="button"
        class="sidebar-menu__item"
        :class="{ 'is-active': activeMenu === menu.index }"
        :data-menu-path="menu.index"
        :title="compact ? menu.label : ''"
        :aria-label="menu.label"
        @click="$emit('navigate', menu.index)"
      >
        <app-icon :name="menu.icon" class="menu-icon" />
        <span v-if="!compact" class="menu-label">{{ menu.label }}</span>
      </button>
    </nav>

    <div v-if="loginEnabled" class="sidebar-account">
      <template v-if="!compact">
        <span class="account-avatar" aria-hidden="true">{{
          avatarInitial
        }}</span>
        <span class="account-name" :title="username">{{ username }}</span>
        <button type="button" class="account-logout" @click="$emit('logout')">
          退出
        </button>
      </template>
      <el-dropdown
        v-else
        trigger="click"
        placement="right-end"
        @command="handleAccountCommand"
      >
        <button
          type="button"
          class="account-avatar account-avatar--button"
          :title="username || '用户账户'"
          :aria-label="username ? username + '的账户菜单' : '用户账户菜单'"
        >
          {{ avatarInitial }}
        </button>
        <template v-slot:dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>{{
              username || '用户'
            }}</el-dropdown-item>
            <el-dropdown-item command="logout" divided
              >退出登录</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <button
      type="button"
      class="sidebar-resizer"
      aria-label="调整侧边栏宽度"
      title="拖拽调整侧边栏宽度"
      @mousedown.prevent="startResize"
    />
  </aside>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import { clampSidebarWidth } from '@/layout/layoutState'

export default {
  name: 'LayoutSidebar',
  props: {
    width: { type: Number, required: true },
    compact: { type: Boolean, default: false },
    activeMenu: { type: String, default: '' },
    menus: { type: Array, default: () => [] },
    loginEnabled: { type: Boolean, default: false },
    username: { type: String, default: '' },
    avatarInitial: { type: String, default: 'U' },
  },
  data() {
    return {
      resizing: false,
      resizeStartX: 0,
      resizeStartWidth: 0,
    }
  },
  beforeUnmount() {
    this.removeResizeListeners()
  },
  methods: {
    handleAccountCommand(command) {
      if (command === 'logout') $emit(this, 'logout')
    },
    startResize(event) {
      this.resizing = true
      this.resizeStartX = event.clientX
      this.resizeStartWidth = this.width
      window.addEventListener('mousemove', this.handleResize)
      window.addEventListener('mouseup', this.finishResize)
    },
    resizeWidth(event) {
      return clampSidebarWidth(
        this.resizeStartWidth + event.clientX - this.resizeStartX
      )
    },
    handleResize(event) {
      if (!this.resizing) return
      $emit(this, 'resize', this.resizeWidth(event))
    },
    finishResize(event) {
      if (!this.resizing) return
      const width = this.resizeWidth(event)
      $emit(this, 'resize', width)
      $emit(this, 'resize-end', width)
      this.resizing = false
      this.removeResizeListeners()
    },
    removeResizeListeners() {
      window.removeEventListener('mousemove', this.handleResize)
      window.removeEventListener('mouseup', this.finishResize)
    },
  },
  emits: ['navigate', 'logout', 'resize', 'resize-end'],
}
</script>

<style lang="scss" scoped>
.layout-sidebar {
  position: relative;
  display: flex;
  flex: none;
  flex-direction: column;
  height: 100vh;
  box-sizing: border-box;
  color: $menuText;
  background: $menuBg;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  transition: width 180ms ease;
  &.is-resizing {
    transition: none;
    user-select: none;
  }
}
.sidebar-brand {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  min-height: 60px;
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.sidebar-brand__logo {
  flex: none;
  width: 36px;
  height: 36px;
}
.brand-copy {
  display: flex;
  min-width: 0;
  margin-left: 10px;
  flex-direction: column;
  strong {
    overflow: hidden;
    color: #ffffff;
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    overflow: hidden;
    margin-top: 2px;
    color: #22d3ee;
    font-size: 12px;
    line-height: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.sidebar-collapse {
  position: absolute;
  right: 6px;
  bottom: -12px;
  z-index: 2;
  display: flex;
  width: 24px;
  height: 24px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: #262d4f;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 50%;
  cursor: pointer;
  &:hover,
  &:focus-visible {
    color: #ffffff;
    border-color: rgba(255, 255, 255, 0.3);
    outline: none;
  }
}
.sidebar-menu {
  display: flex;
  min-height: 0;
  padding: 10px 8px 10px;
  overflow-x: hidden;
  overflow-y: auto;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}
.sidebar-menu__item {
  position: relative;
  display: flex;
  flex: none;
  width: 100%;
  height: 44px;
  padding: 0 12px;
  align-items: center;
  color: $menuText;
  font: inherit;
  font-size: 14px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  transition: color 160ms ease, background-color 160ms ease;
  &::after {
    position: absolute;
    top: 10px;
    right: -8px;
    bottom: 10px;
    width: 3px;
    content: '';
    background: transparent;
    border-radius: 3px 0 0 3px;
  }

  &:hover,
  &:focus-visible {
    color: #ffffff;
    background: $menuHover;
    outline: none;
  }

  &.is-active {
    color: #ffffff;
    font-weight: 600;
    background: rgba($--color-primary, 0.28);

    &::after {
      background: #7180ff;
    }
  }
}
.menu-icon {
  flex: none;
  width: 24px;
  color: #94a3b8;
  font-size: 18px;
  text-align: center;
}
.sidebar-menu__item.is-active .menu-icon,
.sidebar-menu__item:hover .menu-icon {
  color: inherit;
}
.menu-label {
  overflow: hidden;
  margin-left: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sidebar-account {
  display: flex;
  min-height: 64px;
  padding: 12px 16px;
  align-items: center;
  box-sizing: border-box;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.account-avatar {
  display: inline-flex;
  flex: none;
  width: 34px;
  height: 34px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(145deg, #5264f2, #2639e9);
  border-radius: 50%;
}
.account-avatar--button {
  cursor: pointer;
}
.account-name {
  min-width: 0;
  margin-left: 10px;
  overflow: hidden;
  color: #e2e8f0;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-logout {
  flex: none;
  padding: 4px;
  margin-left: auto;
  color: #9caafb;
  font: inherit;
  font-size: 13px;
  background: transparent;
  border: 0;
  cursor: pointer;
  &:hover,
  &:focus-visible {
    color: #ffffff;
    outline: none;
  }
}
.sidebar-resizer {
  position: absolute;
  z-index: 4;
  top: 0;
  right: -3px;
  bottom: 0;
  width: 6px;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: ew-resize;
  &::after {
    position: absolute;
    top: 0;
    right: 2px;
    bottom: 0;
    width: 2px;
    content: '';
    background: transparent;
    transition: background-color 160ms ease;
  }

  &:hover::after,
  &:focus-visible::after {
    background: #7180ff;
  }
}
.layout-sidebar.is-compact {
  .sidebar-brand {
    padding: 0;
    justify-content: center;
  }

  .sidebar-collapse {
    right: -12px;
  }

  .sidebar-menu {
    padding-right: 10px;
    padding-left: 10px;
  }

  .sidebar-menu__item {
    width: 44px;
    padding: 0;
    justify-content: center;

    &::after {
      right: -10px;
    }
  }

  .menu-icon {
    width: auto;
  }

  .sidebar-account {
    padding: 12px 0;
    justify-content: center;
  }
}
</style>
