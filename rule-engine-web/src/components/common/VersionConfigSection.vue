<template>
  <section class="business-preview-card">
    <div v-if="versionLabel" class="narrow-version-label">{{ versionLabel }}</div>
    <p v-if="!section" class="missing">{{ absentLabel }}</p>
    <template v-else>
      <div v-if="section.matrix" class="matrix-scroll" tabindex="0">
        <table class="result-matrix">
          <thead><tr><th scope="col">行条件 / 列条件</th><th v-for="(label, i) in columnLabels" :key="i" scope="col">{{ label }}</th></tr></thead>
          <tbody>
            <tr v-for="(label, i) in rowLabels" :key="i">
              <th scope="row">{{ label }}</th>
              <td v-for="(_, j) in columnLabels" :key="j">
                <operand-value-display :operand="section.matrix.cells[i]?.[j]" empty-text="—" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <score-threshold-editor v-else-if="section.thresholds?.length" :thresholds="section.thresholds" read-only compact>
        <template #result="{ threshold }">
          <operand-value-display v-if="threshold.resultOperand" :operand="threshold.resultOperand" />
        </template>
      </score-threshold-editor>
      <source-summary-card v-else-if="section.sourceTitle" :title="section.sourceTitle" :rows="sourceRows">
        <template #value="{ item }"><version-business-node :node="item.node" bare /></template>
      </source-summary-card>
      <template v-else-if="section.graph">
        <rule-snapshot-canvas :graph="section.graph" />
        <details v-if="section.nodes.length" class="graph-details">
          <summary>节点配置（{{ section.nodes.length }}）</summary>
          <version-business-node v-for="(item, i) in section.nodes" :key="i" :node="item" />
        </details>
      </template>
      <template v-else-if="section.nodes.length">
        <template v-for="(item, index) in section.nodes" :key="index">
          <monaco-editor v-if="item.key === 'script' && section.key === 'rule:script'" :value="item.text"
            language="ql" read-only reference-catalog-mode="FROZEN" height="220px" :options="{ minimap: { enabled: false }, wordWrap: 'on' }" />
          <version-business-node v-else :node="item" :bare="item.kind === 'settings' || item.title === section.title" />
        </template>
      </template>
      <p v-else class="missing">暂无{{ section.title }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed, defineAsyncComponent } from 'vue'
import ScoreThresholdEditor from '@/components/decision/ScoreThresholdEditor.vue'
import OperandValueDisplay from './OperandValueDisplay.vue'
import VersionBusinessNode from './VersionBusinessNode.vue'
import SourceSummaryCard from './SourceSummaryCard.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
const RuleSnapshotCanvas = defineAsyncComponent(() => import('@/components/flow/RuleSnapshotCanvas.vue'))

const props = defineProps({
  section: { type: Object, default: null },
  absentLabel: { type: String, default: '' },
  versionLabel: { type: String, default: '' },
})
const sourceRows = computed(() => props.section.nodes[0].children.map(node => ({ key: node.key, label: node.title, node })))
const columnLabels = computed(() => {
  const { columns, cells } = props.section.matrix
  const count = cells.reduce((count, row) => Math.max(count, row.length), columns.length)
  return Array.from({ length: count }, (_, index) => columns[index] || `第 ${index + 1} 列`)
})
const rowLabels = computed(() => {
  const { rows, cells } = props.section.matrix
  return Array.from({ length: Math.max(rows.length, cells.length) }, (_, index) => rows[index] || `第 ${index + 1} 行`)
})
</script>
<style scoped>
.business-preview-card { container: version-panel / inline-size; min-width: 0; color: var(--tianshu-text-primary); overflow-wrap: anywhere; }
.business-preview-card > .snapshot-branch { overflow-x: auto; padding-block: 4px; }
.business-preview-card > .source-summary-card { margin: 0; }
.matrix-scroll { max-width: 100%; overflow: auto; }
.result-matrix { width: 100%; border-collapse: collapse; font-size: 13px; }
.result-matrix :is(th, td) { padding: 8px; border: 1px solid var(--tianshu-border); text-align: left; }
.result-matrix th { background: var(--tianshu-bg-soft); font-weight: 600; }
.missing, .graph-details summary, .narrow-version-label { font-size: 12px; color: var(--tianshu-text-secondary); }
.graph-details summary { padding: 10px 0; cursor: pointer; }
.narrow-version-label { display: none; }
@media (max-width: 640px) { .narrow-version-label { display: block; margin-bottom: 8px; font-weight: 600; } }
</style>
