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
          >
            <span class="workspace-tab__title">{{ tab.title }}</span>
          </button>
        </el-tooltip>
        <button
          type="button"
          class="workspace-tab__close"
          :data-close="tab.fullPath"
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
      class="workspace-tabs__context-menu"
      :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
      role="menu"
    >
      <button
        v-for="operation in operations"
        :key="operation.key"
        type="button"
        role="menuitem"
        :data-operation="operation.key"
        :disabled="isOperationDisabled(operation.key, contextMenu.targetPath)"
        :class="{ 'is-divided': operation.key === 'all' }"
        @click="performOperation(operation.key, contextMenu.targetPath)"
      >
        <app-icon :name="operation.icon" />
        <span>{{ operation.label }}</span>
        <span
          v-if="operation.shortcut"
          class="workspace-tab-operation__shortcut"
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
  },
  data() {
    return {
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
        }
      })
    },
  },
  watch: {
    activePath() {
      this.closeContextMenu()
      this.$nextTick(this.scrollActiveIntoView)
    },
  },
  mounted() {
    document.addEventListener('click', this.closeContextMenu)
    window.addEventListener('blur', this.closeContextMenu)
    window.addEventListener('scroll', this.closeContextMenu, true)
    this.scrollActiveIntoView()
  },
  beforeUnmount() {
    document.removeEventListener('click', this.closeContextMenu)
    window.removeEventListener('blur', this.closeContextMenu)
    window.removeEventListener('scroll', this.closeContextMenu, true)
  },
  methods: {
    openContextMenu(event, targetPath) {
      const menuWidth = 176
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
  emits: ['activate', 'operate'],
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
  padding: 8px 12px;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  align-items: center;
  box-sizing: border-box;
  gap: 2px;
}
.workspace-tab {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  height: 34px;
  width: fit-content;
  min-width: 0;
  max-width: 160px;
  box-sizing: border-box;
  align-items: center;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border);
  border-radius: 8px;
  transition: color 160ms ease, background-color 160ms ease,
    border-color 160ms ease, box-shadow 160ms ease;
  &::after {
    position: absolute;
    right: 10px;
    bottom: 2px;
    left: 10px;
    height: 2px;
    content: '';
    background: transparent;
    border-radius: 2px;
  }

  &:hover {
    color: var(--tianshu-text-primary, #334155);
    background: var(--tianshu-bg-hover);
    border-color: var(--tianshu-info-border);
  }

  &.is-active {
    color: var(--tianshu-text-primary);
    font-weight: 600;
    background: var(--tianshu-bg-elevated, var(--tianshu-bg-surface));
    border-color: var(--tianshu-info-border);
    box-shadow: var(--tianshu-shadow-small, 0 2px 8px rgba(15, 23, 42, 0.08));

    &::after {
      background: var(--tianshu-brand-background, #{$--color-primary});
    }
  }
}
.workspace-tab__main {
  display: flex;
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 26px 0 8px;
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
  text-overflow: ellipsis;
  white-space: nowrap;
}
:global(.workspace-tab-tooltip) {
  max-width: min(480px, calc(100vw - 32px));
  overflow-wrap: anywhere;
  white-space: normal;
}
.workspace-tab__close {
  position: absolute;
  right: 2px;
  display: flex;
  flex: none;
  width: 20px;
  height: 24px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: var(--tianshu-text-disabled);
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
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
.workspace-tab.is-active .workspace-tab__close {
  opacity: 0.7;
}
.workspace-tabs__context-menu {
  position: fixed;
  z-index: 3000;
  display: flex;
  width: 176px;
  padding: 6px;
  flex-direction: column;
  background: var(--tianshu-bg-elevated, var(--tianshu-bg-surface));
  border: 1px solid var(--tianshu-border, #dce3ed);
  border-radius: 8px;
  box-shadow: var(--tianshu-shadow-large, 0 16px 36px rgba(15, 23, 42, 0.14));
  button {
    display: flex;
    height: 34px;
    padding: 0 10px;
    align-items: center;
    color: var(--tianshu-text-primary, #334155);
    font: inherit;
    font-size: 13px;
    text-align: left;
    background: transparent;
    border: 0;
    border-radius: 5px;
    cursor: pointer;

    i {
      width: 20px;
      margin-right: 6px;
      color: var(--tianshu-text-tertiary);
      text-align: center;
    }

    &:hover:not(:disabled),
    &:focus-visible:not(:disabled) {
      color: var(--el-color-primary, #{$--color-primary});
      background: var(--tianshu-bg-active, #eef1ff);
      outline: none;
    }

    &:disabled {
      color: var(--tianshu-text-disabled, #cbd5e1);
      cursor: not-allowed;

      i {
        color: inherit;
      }
    }

    &.is-divided {
      margin-top: 5px;
      border-top: 1px solid var(--tianshu-border-subtle, #eef2f6);
      border-radius: 0 0 5px 5px;
    }
  }
}
.workspace-tab-operation {
  display: flex;
  min-width: 144px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.workspace-tab-operation__shortcut {
  margin-left: auto;
  color: var(--tianshu-text-disabled);
  font-size: 12px;
  font-weight: 400;
}
</style>
