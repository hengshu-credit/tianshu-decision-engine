<template>
  <nav class="workspace-tabs" aria-label="工作区页签">
    <div ref="scroll" class="workspace-tabs__scroll">
      <div
        v-for="tab in displayTabs"
        :key="tab.fullPath"
        class="workspace-tab"
        :class="{ 'is-active': tab.fullPath === activePath }"
        :data-tab="tab.fullPath"
        @contextmenu.prevent.stop="openContextMenu($event, tab.fullPath)"
      >
        <el-tooltip
          :content="tab.title"
          placement="bottom"
          :show-after="300"
          popper-class="workspace-tab-tooltip"
        >
          <button
            type="button"
            class="workspace-tab__main"
            :data-path="tab.fullPath"
            :aria-label="tab.title"
            @click="$emit('activate', tab.fullPath)"
            @mouseenter="$emit('preload', tab.fullPath)"
            @focus="$emit('preload', tab.fullPath)"
          >
            <span class="workspace-tab__title" :class="{ 'is-overflowing': overflowingPaths.includes(tab.fullPath) }">{{ tab.displayTitle }}</span>
          </button>
        </el-tooltip>
        <button
          type="button"
          class="workspace-tab__close"
          :data-close="tab.fullPath"
          :disabled="isOperationDisabled('current', tab.fullPath)"
          :aria-label="'关闭' + tab.title"
          :title="'关闭' + tab.title"
          @click.stop="performOperation('current', tab.fullPath)"
        >
          <el-icon><el-icon-close /></el-icon>
        </button>
      </div>
    </div>

    <div
      v-if="contextMenu.visible"
      class="workspace-tabs__context-menu layout-popup-menu"
      :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
      role="menu"
    >
      <button
        v-for="operation in operations"
        :key="operation.key"
        type="button"
        role="menuitem"
        class="layout-popup-menu__item"
        :data-operation="operation.key"
        :disabled="isOperationDisabled(operation.key, contextMenu.targetPath)"
        :class="{ 'is-divided': operation.key === 'all' }"
        @click="performOperation(operation.key, contextMenu.targetPath)"
      >
        <app-icon :name="operation.icon" />
        <span>{{ operation.label }}</span>
        <span
          v-if="operation.shortcut"
          class="workspace-tab-operation__shortcut layout-popup-menu__shortcut"
          >{{ operation.shortcut }}</span
        >
      </button>
    </div>
  </nav>
</template>

<script>
import { Close as ElIconClose } from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import { SIDEBAR_MENUS } from '@/layout/layoutState'
const TAB_TITLE_LABELS = {
  '外数数据源详情': '外数',
  '外数 API 详情': 'API',
  '数据库数据源详情': '数据库',
  '配置表达式': '表达式',
}
export default {
  components: {
    ElIconClose,
  },
  name: 'WorkspaceTabs',
  props: {
    tabs: { type: Array, default: () => [] },
    activePath: { type: String, default: '' },
    pendingPath: { type: String, default: '' },
  },
  data() {
    return {
      overflowingPaths: [],
      contextMenu: {
        visible: false,
        left: 0,
        top: 0,
        targetPath: '',
      },
      operations: [
        {
          key: 'refresh',
          label: '刷新',
          icon: 'RefreshRight',
          shortcut: 'Ctrl+R',
        },
        {
          key: 'current',
          label: '关闭当前',
          icon: 'Close',
          shortcut: 'Ctrl+W',
        },
        { key: 'left', label: '关闭左侧', icon: 'Back' },
        { key: 'right', label: '关闭右侧', icon: 'Right' },
        { key: 'others', label: '关闭其他', icon: 'Files' },
        { key: 'all', label: '关闭全部', icon: 'CircleClose' },
      ],
    }
  },
  computed: {
    displayTabs() {
      return this.tabs.map(tab => {
        const path = tab.path || String(tab.fullPath || '').split('?')[0]
        const topLevel = SIDEBAR_MENUS.some(menu => menu.index === path)
        const title = topLevel ? tab.title : TAB_TITLE_LABELS[tab.title] || tab.title.replace(/(?:管理|详情|设计器|编辑器)$/, '').trim()
        return {
          ...tab,
          title: tab.detailTitle ? `${title} · ${tab.detailTitle}` : title,
          displayTitle: tab.detailTitle ? `${title}·${tab.detailTitle}` : title,
        }
      })
    },
  },
  watch: {
    tabs: { deep: true, handler() { this.$nextTick(this.observeTitles) } },
    activePath() {
      this.closeContextMenu()
      this.$nextTick(this.scrollActiveIntoView)
      this.$nextTick(this.measureTitles)
    },
  },
  mounted() {
    document.addEventListener('click', this.closeContextMenu)
    window.addEventListener('blur', this.closeContextMenu)
    window.addEventListener('scroll', this.closeContextMenu, true)
    this.scrollActiveIntoView()
    this.titleObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(this.measureTitles)
    this.observeTitles()
    document.fonts?.ready.then(() => this.measureTitles())
  },
  beforeUnmount() {
    document.removeEventListener('click', this.closeContextMenu)
    window.removeEventListener('blur', this.closeContextMenu)
    window.removeEventListener('scroll', this.closeContextMenu, true)
    this.titleObserver?.disconnect()
    this.titleObserver = null
  },
  methods: {
    observeTitles() {
      this.titleObserver?.disconnect()
      this.$el?.querySelectorAll('.workspace-tab__title').forEach(title => this.titleObserver?.observe(title))
      this.measureTitles()
    },
    measureTitles() {
      if (!this.$el?.isConnected) return
      const paths = Array.from(this.$el.querySelectorAll('.workspace-tab')).filter(tab => {
        const title = tab.querySelector('.workspace-tab__title')
        return title && title.scrollWidth > title.clientWidth
      }).map(tab => tab.dataset.tab)
      if (paths.length !== this.overflowingPaths.length || paths.some((path, index) => path !== this.overflowingPaths[index])) this.overflowingPaths = paths
    },
    openContextMenu(event, targetPath) {
      const menuWidth = 200
      const menuHeight = 224
      const viewportWidth =
        window.innerWidth || document.documentElement.clientWidth
      const viewportHeight =
        window.innerHeight || document.documentElement.clientHeight
      this.contextMenu = {
        visible: true,
        left: Math.max(
          8,
          Math.min(event.clientX || 0, viewportWidth - menuWidth - 8)
        ),
        top: Math.max(
          8,
          Math.min(event.clientY || 0, viewportHeight - menuHeight - 8)
        ),
        targetPath,
      }
    },
    closeContextMenu() {
      if (!this.contextMenu.visible) return
      this.contextMenu = { ...this.contextMenu, visible: false }
    },
    isOperationDisabled(operation, targetPath) {
      if (this.pendingPath) return operation !== 'current' || targetPath !== this.pendingPath
      const index = this.tabs.findIndex((tab) => tab.fullPath === targetPath)
      if (!targetPath || index < 0) return true
      if (operation === 'left') return index === 0
      if (operation === 'right') return index === this.tabs.length - 1
      if (operation === 'others') return this.tabs.length <= 1
      if (operation === 'all') return this.tabs.length === 0
      return false
    },
    performOperation(operation, targetPath) {
      if (this.isOperationDisabled(operation, targetPath)) return
      $emit(this, 'operate', { operation, targetPath })
      this.closeContextMenu()
    },
    scrollActiveIntoView() {
      const elements = this.$el
        ? this.$el.querySelectorAll('.workspace-tab')
        : []
      const active = Array.from(elements).find(
        (element) => element.dataset.tab === this.activePath
      )
      if (active && typeof active.scrollIntoView === 'function') {
        active.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      }
    },
  },
  emits: ['activate', 'operate', 'preload'],
}
</script>

<style lang="scss" scoped>
.workspace-tabs {
  position: relative;
  display: flex;
  height: 52px;
  min-width: 0;
  overflow: hidden;
  flex: 1;
  align-items: center;
}
.workspace-tabs__scroll {
  display: flex;
  min-width: 0;
  height: 100%;
  padding: 6px 12px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  align-items: center;
  box-sizing: border-box;
  gap: 0;
}
.workspace-tab {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  height: 40px;
  width: fit-content;
  min-width: 96px;
  max-width: 160px;
  box-sizing: border-box;
  align-items: center;
  color: var(--tianshu-text-secondary);
  background: transparent;
  border: 0;
  border-radius: 0;
  transition: color 160ms ease, background-color 160ms ease;

  &::after {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 2px;
    content: '';
    background: var(--tianshu-border);
    pointer-events: none;
  }

  &:hover {
    color: var(--tianshu-text-primary);
    background: var(--tianshu-bg-hover);
  }

  &.is-active {
    color: var(--tianshu-text-primary);
    font-weight: 600;

    &::after {
      background: var(--tianshu-brand-background);
    }
  }
}
.workspace-tab + .workspace-tab::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 1px;
  height: 16px;
  content: '';
  background: color-mix(in srgb, var(--el-color-primary) 32%, transparent);
  border-radius: 1px;
  transform: translateY(-50%);
  pointer-events: none;
}
.workspace-tab__main {
  display: flex;
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 8px;
  align-items: center;
  color: inherit;
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
  &:focus-visible {
    border-radius: 5px;
    outline: 2px solid var(--tianshu-focus-ring, rgba($--color-primary, 0.35));
    outline-offset: -2px;
  }
}
.workspace-tab__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 20px;
  text-align: left;
  text-overflow: clip;
  white-space: nowrap;
  &.is-overflowing {
    mask-image: linear-gradient(to right, #000 calc(100% - 12px), transparent);
  }
}
:global(.workspace-tab-tooltip) {
  max-width: min(480px, calc(100vw - 32px));
  overflow-wrap: anywhere;
  white-space: normal;
}
.workspace-tab__close {
  position: absolute;
  right: 4px;
  z-index: 1;
  display: flex;
  flex: none;
  width: 20px;
  height: 20px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-hover);
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: color 160ms ease, background-color 160ms ease,
    opacity 160ms ease;
  &:hover,
  &:focus-visible {
    color: var(--tianshu-text-primary, #334155);
    background: var(--tianshu-bg-hover, #e9eef5);
    outline: none;
    opacity: 1;
  }
}
.workspace-tab:hover .workspace-tab__close,
.workspace-tab:has(:focus-visible) .workspace-tab__close {
  opacity: 1;
  pointer-events: auto;
}
.workspace-tabs__context-menu {
  position: fixed;
  z-index: 3000;
  display: flex;
  width: 200px;
  flex-direction: column;
}
.workspace-tab-operation {
  display: flex;
  min-width: 144px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
</style>
