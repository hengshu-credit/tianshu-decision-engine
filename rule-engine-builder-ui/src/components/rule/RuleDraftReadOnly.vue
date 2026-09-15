<template>
  <div class="rule-draft-source-shell">
    <div
      v-if="visible"
      class="rule-draft-read-only"
      data-testid="draft-read-only"
      role="status"
      aria-label="规则版本信息"
      aria-live="polite"
    >
      <div class="rule-draft-read-only__card">
        <app-icon :name="loading ? 'Loading' : 'InfoFilled'" />
        <strong>{{ loading ? '正在加载规则版本' : loadError ? '当前版本加载失败' : '当前版本只读' }}</strong>
        <span v-if="loading">正在读取所选版本，不会创建草稿。</span>
        <span v-else-if="loadError">无法显示当前版本，可重试或选择其他版本。</span>
        <span v-else>{{ revisionLabel }}：当前账号没有规则编辑权限，可选择版本查看。</span>
        <rule-designer-version-select
          v-if="sourceOptions.length"
          :options="sourceOptions"
          :model-value="selectedSource"
          :loading="sourceLoading"
          @change="$emit('change-source', $event)"
        />
        <div class="rule-draft-read-only__actions">
          <el-button
            v-if="loadError && !loading"
            link
            type="primary"
            size="small"
            data-testid="designer-source-retry"
            @click="$emit('retry')"
          >
            重试
          </el-button>
          <el-button
            link
            size="small"
            data-testid="draft-read-only-back"
            @click="$emit('go-back')"
          >
            返回
          </el-button>
        </div>
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
  },
  emits: ['change-source', 'fork', 'go-back', 'go-lifecycle', 'retry'],
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
      this.inertedSiblings = Array.from(this.$el.parentElement.children)
        .filter((element) => element !== this.$el)
        .map((element) => {
          const added = !element.hasAttribute('inert')
          if (added) element.setAttribute('inert', '')
          return { element, added }
        })
    },
  },
}
</script>

<style lang="scss" scoped>
.rule-draft-source-shell {
  display: contents;
}

.rule-draft-read-only {
  flex-shrink: 0;
  min-width: 0;
  margin-bottom: 8px;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-muted);
  border-radius: 4px;
}

.rule-draft-read-only__card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 12px;

  strong {
    color: var(--tianshu-text-primary);
    white-space: nowrap;
  }

  span {
    font-size: 13px;
  }
}

.rule-draft-read-only__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
</style>
