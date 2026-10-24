<template>
  <content-reference v-if="operand" :value="operand" class="operand-value-display" />
  <span v-else class="operand-value-empty">{{ emptyText }}</span>
</template>
<script>
import ContentReference from './ContentReference.vue'
export default { name: 'OperandValueDisplay', components: { ContentReference }, props: { operand: { type: Object, default: null }, emptyText: { type: String, default: '未配置' } } }
</script>
