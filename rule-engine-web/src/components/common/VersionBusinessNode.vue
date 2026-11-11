<template>
  <condition-group-editor v-if="node.group" :group="node.group" read-only compact />
  <div v-else-if="assignment" class="assignment-summary">
      <span class="config-label">赋值</span>
      <operand-value-display :operand="assignment.target" compact />
      <span>＝</span>
      <operand-value-display :operand="assignment.value" compact />
  </div>
  <div v-else-if="columns.length" class="config-table">
    <strong v-if="!bare" class="config-group-title">{{ node.title }}</strong>
    <el-table :data="node.children" size="small">
      <el-table-column v-for="col in columns" :key="col.key" :label="col.title" :min-width="120">
        <template #default="{ row }">
          <version-business-node v-if="cell(row, col.key)" :node="cell(row, col.key)" bare />
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
  <section v-else-if="node.children?.length" class="config-group" :class="{ 'config-group--bare': bare }">
    <button v-if="!bare" type="button" class="config-group-title" :aria-expanded="!folded" @click="folded = !folded">
      <span aria-hidden="true">{{ folded ? '▸' : '▾' }}</span>{{ node.title }}
      <span v-if="node.text" class="config-label">{{ node.text }}</span>
    </button>
    <div v-if="!folded || bare" class="config-group-content">
      <version-business-node v-for="(child, index) in visibleChildren" :key="index" :node="child" />
    </div>
  </section>
  <div v-else class="config-value" :class="{ 'config-value--bare': bare }">
    <span v-if="!bare" class="config-label">{{ node.title }}</span>
    <operand-value-display v-if="node.operand" :operand="node.operand" compact />
    <pre v-else-if="code" class="config-code">{{ node.text }}</pre>
    <span v-else>{{ node.text }}</span>
  </div>
</template>
<script>
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import OperandValueDisplay from './OperandValueDisplay.vue'

export default {
  name: 'VersionBusinessNode',
  components: { ConditionGroupEditor, OperandValueDisplay },
  props: { node: { type: Object, required: true }, bare: Boolean },
  data: () => ({ folded: false }),
  computed: {
    code() { return ['sql', 'sqlTemplate', 'querySql', 'script', 'implScript', 'requestScript', 'responseScript'].includes(this.node.key) },
    visibleChildren() {
      return this.node.children.filter(child => !(this.node.kind === 'action' && child.key === 'actionType' && this.node.title.startsWith(child.text)) && !(child.key === 'ruleName' && child.text === this.node.title))
    },
    columns() {
      const records = this.node.children
      if (!this.node.list || !records?.length || !records.every(row => row.children?.length && row.children.every(field => field.key))) return []
      const columns = new Map()
      for (const row of records) {
        for (const field of row.children) columns.set(field.key, { key: field.key, title: field.title })
      }
      return [...columns.values()]
    },
    assignment() {
      if (this.node.kind !== 'action') return null
      const items = this.node.children
      if (items.length !== 3 || items[0].text !== '赋值') return null
      return { target: this.cell(this.node, 'targetOperand')?.operand, value: this.cell(this.node, 'valueOperand')?.operand }
    },
  },
  watch: { node() { this.folded = false } },
  methods: { cell(row, key) { return row.children.find(field => field.key === key) } },
}
</script>
<style scoped>
.config-group { margin: 12px 0; border-top: 1px solid var(--tianshu-border-subtle); padding-top: 12px; min-width: 0; }
.config-group:first-child { margin-top: 0; border-top: 0; padding-top: 0; }
.config-group--bare { border: 0; padding: 0; }
.config-group-title { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; margin: 0 0 8px; padding: 0; border: 0; background: none; color: var(--tianshu-text-primary); font: inherit; font-size: 13px; font-weight: 600; text-align: left; cursor: pointer; }
.config-group-content { display: grid; gap: 8px; }
.config-group-content > .config-group { margin: 0; padding: 8px 0 0 12px; border-top: 0; border-left: 2px solid var(--tianshu-border-subtle); }
.config-value { display: grid; grid-template-columns: minmax(80px, 24%) minmax(0, 1fr); align-items: baseline; gap: 4px 12px; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; white-space: pre-wrap; }
.config-value--bare { display: block; }
.config-label { color: var(--tianshu-text-secondary); font-weight: 400; }
.assignment-summary { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; font-size: 13px; line-height: 1.6; }
.config-code { min-width: 0; margin: 0; padding: 8px 12px; background: var(--tianshu-bg-soft); border-radius: 4px; white-space: pre-wrap; overflow-wrap: anywhere; }
.config-table { min-width: 0; max-width: 100%; }
</style>
