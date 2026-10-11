<template>
  <div class="condition-range-input">
    <div class="range-bound">
      <span>下限（包含）</span>
      <el-input
        :model-value="parts[0] || ''"
        :placeholder="dateType ? '如 2026-01-01' : '填写下限'"
        aria-label="区间下限（包含）"
        data-testid="condition-range-lower"
        @update:model-value="setBound(0, $event)"
      />
    </div>
    <div class="range-bound">
      <span>上限（包含）</span>
      <el-input
        :model-value="parts[1] || ''"
        :placeholder="dateType ? '如 2026-12-31' : '填写上限'"
        aria-label="区间上限（包含）"
        data-testid="condition-range-upper"
        @update:model-value="setBound(1, $event)"
      />
    </div>
    <span v-if="error" class="range-error" role="alert">{{ error }}</span>
  </div>
</template>

<script>
import { requireRangeBounds } from '@/utils/conditionRange'

export default {
  name: 'ConditionRangeInput',
  props: {
    value: { type: Object, default: null },
    valueType: { type: String, default: 'NUMBER' },
  },
  emits: ['input'],
  computed: {
    parts() { return this.value?.kind === 'LITERAL' ? String(this.value.value ?? '').split(',') : [] },
    dateType() { return ['DATE', 'DATETIME', 'TIMESTAMP', 'LOCALDATE', 'LOCALDATETIME'].includes(this.valueType) },
    error() {
      try {
        requireRangeBounds(this.value?.value, this.valueType, !this.value || this.value.kind === 'LITERAL' ? 'CONST' : 'VAR')
        return ''
      } catch (error) { return error.message }
    },
  },
  methods: {
    setBound(index, text) {
      const parts = [this.parts[0] || '', this.parts[1] || '']
      parts[index] = text
      this.$emit('input', {
        ...(this.value?.kind === 'LITERAL' ? this.value : {}),
        kind: 'LITERAL', valueType: this.valueType || 'NUMBER', value: parts.join(','),
      })
    },
  },
}
</script>

<style scoped>
.condition-range-input { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 8px; width: 100%; min-width: 0; }
.range-bound { min-width: 0; }
.range-bound > span { display: block; margin-bottom: 4px; color: var(--tianshu-text-secondary); font-size: 12px; }
.range-error { grid-column: 1 / -1; color: var(--tianshu-danger-text); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
</style>
