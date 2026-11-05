<template>
  <div class="version-preview-summary" :class="{ 'is-comparing': comparing }">
    <div class="business-preview-intro">
      <div><strong>{{ comparing ? '业务内容对比' : '业务内容预览' }}</strong><p>{{ comparing ? '按业务配置逐项对照，标记表示相对左侧版本的变化。' : '查看此版本保存的业务配置与执行内容。' }}</p></div>
      <el-tag v-if="comparing" :type="changedCount || left.warning || right.warning ? 'warning' : 'info'">{{ left.warning || right.warning ? '部分内容无法预览' : changedCount ? `${changedCount} 项配置有变化` : '预览内容一致' }}</el-tag>
    </div>
    <div class="business-preview-head" :class="{ 'two-columns': comparing }">
      <div v-if="comparing"><span>左侧 · 基准版本</span><strong>{{ originalLabel }}</strong></div>
      <div><span>{{ comparing ? '右侧 · 当前所选版本' : '当前所选版本' }}</span><strong>{{ modifiedLabel }}</strong></div>
    </div>
    <el-alert v-if="left.warning || right.warning" :title="[left.warning && '左侧：' + left.warning, right.warning && (comparing ? '右侧：' : '') + right.warning].filter(Boolean).join(' ')" type="warning" :closable="false" show-icon />
    <div v-for="row in rows" :key="row.key" class="business-preview-row" :class="[{ 'two-columns': comparing }, 'is-' + row.status]" :data-section="row.key">
      <section v-if="comparing" class="business-preview-card">
        <div v-if="row.status !== 'same'" class="business-change-label">{{ changeLabel(row.status) }}</div>
        <template v-if="row.left"><h4 v-if="row.key.startsWith('rule:')">{{ row.left.title }}</h4><version-business-node v-for="(item, index) in row.left.nodes" :key="index" :node="item" /><p v-if="!row.left.nodes.length" class="business-missing">暂无{{ row.left.title }}</p></template>
        <p v-else class="business-missing">此版本未配置{{ row.right?.title }}</p>
      </section>
      <section class="business-preview-card">
        <div v-if="comparing && row.status !== 'same'" class="business-change-label">{{ changeLabel(row.status) }}</div>
        <template v-if="row.right"><h4 v-if="row.key.startsWith('rule:')">{{ row.right.title }}</h4><version-business-node v-for="(item, index) in row.right.nodes" :key="index" :node="item" /><p v-if="!row.right.nodes.length" class="business-missing">暂无{{ row.right.title }}</p></template>
        <p v-else class="business-missing">此版本已移除{{ row.left?.title }}</p>
      </section>
    </div>
    <el-empty v-if="!rows.length && !right.warning" description="此快照暂无业务配置" :image-size="64" />
    <p class="business-preview-note">预览以版本快照为准；完整配置及未展示的系统字段可展开下方原始 JSON 查看。</p>
  </div>
</template>
<script>
import { buildResourcePreview, pairPreviewSections } from '@/utils/resourceVersionPreview'
import VersionBusinessNode from './VersionBusinessNode.vue'
export default {
  name: 'ResourceVersionPreview',
  components: { VersionBusinessNode },
  props: {
    resourceType: { type: String, required: true }, snapshot: { type: [String, Object], required: true },
    original: { type: [String, Object], default: '' }, comparing: Boolean,
    originalLabel: { type: String, default: '基准版本' }, modifiedLabel: { type: String, default: '当前版本' },
  },
  computed: {
    left() { return this.comparing ? buildResourcePreview(this.original, this.resourceType) : { sections: [] } },
    right() { return buildResourcePreview(this.snapshot, this.resourceType) },
    rows() { return this.comparing ? pairPreviewSections(this.left.sections, this.right.sections) : this.right.sections.map(section => ({ key: section.key, right: section, status: 'same' })) },
    changedCount() { return this.rows.filter(row => row.status !== 'same').length },
  },
  methods: { changeLabel(status) { return { added: '＋ 新增配置', removed: '－ 移除配置', changed: '修改配置' }[status] } },
}
</script>
<style scoped>
.version-preview-summary { margin: 16px 0; min-width: 0; }
.business-preview-intro { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.business-preview-intro strong { font-size: 16px; color: var(--tianshu-text-primary); }
.business-preview-intro p, .business-preview-note { margin: 8px 0 0; font-size: 13px; color: var(--tianshu-text-secondary); line-height: 1.6; }
.business-preview-head, .business-preview-row { display: grid; gap: 12px; min-width: 0; }
.two-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.business-preview-head { margin-bottom: 12px; }
.business-preview-head > div { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px 16px; padding: 12px 16px; background: var(--tianshu-bg-soft); border-radius: 8px; border: 1px solid var(--tianshu-border); }
.business-preview-head span { font-size: 12px; color: var(--tianshu-text-secondary); }
.business-preview-head strong { color: var(--tianshu-text-primary); font-size: 14px; }
.business-preview-row { margin-bottom: 8px; }
.business-preview-card { min-width: 0; padding: 8px 16px; border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; background: var(--tianshu-bg-surface); }
.business-preview-card h4 { font-size: 14px; margin: 8px 0; color: var(--tianshu-text-primary); }
.is-changed .business-preview-card { border-color: var(--tianshu-warning-border); background: var(--tianshu-warning-bg); }
.is-added .business-preview-card { border-color: var(--tianshu-success-border); }
.is-removed .business-preview-card { border-color: var(--tianshu-danger-border); }
.business-change-label { font-size: 12px; font-weight: 600; margin: 4px 0; color: var(--tianshu-warning-text); }
.is-added .business-change-label { color: var(--tianshu-success-text); }
.is-removed .business-change-label { color: var(--tianshu-danger-text); }
.business-missing { font-size: 13px; color: var(--tianshu-text-tertiary); line-height: 1.7; }
@media (max-width: 640px) { .two-columns { grid-template-columns: minmax(0, 1fr); } .is-comparing .business-preview-card:first-child::before { content: '左侧 · 基准版本'; color: var(--tianshu-text-tertiary); font-size: 12px; } .is-comparing .business-preview-card:last-child::before { content: '右侧 · 当前所选版本'; color: var(--tianshu-text-tertiary); font-size: 12px; } }
</style>
