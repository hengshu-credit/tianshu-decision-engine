<template>
  <div class="api-value-editor">
    <operand-picker :value="value" :vars="pickerVars" :functions="pureFunctions" @input="update" />
    <el-select v-if="modules.length" v-model="modulePath" clearable filterable :filter-method="filterModules" placeholder="外数模块字段" @change="selectModule">
      <el-option v-for="item in visibleModules" :key="item.value" :label="item.label === item.value ? item.value : item.label + ' · ' + item.value" :value="item.value" />
    </el-select>
    <small v-if="modules.length > 100" class="module-hint">共 {{ modules.length }} 个路径，输入字段名搜索，每次显示最多 100 项</small>
    <div v-if="error" class="api-binding-error" role="alert">{{ error }}</div>
  </div>
</template>
<script>
import OperandPicker from './OperandPicker.vue'
import { API_MODULE_FIELDS, filterApiPaths } from '@/utils/apiExecution'
import { resolvePathOperand } from '@/utils/operand'
export default {
  components: { OperandPicker },
  props: { value: { type: Object, default: null }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, modules: { type: Array, default: () => API_MODULE_FIELDS } },
  emits: ['update:value'],
  data: () => ({ modulePath: '', moduleQuery: '', error: '' }),
  computed: {
    visibleModules() { return filterApiPaths(this.modules, this.moduleQuery, this.modulePath) },
    pureFunctions() { return this.functions.filter(item => ['AggregateBuiltinFunctions', 'DecisionBuiltinFunctions', 'DigestBuiltinFunctions'].some(name => item.implClass?.endsWith('.' + name))) },
    pickerVars() { return [...this.vars, ...this.modules.map((item, index) => ({ _varId: index + 1, _refType: 'API_CONTEXT', varCode: item.value, varLabel: item.label, varType: item.type || 'OBJECT', sourceType: 'dataObject', objectCode: item.value.split('.')[0], objectLabel: '外数调用模块' }))] },
  },
  methods: {
    filterModules(query) { this.moduleQuery = query },
    selectModule(path) {
      if (!path) return
      this.error = ''
      this.$emit('update:value', { kind: 'PATH', value: path, protocolPath: true, resolved: true })
    },
    update(value) {
      this.error = ''
      const normalize = node => {
        if (!node || typeof node !== 'object') return node
        if (Array.isArray(node)) return node.map(normalize)
        if (node.refType === 'API_CONTEXT') return { kind: 'PATH', value: node.value || node.code, protocolPath: true, resolved: true }
        return Object.fromEntries(Object.entries(node).map(([key, child]) => [key, normalize(child)]))
      }
      value = normalize(value)
      if (value?.kind === 'PATH' && !value.refId) {
        if (this.modules.some(item => item.value === value.value || value.value?.startsWith(item.value + '.') || value.value?.startsWith(item.value + '['))) value = { ...value, protocolPath: true, resolved: true }
        else if (!value.protocolPath) {
          const result = resolvePathOperand(value, this.vars)
          value = result.operand
          if (!value?.refId) { this.error = '该路径无法对应到可用字段，请选择字段或先导入响应样例'; return }
        }
      }
      this.$emit('update:value', value)
    },
  },
}
</script>
<style scoped>
.api-value-editor { display: grid; gap: 6px; min-width: 220px; }
.api-binding-error { color: var(--el-color-danger); font-size: 12px; }
.module-hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
