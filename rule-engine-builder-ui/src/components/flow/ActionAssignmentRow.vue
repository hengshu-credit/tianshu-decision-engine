<template>
  <div class="inline-row assignment-row">
    <operand-picker
      :value="action.targetOperand"
      :vars="actionEditor.vars"
      :selected-vars="actionEditor.selectedVars"
      :allowed-kinds="actionEditor.writeKinds"
      writable-only
      placeholder="选择目标字段"
      size="mini"
      @input="value => actionEditor.setOperand(action, 'targetOperand', value)"
    />
    <span class="eq">=</span>
    <operand-picker
      :value="action.valueOperand"
      :vars="actionEditor.vars"
      :functions="actionEditor.functions"
      :selected-vars="actionEditor.selectedVars"
      :allowed-kinds="actionEditor.valueKinds"
      placeholder="选择值或字段"
      size="mini"
      @input="value => actionEditor.setOperand(action, 'valueOperand', value)"
    />
  </div>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'

export default {
  name: 'ActionAssignmentRow',
  components: { OperandPicker },
  inject: ['actionEditor'],
  props: { action: { type: Object, required: true } }
}
</script>

<style lang="scss" scoped>
.inline-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.inline-row ::v-deep .var-picker-wrap { flex: 1 1 0; min-width: 0; width: auto !important; }
.eq { flex-shrink: 0; color: #909399; }
</style>
