<template>
  <div class="inline-row">
    <span class="mini-label">结果</span>
    <operand-picker
      :value="block.targetOperand"
      :vars="actionEditor.vars"
      :selected-vars="actionEditor.selectedVars"
      :allowed-kinds="actionEditor.writeKinds"
      writable-only
      placeholder="选择结果字段"
      size="small"
      @input="(value) => actionEditor.setOperand(block, 'targetOperand', value)"
    />
  </div>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'

export default {
  name: 'ActionAssignmentTarget',
  components: { OperandPicker },
  inject: ['actionEditor'],
  props: { block: { type: Object, required: true } },
}
</script>

<style lang="scss" scoped>
.inline-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.inline-row :deep(.var-picker-wrap) {
  flex: 1 1 0;
  min-width: 0;
  width: auto !important;
}
.mini-label {
  flex-shrink: 0;
  color: #606266;
  font-size: 12px;
}
</style>
