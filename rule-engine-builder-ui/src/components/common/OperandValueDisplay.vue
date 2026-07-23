<template>
  <span v-if="operand" class="operand-value-display">
    <span
      class="operand-value-kind"
      :class="'operand-value-kind--' + meta.tone"
      >{{ meta.label }}</span
    >
    <span class="operand-value-text">{{ text || emptyText }}</span>
  </span>
  <span v-else class="operand-value-empty">{{ emptyText }}</span>
</template>

<script>
import { operandDisplay, operandKindMeta } from '@/utils/operand'

export default {
  name: 'OperandValueDisplay',
  props: {
    operand: { type: Object, default: null },
    emptyText: { type: String, default: '未配置' },
  },
  computed: {
    meta() {
      return operandKindMeta(this.operand)
    },
    text() {
      return operandDisplay(this.operand)
    },
  },
}
</script>

<style lang="scss" scoped>
.operand-value-display {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}
.operand-value-kind {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 3px;
  color: #fff;
  font-size: 11px;
  background: #909399;
}
.operand-value-kind--literal {
  background: #e6a23c;
}
.operand-value-kind--path {
  background: #607d8b;
}
.operand-value-kind--path-resolved {
  background: #546e7a;
}
.operand-value-kind--variable {
  background: var(--el-color-primary);
}
.operand-value-kind--constant {
  background: #9c6ade;
}
.operand-value-kind--object {
  background: #00a870;
}
.operand-value-kind--model {
  background: #f56c6c;
}
.operand-value-kind--function {
  background: #13c2c2;
}
.operand-value-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.operand-value-empty {
  color: #909399;
}
</style>
