<template>
  <div class="history-filters">
    <div v-for="(filter, index) in modelValue" :key="index" class="filter-row">
      <operand-picker :value="filter.field" :vars="vars" :allowed-kinds="['REFERENCE']" placeholder="历史属性字段" @input="patch(index, 'field', $event)" />
      <el-select :model-value="filter.operator" aria-label="筛选操作" @update:model-value="patch(index, 'operator', $event)">
        <el-option v-for="option in operators" :key="option[0]" :value="option[0]" :label="option[1]" />
      </el-select>
      <operand-picker v-if="!['IS_NULL', 'NOT_NULL'].includes(filter.operator)" :value="filter.value" :vars="vars" :functions="functions" :allowed-kinds="operandKinds" placeholder="阈值 / 本次请求字段" @input="patch(index, 'value', $event)" />
      <el-button text type="danger" @click="remove(index)">移除</el-button>
    </div>
    <el-button text type="primary" @click="$emit('update:modelValue', [...modelValue, { field: null, operator: 'EQ', value: null }])">添加筛选条件（全部满足）</el-button>
  </div>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'
import { DERIVED_OPERAND_KINDS } from '@/utils/derivedVariable'

export default {
  name: 'HistoryFilters',
  components: { OperandPicker },
  props: { modelValue: { type: Array, default: () => [] }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] } },
  emits: ['update:modelValue'],
  data() {
    return { operandKinds: DERIVED_OPERAND_KINDS, operators: [['EQ', '等于'], ['NE', '不等于'], ['GT', '大于'], ['GE', '大于等于'], ['LT', '小于'], ['LE', '小于等于'], ['IS_NULL', '为空'], ['NOT_NULL', '不为空']] }
  },
  methods: {
    patch(index, key, value) { this.$emit('update:modelValue', this.modelValue.map((item, i) => i === index ? { ...item, [key]: value } : item)) },
    remove(index) { this.$emit('update:modelValue', this.modelValue.filter((item, i) => i !== index)) },
  },
}
</script>

<style scoped>
.filter-row { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0; }
.filter-row > .operand-picker { flex: 1; min-width: 180px; }
.filter-row > .el-select { width: 120px; }
</style>
