<template>
  <span class="match-content">
      <content-reference v-if="operand" :value="literal ? { ...operand, value: actualText } : operand" mode="trace" />
      <span v-else>来源未记录</span>
      <span v-if="!literal" class="match-content-equals" aria-hidden="true">=</span>
      <code v-if="!literal" class="list-match-value" :title="`实际值：${actualText}`" :aria-label="`实际值：${actualText}`">{{ actualText }}</code>
  </span>
</template>

<script>
import ContentReference from './ContentReference.vue'

export default {
  name: 'ListMatchContent',
  components: { ContentReference },
  props: {
    operand: { type: Object, default: null },
    actualValue: { default: undefined },
  },
  computed: {
    literal() { return this.operand?.kind === 'LITERAL' },
    actualText() {
      if (this.actualValue === null) return '空值（null）'
      if (this.actualValue === undefined) return '实际值未记录'
      if (this.actualValue === '') return '空字符串'
      return typeof this.actualValue === 'object' ? JSON.stringify(this.actualValue) : String(this.actualValue)
    },
  },
}
</script>

<style scoped>
.match-content { display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; font-size: 13px; white-space: nowrap; }
.list-match-value { padding: 2px 8px; border: 1px solid var(--tianshu-border-subtle); border-radius: 4px; background: var(--tianshu-bg-muted); white-space: nowrap; color: var(--tianshu-text-primary); font-size: 13px; }
</style>
