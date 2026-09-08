<template>
  <aside
    class="layout-sidebar"
    :class="{ 'is-compact': compact, 'is-resizing': resizing }"
    :style="{ width: width + 'px' }"
  >
    <div class="sidebar-brand">
      <img
        :src="brandLogoUrl"
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
  },
  data() {
    return {
      brandLogoUrl: `${import.meta.env.BASE_URL || './'}images/hengshucredit_animated.svg`,
      resizing: false,
      resizeStartX: 0,
      resizeStartWidth: 0,
    }
  },
  beforeUnmount() {
    this.removeResizeListeners()
  },
  methods: {
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
  emits: ['navigate', 'resize', 'resize-end'],
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
  color: var(--tianshu-sidebar-text, #{$menuText});
  background: var(--tianshu-sidebar-bg, #{$menuBg});
  border-right: 1px solid var(--tianshu-sidebar-border, rgba(255, 255, 255, 0.06));
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
  border-bottom: 1px solid var(--tianshu-sidebar-border, rgba(255, 255, 255, 0.08));
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
    color: var(--tianshu-sidebar-text-active, #ffffff);
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
  color: var(--tianshu-text-disabled);
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
  color: var(--tianshu-sidebar-text, #{$menuText});
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
    color: var(--tianshu-sidebar-text-active, #ffffff);
    background: var(--tianshu-sidebar-bg-hover, #{$menuHover});
    outline: none;
  }

  &.is-active {
    color: var(--tianshu-sidebar-text-active, #ffffff);
    font-weight: 600;
    background: var(--tianshu-sidebar-bg-active, rgba($--color-primary, 0.28));

    &::after {
      background: var(--el-color-primary, #7180ff);
    }
  }
}
.menu-icon {
  flex: none;
  width: 24px;
  color: var(--tianshu-sidebar-text, var(--tianshu-text-disabled));
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
      background: var(--el-color-primary, #7180ff);
}

:global([data-sidebar-theme='light']) .brand-copy strong {
  color: var(--tianshu-text-primary, #172033);
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
}
</style>
