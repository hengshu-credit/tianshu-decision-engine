<template>
  <header class="workspace-tabs" aria-label="工作区页签">
    <div ref="scroll" class="workspace-tabs__scroll">
      <div
        v-for="tab in tabs"
        :key="tab.fullPath"
        class="workspace-tab"
        :class="{ 'is-active': tab.fullPath === activePath }"
        :data-tab="tab.fullPath"
        @contextmenu.prevent.stop="openContextMenu($event, tab.fullPath)"
      >
        <button
          type="button"
          class="workspace-tab__main"
          :data-path="tab.fullPath"
          :title="tab.title"
          @click="$emit('activate', tab.fullPath)"
        >
          <span class="workspace-tab__dot" aria-hidden="true" />
          <span class="workspace-tab__title">{{ tab.title }}</span>
        </button>
        <button
          type="button"
          class="workspace-tab__close"
          :data-close="tab.fullPath"
          :aria-label="'关闭' + tab.title"
          :title="'关闭' + tab.title"
          @click.stop="performOperation('current', tab.fullPath)"
        >
          <i class="el-icon-close" />
        </button>
      </div>
    </div>

    <el-dropdown trigger="click" placement="bottom-end" @command="handleOverflowCommand">
      <button type="button" class="workspace-tabs__more" aria-label="页签操作" title="页签操作">
        <i class="el-icon-more" />
      </button>
      <el-dropdown-menu slot="dropdown">
        <el-dropdown-item command="refresh" :disabled="!activePath">
          <span class="workspace-tab-operation"><span>刷新当前</span><span class="workspace-tab-operation__shortcut">Ctrl+R</span></span>
        </el-dropdown-item>
        <el-dropdown-item command="current" :disabled="!activePath">
          <span class="workspace-tab-operation"><span>关闭当前</span><span class="workspace-tab-operation__shortcut">Ctrl+W</span></span>
        </el-dropdown-item>
        <el-dropdown-item command="left" :disabled="isOperationDisabled('left', activePath)">关闭左侧</el-dropdown-item>
        <el-dropdown-item command="right" :disabled="isOperationDisabled('right', activePath)">关闭右侧</el-dropdown-item>
        <el-dropdown-item command="others" :disabled="isOperationDisabled('others', activePath)">关闭其他</el-dropdown-item>
        <el-dropdown-item command="all" divided :disabled="tabs.length === 0">关闭全部</el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>

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
        <i :class="operation.icon" />
        <span>{{ operation.label }}</span>
        <span v-if="operation.shortcut" class="workspace-tab-operation__shortcut">{{ operation.shortcut }}</span>
      </button>
    </div>
  </header>
</template>

<script>
export default {
  name: 'WorkspaceTabs',
  props: {
    tabs: { type: Array, default: () => [] },
    activePath: { type: String, default: '' }
  },
  data() {
    return {
      contextMenu: {
        visible: false,
        left: 0,
        top: 0,
        targetPath: ''
      },
      operations: [
        { key: 'refresh', label: '刷新', icon: 'el-icon-refresh-right', shortcut: 'Ctrl+R' },
        { key: 'current', label: '关闭当前', icon: 'el-icon-close', shortcut: 'Ctrl+W' },
        { key: 'left', label: '关闭左侧', icon: 'el-icon-back' },
        { key: 'right', label: '关闭右侧', icon: 'el-icon-right' },
        { key: 'others', label: '关闭其他', icon: 'el-icon-files' },
        { key: 'all', label: '关闭全部', icon: 'el-icon-circle-close' }
      ]
    }
  },
  watch: {
    activePath() {
      this.closeContextMenu()
      this.$nextTick(this.scrollActiveIntoView)
    }
  },
  mounted() {
    document.addEventListener('click', this.closeContextMenu)
    window.addEventListener('blur', this.closeContextMenu)
    window.addEventListener('scroll', this.closeContextMenu, true)
    this.scrollActiveIntoView()
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeContextMenu)
    window.removeEventListener('blur', this.closeContextMenu)
    window.removeEventListener('scroll', this.closeContextMenu, true)
  },
  methods: {
    openContextMenu(event, targetPath) {
      const menuWidth = 176
      const menuHeight = 224
      const viewportWidth = window.innerWidth || document.documentElement.clientWidth
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight
      this.contextMenu = {
        visible: true,
        left: Math.max(8, Math.min(event.clientX || 0, viewportWidth - menuWidth - 8)),
        top: Math.max(8, Math.min(event.clientY || 0, viewportHeight - menuHeight - 8)),
        targetPath
      }
    },
    closeContextMenu() {
      if (!this.contextMenu.visible) return
      this.contextMenu = { ...this.contextMenu, visible: false }
    },
    isOperationDisabled(operation, targetPath) {
      const index = this.tabs.findIndex(tab => tab.fullPath === targetPath)
      if (!targetPath || index < 0) return true
      if (operation === 'left') return index === 0
      if (operation === 'right') return index === this.tabs.length - 1
      if (operation === 'others') return this.tabs.length <= 1
      if (operation === 'all') return this.tabs.length === 0
      return false
    },
    performOperation(operation, targetPath) {
      if (this.isOperationDisabled(operation, targetPath)) return
      this.$emit('operate', { operation, targetPath })
      this.closeContextMenu()
    },
    handleOverflowCommand(operation) {
      if (!this.activePath && operation !== 'all') return
      this.$emit('operate', { operation, targetPath: this.activePath })
    },
    scrollActiveIntoView() {
      const elements = this.$el ? this.$el.querySelectorAll('.workspace-tab') : []
      const active = Array.from(elements).find(element => element.dataset.tab === this.activePath)
      if (active && typeof active.scrollIntoView === 'function') {
        active.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.workspace-tabs {
  position: relative;
  z-index: 10;
  display: flex;
  flex: none;
  height: 44px;
  min-width: 0;
  align-items: stretch;
  background: #FFFFFF;
  border-bottom: 1px solid #DDE3EE;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.05);
}

.workspace-tabs__scroll {
  display: flex;
  min-width: 0;
  padding: 6px 8px 0;
  overflow-x: auto;
  overflow-y: hidden;
  flex: 1;
  align-items: flex-end;
  gap: 4px;
  scrollbar-width: thin;
}

.workspace-tab {
  position: relative;
  display: flex;
  flex: none;
  height: 36px;
  max-width: 200px;
  align-items: center;
  color: #64748B;
  background: #F5F7FA;
  border: 1px solid #E2E8F0;
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
  transition: color 160ms ease, background-color 160ms ease, border-color 160ms ease;

  &::after {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 2px;
    content: '';
    background: transparent;
  }

  &:hover {
    color: #334155;
    background: #FFFFFF;
  }

  &.is-active {
    color: #1E293B;
    font-weight: 600;
    background: #FFFFFF;
    border-color: #CBD5E1;

    &::after {
      background: $--color-primary;
    }

    .workspace-tab__dot {
      background: $--color-primary;
    }
  }
}

.workspace-tab__main {
  display: flex;
  min-width: 0;
  height: 100%;
  padding: 0 4px 0 12px;
  align-items: center;
  color: inherit;
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;

  &:focus-visible {
    border-radius: 5px;
    outline: 2px solid rgba($--color-primary, 0.35);
    outline-offset: -2px;
  }
}

.workspace-tab__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-right: 8px;
  background: #CBD5E1;
  border-radius: 50%;
}

.workspace-tab__title {
  overflow: hidden;
  font-size: 13px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-tab__close {
  display: flex;
  flex: none;
  width: 28px;
  height: 28px;
  padding: 0;
  margin-right: 2px;
  align-items: center;
  justify-content: center;
  color: #94A3B8;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: #334155;
    background: #E9EEF5;
    outline: none;
  }
}

.workspace-tabs__more {
  display: flex;
  width: 44px;
  height: 43px;
  padding: 0;
  align-items: center;
  justify-content: center;
  color: #64748B;
  font-size: 18px;
  background: #FFFFFF;
  border: 0;
  border-left: 1px solid #E2E8F0;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: $--color-primary;
    background: #F8FAFC;
    outline: none;
  }
}

.workspace-tabs__context-menu {
  position: fixed;
  z-index: 3000;
  display: flex;
  width: 176px;
  padding: 6px;
  flex-direction: column;
  background: #FFFFFF;
  border: 1px solid #DCE3ED;
  border-radius: 8px;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14), 0 3px 8px rgba(15, 23, 42, 0.1);

  button {
    display: flex;
    height: 34px;
    padding: 0 10px;
    align-items: center;
    color: #334155;
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
      color: #64748B;
      text-align: center;
    }

    &:hover:not(:disabled),
    &:focus-visible:not(:disabled) {
      color: $--color-primary;
      background: #EEF1FF;
      outline: none;
    }

    &:disabled {
      color: #CBD5E1;
      cursor: not-allowed;

      i {
        color: inherit;
      }
    }

    &.is-divided {
      margin-top: 5px;
      border-top: 1px solid #EEF2F6;
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
  color: #94A3B8;
  font-size: 12px;
  font-weight: 400;
}
</style>
