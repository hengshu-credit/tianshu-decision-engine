<template>
  <div class="version-preview-summary" :class="{ 'is-comparing': comparing }">
    <div class="preview-title">
      <strong>{{ comparing ? '业务内容对比' : '业务内容预览' }}</strong>
      <el-select v-if="detailRows.length >= 3" :model-value="''" placeholder="定位配置项" aria-label="定位配置项"
        filterable size="small" class="preview-jump" @change="jumpTo">
        <el-option v-for="row in detailRows" :key="row.key" :label="(row.right || row.left).title" :value="row.key" />
      </el-select>
      <template v-if="comparing">
        <el-tag :type="changeCount || warning ? 'warning' : 'info'" size="small">{{ statusText }}</el-tag>
        <el-checkbox v-if="changeCount && !warning" v-model="changesOnly">只看差异</el-checkbox>
      </template>
    </div>
    <div class="business-preview-head pane-grid">
      <div v-for="pane in panes" :key="pane.side">
        <span>{{ pane.caption }}</span><strong>{{ pane.label }}</strong>
        <small v-if="pane.byline">{{ pane.byline }}</small>
        <p v-if="pane.note">{{ pane.note }}</p>
      </div>
    </div>
    <el-alert v-if="warning" :title="warning" type="warning" :closable="false" show-icon />
    <section v-if="basicRows.length" class="preview-section">
      <h4>基本信息</h4>
      <div class="pane-grid">
        <div v-for="pane in panes" :key="pane.side" class="overview-pane">
          <span v-if="comparing" class="narrow-version-label">{{ pane.label }}</span>
          <el-descriptions :column="comparing ? 1 : 2" border size="small">
            <el-descriptions-item v-for="row in basicRows" :key="row.key" :label="(row.right || row.left).title">
              <span>{{ row[pane.side]?.nodes[0].text ?? '此版本未配置' }}</span>
              <el-tag v-if="comparing && row.status !== 'same'" size="small" :type="tagType[row.status]">{{ changeLabel[row.status] }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </section>
    <section v-for="row in detailRows" :key="row.key" :ref="element => registerSection(row.key, element)"
      class="preview-section business-preview-row" :class="'is-' + row.status" :data-section="row.key">
      <h4>
        <button type="button" class="section-toggle" :aria-expanded="!folded.has(row.key)" @click="toggleSection(row.key)">
          <span aria-hidden="true">{{ folded.has(row.key) ? '▸' : '▾' }}</span>{{ (row.right || row.left).title }}
        </button>
        <el-tag v-if="comparing && row.status !== 'same'" :type="tagType[row.status]" size="small">{{ changeLabel[row.status] }}</el-tag>
      </h4>
      <div v-if="!folded.has(row.key)" class="pane-grid">
        <version-config-section v-for="pane in panes" :key="pane.side" :section="row[pane.side]"
          :version-label="comparing ? pane.label : ''" absent-label="此版本未配置" />
      </div>
    </section>
    <el-empty v-if="!rows.length && !warning" description="此快照暂无业务配置" :image-size="64" />
  </div>
</template>
<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { buildResourcePreview, pairPreviewSections } from '@/utils/resourceVersionPreview'
import VersionConfigSection from './VersionConfigSection.vue'

const props = defineProps({
  resourceType: { type: String, required: true }, snapshot: { type: [String, Object], required: true },
  original: { type: [String, Object], default: '' }, comparing: Boolean,
  originalLabel: { type: String, default: '基准版本' }, modifiedLabel: { type: String, default: '当前版本' },
  originalVersion: { type: Object, default: null }, modifiedVersion: { type: Object, default: null },
})
const tagType = { added: 'success', removed: 'danger', changed: 'warning' }
const changeLabel = { added: '新增', removed: '移除', changed: '已修改' }
const changesOnly = ref(false)
const folded = ref(new Set())
const sections = new Map()
function registerSection(key, element) {
  if (element) sections.set(key, element)
  else sections.delete(key)
}
function toggleSection(key) {
  if (folded.value.has(key)) folded.value.delete(key)
  else folded.value.add(key)
}
async function jumpTo(key) {
  folded.value.delete(key)
  await nextTick()
  sections.get(key)?.querySelector('.section-toggle')?.focus({ preventScroll: true })
  sections.get(key)?.scrollIntoView({ block: 'start' })
}
watch(() => [props.original, props.snapshot, props.comparing, props.originalLabel, props.modifiedLabel], () => {
  changesOnly.value = false
  folded.value.clear()
})

function versionNotes(version) {
  if (!version) return {}
  const actor = version.publishBy || version.createBy || version.applicant
  const time = version.publishTime || version.createTime
  return { byline: [actor, time && String(time).replace('T', ' ')].filter(Boolean).join(' · '), note: version.changeSummary || version.changeLog }
}

const panes = computed(() => {
  const current = { side: 'right', label: props.modifiedLabel, caption: props.comparing ? '右侧 · 对比版本' : '所选版本', ...versionNotes(props.modifiedVersion) }
  return props.comparing ? [{ side: 'left', label: props.originalLabel, caption: '左侧 · 基准版本', ...versionNotes(props.originalVersion) }, current] : [current]
})
const left = computed(() => props.comparing ? buildResourcePreview(props.original, props.resourceType) : { sections: [] })
const right = computed(() => buildResourcePreview(props.snapshot, props.resourceType))
const warning = computed(() => [
  left.value.warning && '左侧：' + left.value.warning,
  right.value.warning && (props.comparing ? '右侧：' : '') + right.value.warning,
].filter(Boolean).join(' '))
const rows = computed(() => props.comparing
  ? pairPreviewSections(left.value.sections, right.value.sections)
  : right.value.sections.map(section => ({ key: section.key, right: section, status: 'same' })))
const changeCount = computed(() => rows.value.filter(row => row.status !== 'same').length)
const statusText = computed(() => {
  if (warning.value) return '部分内容无法预览'
  return changeCount.value ? `${changeCount.value} 项配置有变化` : '预览内容一致'
})
const visibleRows = computed(() => changesOnly.value && props.comparing && changeCount.value && !warning.value
  ? rows.value.filter(row => row.status !== 'same') : rows.value)
const isBasic = row => (!row.left || row.left.basic) && (!row.right || row.right.basic)
const basicRows = computed(() => visibleRows.value.filter(isBasic))
const detailRows = computed(() => {
  const details = visibleRows.value.filter(row => !isBasic(row))
  // Put the designer first, followed by the rule's input and output contract.
  return [...details.filter(row => row.key.startsWith('rule:')), ...details.filter(row => !row.key.startsWith('rule:'))]
})
</script>
<style scoped>
.version-preview-summary { margin: 16px 0; color: var(--tianshu-text-primary); min-width: 0; }
.preview-title, .preview-section h4 { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.preview-title { margin-bottom: 12px; font-size: 16px; }
.preview-title .el-checkbox { margin-left: auto; }
.preview-jump { width: 150px; max-width: 100%; }
.section-toggle { display: inline-flex; align-items: center; gap: 6px; padding: 3px 0; background: none; border: none; color: inherit; font: inherit; cursor: pointer; text-align: left; }
.section-toggle span { color: var(--el-color-primary); }
.business-preview-head small { margin-left: 12px; font-size: 12px; color: var(--tianshu-text-secondary); }
.business-preview-head p { margin: 6px 0 0; font-size: 13px; white-space: pre-wrap; }
.pane-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; }
.is-comparing .pane-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.business-preview-head > div { padding: 10px 14px; border-left: 3px solid var(--el-color-primary); background: var(--tianshu-bg-soft); font-size: 14px; overflow-wrap: anywhere; }
.business-preview-head span { margin-right: 12px; font-size: 12px; color: var(--tianshu-text-secondary); }
.preview-section { margin-top: 16px; }
.preview-section h4 { margin: 0; padding: 8px 0; font-size: 14px; }
.business-preview-card { padding: 12px; border: 1px solid var(--tianshu-border); border-radius: 6px; }
.is-changed .business-preview-card { border-color: var(--tianshu-warning-border); }
.is-added .business-preview-card { border-color: var(--tianshu-success-border); }
.is-removed .business-preview-card { border-color: var(--tianshu-danger-border); }
.overview-pane { min-width: 0; }
.overview-pane :deep(.el-descriptions__table) { table-layout: fixed; }
.overview-pane :deep(.el-descriptions__label) { width: 7em; }
.overview-pane :deep(td) { overflow-wrap: anywhere; white-space: pre-wrap; }
.overview-pane :deep(.el-tag) { margin-left: 6px; }
.narrow-version-label { display: none; }
@media (max-width: 640px) {
  .is-comparing .pane-grid { grid-template-columns: minmax(0, 1fr); }
  .narrow-version-label { display: block; font-size: 12px; color: var(--tianshu-text-secondary); margin: 0 0 6px; }
  .overview-pane :deep(.el-descriptions__label) { width: auto; }
}
</style>
