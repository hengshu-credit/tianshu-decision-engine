<template>
  <div class="rule-draft-source-shell">
    <div
      class="rule-draft-read-only__card"
      :data-testid="visible ? 'draft-read-only' : undefined"
      :role="visible ? 'status' : undefined"
      :aria-label="visible ? '规则版本信息' : undefined"
      :aria-live="visible ? 'polite' : undefined"
    >
      <span v-if="visible" class="rule-draft-read-only__notice" :title="noticeMessage">
        <app-icon :name="loading ? 'Loading' : 'InfoFilled'" />
        <strong>{{ loading ? '正在加载规则版本' : loadError ? '当前版本加载失败' : '当前版本只读' }}</strong>
        <span class="rule-draft-read-only__reason">{{ noticeMessage }}</span>
      </span>
      <rule-designer-version-select
        v-if="sourceOptions.length"
        :options="sourceOptions"
        :model-value="selectedSource"
        :loading="sourceLoading"
        :disabled="disabled"
        :allow-delete="!visible"
        @change="$emit('change-source', $event)"
        @delete="$emit('delete-source', $event)"
      />
      <div v-if="visible" class="rule-draft-read-only__actions">
        <el-button v-if="canTestFrozen && !loading" type="primary" size="small" data-testid="designer-frozen-test" @click="$emit('test-frozen')">测试所选冻结版本</el-button>
        <el-button v-if="migrationRequired && !loading" plain type="primary" size="small" data-testid="designer-reference-migration" @click="$emit('open-migration')">离线迁移</el-button>
        <el-button
          v-if="loadError && !loading"
          plain
          type="primary"
          size="small"
          data-testid="designer-source-retry"
          @click="$emit('retry')"
        >
          重试
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import RuleDesignerVersionSelect from './RuleDesignerVersionSelect.vue'

export default {
  name: 'RuleDraftReadOnly',
  components: { RuleDesignerVersionSelect },
  data() {
    return {
      inertedSiblings: [],
    }
  },
  props: {
    visible: { type: Boolean, default: false },
    loading: { type: Boolean, default: false },
    loadError: { type: Boolean, default: false },
    revisionLabel: { type: String, default: '' },
    revisionState: { type: String, default: '' },
    canFork: { type: Boolean, default: false },
    hasEditableDraft: { type: Boolean, default: false },
    sourceOptions: { type: Array, default: () => [] },
    selectedSource: { type: String, default: '' },
    sourceLoading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    readOnlyReason: { type: String, default: '' },
    canTestFrozen: { type: Boolean, default: false },
    migrationRequired: { type: Boolean, default: false },
  },
  emits: ['change-source', 'delete-source', 'fork', 'go-lifecycle', 'retry', 'test-frozen', 'open-migration'],
  computed: {
    noticeMessage() {
      if (this.loading) return '正在读取所选版本，不会创建草稿。'
      if (this.readOnlyReason) return this.readOnlyReason
      if (this.loadError) return '无法显示当前版本，可重试或选择其他版本。'
      return `${this.revisionLabel}：当前账号没有规则编辑权限，可选择版本查看。`
    },
  },
  mounted() {
    this.syncInertSiblings()
  },
  updated() {
    this.syncInertSiblings()
  },
  beforeUnmount() {
    this.clearInertSiblings()
  },
  methods: {
    clearInertSiblings() {
      this.inertedSiblings.forEach(({ element, added }) => {
        if (added) element.removeAttribute('inert')
      })
      this.inertedSiblings = []
    },
    syncInertSiblings() {
      this.clearInertSiblings()
      if (!this.visible || !this.$el?.parentElement) return
      const designerRoot = this.$el.closest('.uiue-compact-designer') || this.$el.parentElement
      const activeControls = [this.$el, ...designerRoot.querySelectorAll('[data-designer-back]')]
      // 只保留版本、恢复操作和标题栏返回按钮；其余编辑分支继续隔离。
      const protectChildren = (parent) => {
        Array.from(parent.children).forEach((element) => {
          if (activeControls.includes(element)) return
          if (activeControls.some((control) => element.contains(control))) {
            protectChildren(element)
            return
          }
          const added = !element.hasAttribute('inert')
          if (added) element.setAttribute('inert', '')
          this.inertedSiblings.push({ element, added })
        })
      }
      protectChildren(designerRoot)
    },
  },
}
</script>

<style lang="scss" scoped>
.rule-draft-source-shell {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  color: var(--designer-toolbar-foreground, var(--tianshu-text-secondary));
}

.rule-draft-read-only__card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  background: transparent;

  strong {
    color: var(--designer-toolbar-foreground, var(--tianshu-text-primary));
    white-space: nowrap;
  }

  span {
    font-size: 13px;
  }
}

.rule-draft-read-only__notice {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 360px;

  .app-icon, strong { flex-shrink: 0; }
}

.rule-draft-read-only__reason {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-draft-read-only__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
</style>
