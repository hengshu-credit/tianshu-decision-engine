<template>
  <span class="content-expression">
    <template v-if="value?.kind === 'OPERATION'">(<template v-for="(term, index) in value.terms" :key="index"><span v-if="index" class="content-expression__operator"> {{ operationLabel(term.operator) }} </span><content-expression v-if="!noRightOperand(term.operator)" :value="term.operand" :options="options" /></template>)</template>
    <template v-else-if="value?.kind === 'FUNCTION'"><content-reference :value="value" :options="options" />(<template v-for="(arg, index) in value.args" :key="index"><span v-if="index">, </span><content-expression :value="arg" :options="options" /></template>)</template>
    <template v-else-if="value?.kind === 'ARRAY'">[<template v-for="(item, index) in value.items" :key="index"><span v-if="index">, </span><content-expression :value="item" :options="options" /></template>]</template>
    <content-reference v-else :value="value" :options="options" wrap />
  </span>
</template>
<script>
import ContentReference from './ContentReference.vue'
import { operationLabel, noRightOperand } from '@/utils/contentOperation'
export default { name: 'ContentExpression', components: { ContentReference }, props: { value: { type: Object, default: null }, options: { type: Array, default: () => [] } }, methods: { operationLabel, noRightOperand } }
</script>
<style scoped>.content-expression { line-height:24px; overflow-wrap:anywhere; } .content-expression__operator { color:var(--el-color-primary); }</style>
