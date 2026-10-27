<template>
  <div class="rule-designer-info" data-testid="designer-info">
    <span class="rule-designer-info__fields">{{ loading ? '正在加载引用目录…' : `已加载 ${fieldCount} 个可选引用` }}<span v-if="referenceMode"> · {{ referenceMode === 'FROZEN' ? '所选版本保存的资源' : referenceMode === 'LIVE' ? '当前环境资源' : '引用目录不可用' }}</span><span v-if="!loading && referenceMode === 'FROZEN' && !fieldCount" class="rule-designer-info__hint">此历史版本没有保存可选字段{{ canUseCurrentResources ? '，可使用当前资源继续编辑。' : '。' }}</span></span>
    <el-button v-if="canUseCurrentResources && !loading" link type="primary" size="small" data-testid="designer-use-current-references" @click="$emit('use-current')">使用当前资源编辑</el-button>
    <span class="rule-designer-info__status" role="status">{{ label }}<span v-if="sourceLabel"> · {{ sourceLabel }}</span></span>
  </div>
</template>

<script>
const LABELS = {
  CLEAN: '已加载，尚未修改', DIRTY: '有未保存修改', SAVING: '正在保存', COMPILING: '后台编译中',
  CHECKING: '正在校验当前配置', CHECKED: '当前配置校验通过',
  SAVED_UNCHECKED: '草稿已保存', CHECK_FAILED: '编译或发布前检查未通过',
  READY_TO_TEST: '编译与发布前检查通过', SAVE_CONFLICT: '保存冲突，修改仍保留',
  SUBMITTED: '已提交发布审批，尚未生效',
  SAVED_WITH_ISSUES: '草稿已保存，存在待修复问题（不可发布）',
}
export default {
  name: 'RuleDesignerStatus',
  props: { state: { type: String, default: 'CLEAN' }, fieldCount: { type: Number, default: 0 }, loading: Boolean, sourceLabel: { type: String, default: '' },
    referenceMode: { type: String, default: '' }, canUseCurrentResources: { type: Boolean, default: false } },
  emits: ['use-current'],
  computed: { label() { return LABELS[this.state] || LABELS.CLEAN } },
}
</script>

<style scoped>
.rule-designer-info { display: flex; justify-content: space-between; align-items: center; gap: 16px; width: 100%; padding: 6px 0 12px; font-size: 12px; color: var(--tianshu-text-secondary); }
.rule-designer-info__fields { text-align: left; }
.rule-designer-info__hint { display: block; margin-top: 4px; }
.rule-designer-info__status { margin-left: auto; text-align: right; color: var(--tianshu-text-primary); }
</style>
