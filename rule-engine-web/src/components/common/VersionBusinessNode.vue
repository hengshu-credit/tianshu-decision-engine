<template>
  <condition-group-editor v-if="node.group" :group="node.group" read-only />
  <trace-branch v-else-if="assignment" snapshot>
    <template #heading>
      <strong>赋值</strong>
      <operand-value-display :operand="assignment.target" />
      <span>＝</span>
      <operand-value-display :operand="assignment.value" />
    </template>
  </trace-branch>
  <div v-else-if="columns.length" class="config-table">
    <el-table :data="node.children" size="small" border>
      <el-table-column v-for="col in columns" :key="col.key" :label="col.title" :min-width="100">
        <template #default="{ row }">
          <version-business-node v-if="cell(row, col.key)" :node="cell(row, col.key)" bare />
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
  <trace-branch v-else-if="node.children?.length" snapshot has-children :show-heading="!bare">
    <template #heading><strong>{{ node.title }}</strong><span v-if="node.text">{{ node.text }}</span></template>
    <version-business-node v-for="(child, index) in node.children" :key="index" :node="child" tree />
  </trace-branch>
  <trace-branch v-else-if="tree" snapshot>
    <template #heading>
      <span class="config-label">{{ node.title }}</span>
      <operand-value-display v-if="node.operand" :operand="node.operand" />
      <strong v-else>{{ node.text }}</strong>
    </template>
  </trace-branch>
  <div v-else class="config-value" :class="{ 'config-value--bare': bare }">
    <span v-if="!bare" class="config-label">{{ node.title }}</span>
    <operand-value-display v-if="node.operand" :operand="node.operand" />
    <pre v-else-if="code" class="config-code">{{ node.text }}</pre>
    <span v-else>{{ node.text }}</span>
  </div>
</template>
<script>
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import OperandValueDisplay from './OperandValueDisplay.vue'
import TraceBranch from './TraceBranch.vue'

export default {
  name: 'VersionBusinessNode',
  components: { ConditionGroupEditor, OperandValueDisplay, TraceBranch },
  props: { node: { type: Object, required: true }, bare: Boolean, tree: Boolean },
  computed: {
    code() { return ['sql', 'sqlTemplate', 'querySql', 'script', 'implScript', 'requestScript', 'responseScript'].includes(this.node.key) },
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
  methods: { cell(row, key) { return row.children.find(field => field.key === key) } },
}
</script>
<style scoped>
.config-value { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 12px; padding: 4px 0; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; white-space: pre-wrap; }
.config-value--bare { display: block; }
.config-label { color: var(--tianshu-text-secondary); }
.config-label { min-width: 6em; }
.config-code { flex: 1 1 100%; min-width: 0; margin: 0; padding: 10px 12px; background: var(--tianshu-bg-soft); border: 1px solid var(--tianshu-border-subtle); border-radius: 4px; white-space: pre-wrap; overflow-wrap: anywhere; }
.config-table { min-width: 0; max-width: 100%; }
</style>
