<template>
  <section class="rule-designer-actions" aria-label="规则设计操作">
    <div class="rule-designer-actions__buttons">
      <button v-for="action in actions" :key="action.key" type="button"
        v-permission="action.key === 'publish' ? 'rule:submit' : 'rule:edit'"
        class="rule-designer-actions__button" :class="'rule-designer-actions__' + action.key"
        :data-action="action.key" :disabled="busy || state === 'SAVING' || (action.key === 'test' ? !canTest : !canEdit)"
        :aria-busy="busy" :title="action.key === 'test' ? '使用当前页面配置测试' : action.label"
        @click="$emit(action.key)">{{ action.label }}</button>
    </div>
    <div v-if="issueContext" class="issue-context">
      <span>校验定位 · {{ pathLabel(issueContext.path) }}：{{ issueContext.message }}</span>
      <button type="button" :disabled="issueContext.stale" @click="$emit('locate', issueContext)">定位问题</button>
    </div>
    <rule-validation-report v-if="report" class="rule-designer-actions__report" :report="report" locatable @locate="$emit('locate', $event)" />
  </section>
</template>

<script>
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'
import { validationPathLabel } from '@/utils/validationIssueLocation'
export default {
  name: 'RuleDesignerActionBar',
  components: { RuleValidationReport },
  props: {
    canEdit: Boolean, canTest: Boolean, busy: Boolean,
    state: { type: String, default: 'CLEAN' },
    report: { type: Object, default: null },
    issueContext: { type: Object, default: null },
    recovery: { type: Object, default: null },
  },
  emits: ['compile', 'save', 'publish', 'test', 'locate', 'restore', 'discard-recovery'],
  data: () => ({ actions: [{ key: 'compile', label: '编译' }, { key: 'save', label: '保存' }, { key: 'publish', label: '发布' }, { key: 'test', label: '测试' }] }),
  methods: { pathLabel: validationPathLabel },
}
</script>

<style scoped>
.rule-designer-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 12px; }
.rule-designer-actions__buttons { display: inline-flex; align-items: center; gap: 8px; }
.rule-designer-actions__button { min-height: 32px; padding: 0 14px; border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; background: var(--tianshu-bg-surface); color: var(--tianshu-text-primary); font: inherit; cursor: pointer; }
.rule-designer-actions__button:hover:not(:disabled) { background: var(--tianshu-bg-muted); color: var(--tianshu-text-primary); }
.rule-designer-actions__publish { border-color: var(--el-color-primary); background-color: var(--el-color-primary); background-image: var(--tianshu-brand-gradient); color: var(--tianshu-brand-foreground); }
.rule-designer-actions__publish:hover:not(:disabled) { background-color: var(--el-color-primary-dark-1); background-image: var(--tianshu-brand-gradient); color: var(--tianshu-brand-foreground); }
.rule-designer-actions__publish:active:not(:disabled) { background-color: var(--el-color-primary-dark-2); }
.rule-designer-actions__button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.rule-designer-actions__button:disabled { background: var(--tianshu-bg-muted); color: var(--tianshu-text-secondary); border-color: var(--tianshu-border-subtle); cursor: not-allowed; }
.rule-designer-actions__button[aria-busy="true"] { cursor: progress; }
.rule-designer-actions__report, .issue-context { flex: 1 0 100%; }
.issue-context { color: var(--tianshu-text-primary); background: var(--tianshu-bg-muted); padding: 8px; }
</style>
