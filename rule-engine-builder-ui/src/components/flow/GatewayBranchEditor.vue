<template>
  <div class="gateway-branch" :data-edge-id="edge.id">
    <div class="branch-header">
      <button type="button" class="branch-toggle" :aria-expanded="!collapsed" @click="collapsed = !collapsed">
        <span class="edge-idx">{{ index + 1 }}</span>
        <span class="branch-title">{{ form.conditionName || '未命名分支' }} → {{ targetName }}</span>
        <span class="edge-priority">P{{ priority }}</span>
        <span class="branch-toggle-label">{{ collapsed ? '展开' : '收起' }}</span>
      </button>
      <el-button link size="small" class="edge-priority-up" :disabled="index === 0" @click="$emit('move', -1)">上移</el-button>
      <el-button link size="small" class="edge-priority-down" :disabled="index === count - 1" @click="$emit('move', 1)">下移</el-button>
    </div>
    <div class="branch-condition-summary">
      <span>分流条件：</span><code>{{ form.conditionExpr || '默认分支（else）' }}</code>
    </div>
    <div v-show="!collapsed" class="branch-editor">
      <edge-properties-editor
        :model-value="form"
        :condition-root="conditionRoot"
        :mode="mode"
        :vars="vars"
        :functions="functions"
        :list-options="listOptions"
        :selected-vars="selectedVars"
        :get-var-options-fn="getVarOptionsFn"
        @update:model-value="updateForm"
        @update:mode="changeMode"
        @condition-change="persistCondition"
        @generate="persistCondition(true)"
      />
    </div>
  </div>
</template>

<script>
import EdgePropertiesEditor from './EdgePropertiesEditor.vue'
import { compileConditionTreeExpression } from '@/utils/decisionConditionTree'

export default {
  name: 'GatewayBranchEditor',
  components: { EdgePropertiesEditor },
  props: {
    edge: { type: Object, required: true },
    index: { type: Number, required: true },
    count: { type: Number, required: true },
    priority: { type: Number, required: true },
    targetName: { type: String, default: '' },
    parseConditionConfig: { type: Function, required: true },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    getVarOptionsFn: { type: Function, default: null },
  },
  emits: ['change', 'move'],
  data() {
    return { collapsed: false, form: {}, conditionRoot: null, mode: 'visual' }
  },
  watch: {
    'edge.properties': {
      immediate: true,
      deep: true,
      handler(properties) {
        const next = { conditionName: '', conditionExpr: '', edgeLineType: '', ...properties, priority: this.priority }
        const conditionChanged = !this.conditionRoot || next.conditionExpr !== this.form.conditionExpr ||
          JSON.stringify(next.conditionConfig) !== JSON.stringify(this.form.conditionConfig)
        this.form = JSON.parse(JSON.stringify(next))
        if (conditionChanged) {
          this.conditionRoot = this.parseConditionConfig(next.conditionConfig, next.conditionExpr, next)
          this.mode = next.conditionExpr && this.conditionRoot.type === 'group' && !this.conditionRoot.children?.length ? 'script' : 'visual'
        }
      },
    },
    priority(value) { this.form.priority = value },
  },
  methods: {
    updateForm(form) {
      this.form = form
      this.$emit('change', form)
    },
    changeMode(mode) {
      if (mode === 'visual') this.conditionRoot = this.parseConditionConfig(this.form.conditionConfig, this.form.conditionExpr, this.form)
      this.mode = mode
    },
    persistCondition(generate = false) {
      const empty = this.conditionRoot.type === 'group' && !this.conditionRoot.children?.length
      const expr = empty ? '' : compileConditionTreeExpression(this.conditionRoot)
      this.updateForm({
        ...this.form,
        conditionExpr: expr,
        conditionConfig: empty ? null : JSON.parse(JSON.stringify(this.conditionRoot)),
        conditionName: generate && !this.form.conditionName ? expr : this.form.conditionName,
        leftVarId: null, leftRefType: null, rightVarId: null, rightRefType: null,
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.gateway-branch { min-width: 0; margin-bottom: 10px; border: 1px solid var(--tianshu-border-subtle); border-radius: 4px; }
.branch-header { display: flex; align-items: center; gap: 6px; padding: 6px 10px; background: var(--tianshu-bg-muted); }
.branch-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  padding: 4px 0;
  border: none;
  background: transparent;
  color: var(--tianshu-text-primary);
  text-align: left;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  &:hover { color: var(--el-color-primary); }
  &:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
}
.branch-title { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.edge-idx { flex-shrink: 0; padding: 1px 6px; color: var(--tianshu-brand-foreground); background: var(--el-color-primary); border-radius: 3px; font-weight: bold; }
.edge-priority, .branch-toggle-label { flex-shrink: 0; color: var(--tianshu-text-tertiary); font-size: 11px; }
.branch-header :deep(.el-button) { margin: 0; padding: 0 2px; }
.branch-condition-summary { padding: 8px 10px; font-size: 12px; line-height: 1.6; color: var(--tianshu-text-secondary); overflow-wrap: anywhere; }
.branch-condition-summary code { font-family: 'Consolas', monospace; color: var(--el-color-primary); white-space: pre-wrap; }
.branch-editor { padding: 10px; border-top: 1px solid var(--tianshu-border-subtle); }
</style>
