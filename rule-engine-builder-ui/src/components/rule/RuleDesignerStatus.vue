<template>
  <div class="rule-designer-info" data-testid="designer-info">
    <span class="rule-designer-info__fields">{{ loading ? '正在加载变量库…' : `已加载 ${fieldCount} 个变量/常量/对象字段` }}</span>
    <span class="rule-designer-info__status" role="status">{{ label }}<span v-if="sourceLabel"> · {{ sourceLabel }}</span></span>
  </div>
</template>

<script>
const LABELS = {
  CLEAN: '已加载，尚未修改', DIRTY: '有未保存修改', SAVING: '正在保存',
  SAVED_UNCHECKED: '草稿已保存', CHECK_FAILED: '编译或发布前检查未通过',
  READY_TO_TEST: '编译与发布前检查通过', SAVE_CONFLICT: '保存冲突，修改仍保留',
}
export default {
  name: 'RuleDesignerStatus',
  props: { state: { type: String, default: 'CLEAN' }, fieldCount: { type: Number, default: 0 }, loading: Boolean, sourceLabel: { type: String, default: '' } },
  computed: { label() { return LABELS[this.state] || LABELS.CLEAN } },
}
</script>

<style scoped>
.rule-designer-info { display: flex; justify-content: space-between; align-items: center; gap: 16px; width: 100%; padding: 6px 0 12px; font-size: 12px; color: var(--tianshu-text-secondary); }
.rule-designer-info__fields { text-align: left; }
.rule-designer-info__status { margin-left: auto; text-align: right; color: var(--tianshu-text-primary); }
</style>
