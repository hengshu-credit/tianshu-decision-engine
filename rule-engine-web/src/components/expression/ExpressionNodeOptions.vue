<template>
  <div v-if="['ACCESS', 'CAST', 'ARRAY', 'LIST_QUERY'].includes(node.kind)" class="expression-node-options" :class="{ 'expression-node-options--list': node.kind === 'LIST_QUERY' }" @click.stop @pointerdown.stop>
    <el-select v-if="node.kind === 'ACCESS'" :model-value="node.accessType" size="small" aria-label="取值方式" @update:model-value="$emit('patch', { accessType: $event })"><el-option label="字典 Key" value="KEY" /><el-option label="数组 Index" value="INDEX" /></el-select>
    <el-select v-else-if="node.kind === 'CAST'" :model-value="node.targetType" size="small" aria-label="转换类型" @update:model-value="$emit('patch', { targetType: $event, valueType: $event })"><el-option v-for="type in types" :key="type.value" :label="type.label" :value="type.value" /></el-select>
    <template v-else-if="node.kind === 'LIST_QUERY'">
      <el-select :model-value="node.listIds" size="small" multiple filterable aria-label="查询名单" placeholder="选择名单" @update:model-value="$emit('patch', { listIds: $event })"><el-option v-for="list in listOptions" :key="list.id" :label="list.listName || list.name || list.listCode" :value="list.id" /></el-select>
      <el-select :model-value="node.combinationMode" size="small" aria-label="字段与名单组合" placeholder="字段与名单组合" @update:model-value="$emit('patch', { combinationMode: $event })"><el-option v-for="item in combinations" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select :model-value="node.matchMode" size="small" aria-label="名单匹配方式" placeholder="匹配方式" @update:model-value="$emit('patch', { matchMode: $event })"><el-option v-for="item in matches" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select :model-value="node.itemTypes" size="small" multiple clearable aria-label="名单内容类型" placeholder="任意内容类型" @update:model-value="$emit('patch', { itemTypes: $event })"><el-option v-for="item in itemTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
    </template>
  </div>
</template>
<script>
import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, POSITIVE_LIST_MATCH_MODES } from '@/constants/listMatchModes'
export default {
  name: 'ExpressionNodeOptions',
  props: { node: { type: Object, required: true }, listOptions: { type: Array, default: () => [] } },
  emits: ['patch'],
  data: () => ({
    combinations: LIST_COMBINATION_MODES, itemTypes: LIST_ITEM_TYPES, matches: POSITIVE_LIST_MATCH_MODES,
    types: [{ label: '文本', value: 'STRING' }, { label: '数字', value: 'NUMBER' }, { label: '布尔', value: 'BOOLEAN' }, { label: '日期', value: 'DATE' }, { label: '日期时间', value: 'DATETIME' }, { label: '数组', value: 'LIST' }, { label: '字典', value: 'MAP' }],
  }),
}
</script>
<style scoped>
.expression-node-options { display: flex; flex-wrap: wrap; gap: 6px; margin-left: auto; min-width: 0; }
.expression-node-options > .el-select { width: 140px; max-width: 100%; }
.expression-node-options--list { flex-basis: 100%; margin: 4px 0 0; }
.expression-node-options--list > .el-select { flex: 1 1 180px; }
.expression-node-options :deep(.el-select__wrapper) { border-radius: 0; }
</style>
