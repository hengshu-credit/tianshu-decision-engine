<template>
  <span v-if="operand && compact" class="operand-value-compact" :title="description">
    <span v-for="part in compactParts" :key="part.key" :class="'operand-part--' + part.key">{{ part.text }}</span>
  </span>
  <content-reference v-else-if="operand" :value="operand" class="operand-value-display" />
  <span v-else class="operand-value-empty">{{ emptyText }}</span>
</template>
<script>
import ContentReference from './ContentReference.vue'
import { contentReferenceParts } from '@/utils/contentReference'
export default {
  name: 'OperandValueDisplay', components: { ContentReference },
  props: { operand: { type: Object, default: null }, emptyText: { type: String, default: '未配置' }, compact: Boolean },
  computed: {
    parts() { return contentReferenceParts(this.operand) },
    compactParts() {
      const name = this.parts.find(part => part.key === 'name')?.text
      const parts = this.parts.filter(part => !['source', 'type'].includes(part.key) && !(part.key === 'code' && part.text === name))
      if (this.operand?.refId != null && !parts.some(part => ['name', 'code'].includes(part.key))) parts.unshift({ key: 'name', text: `引用 #${this.operand.refId}` })
      return parts
    },
    description() { return [...this.parts.map(part => part.title), ...(this.operand?.refId != null ? [`${this.operand.refType || '引用'} #${this.operand.refId}`] : [])].join(' · ') },
  },
}
</script>
<style scoped>
.operand-value-compact { display: inline-flex; flex-wrap: wrap; align-items: baseline; gap: 4px 8px; max-width: 100%; overflow-wrap: anywhere; white-space: pre-wrap; }
.operand-part--name, .operand-part--value { font-weight: 500; color: var(--tianshu-text-primary); }
.operand-part--code, .operand-part--version { color: var(--tianshu-text-tertiary); font-size: 12px; }
</style>
