<template>
  <div class="api-value-editor">
    <operand-picker :value="value" :vars="pickerVars" :functions="pureFunctions" :expected-type="expectedType" @input="update" />
    <div v-if="error" class="api-binding-error" role="alert">{{ error }}</div>
  </div>
</template>
<script>
import OperandPicker from './OperandPicker.vue'
import { API_MODULE_FIELDS } from '@/utils/apiExecution'
import { apiProtocolFields, isAvailableApiPath } from '@/utils/apiContext'
import { resolvePathOperand } from '@/utils/operand'
export default {
  components: { OperandPicker },
  props: { value: { type: Object, default: null }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, modules: { type: Array, default: () => API_MODULE_FIELDS }, expectedType: { type: String, default: '' }, contextLabel: { type: String, default: '当前外数 API · HTTP 上下文' } },
  emits: ['update:value'],
  data: () => ({ error: '' }),
  computed: {
    pureFunctions() { return this.functions.filter(item => ['AggregateBuiltinFunctions', 'DecisionBuiltinFunctions', 'DigestBuiltinFunctions'].some(name => item.implClass?.endsWith('.' + name))) },
    pickerVars() { return [...this.vars, ...apiProtocolFields(this.modules, this.contextLabel)] },
  },
  methods: {
    update(value) {
      this.error = ''
      const normalize = node => {
        if (!node || typeof node !== 'object') return node
        if (Array.isArray(node)) return node.map(normalize)
        if (node.kind === 'LITERAL') return node
        if (node.refType === 'API_CONTEXT' || node.kind === 'PATH' && !node.refId) {
          const path = node.value || node.code
          if (isAvailableApiPath(path, this.modules)) return { kind: 'PATH', value: path, protocolPath: true, resolved: true }
          if (node.refType === 'API_CONTEXT' || node.protocolPath) throw new Error('当前配置阶段尚不能使用此路径，请选择当前可用的模块或步骤')
          const result = resolvePathOperand(node, this.vars)
          if (!result.operand?.refId) throw new Error('该路径无法对应到当前可用字段，请选择字段或先导入响应样例')
          return result.operand
        }
        if (node.kind === 'FUNCTION') return { ...node, args: (node.args || []).map(normalize) }
        if (node.kind === 'OPERATION') return { ...node, terms: (node.terms || []).map(term => ({ ...term, operand: normalize(term.operand) })) }
        if (node.kind === 'ACCESS') return { ...node, target: normalize(node.target), accessor: normalize(node.accessor) }
        if (node.kind === 'CAST') return { ...node, operand: normalize(node.operand) }
        if (node.kind === 'ARRAY') return { ...node, items: (node.items || []).map(normalize) }
        return node
      }
      try { this.$emit('update:value', normalize(value)) }
      catch (error) { this.error = error.message }
    },
  },
}
</script>
<style scoped>
.api-value-editor { display: grid; gap: 6px; min-width: 220px; }
.api-binding-error { color: var(--el-color-danger); font-size: 12px; }
.module-hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
