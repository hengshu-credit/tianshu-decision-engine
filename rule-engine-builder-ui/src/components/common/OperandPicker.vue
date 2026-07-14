<template>
  <div class="operand-picker">
    <var-picker
      v-bind="$attrs"
      :value="value"
      :vars="vars"
      :functions="functions"
      :allowed-kinds="allowedKinds"
      :expected-type="expectedType"
      :writable-only="writableOnly"
      :operand-mode="true"
      v-on="forwardedListeners"
      @input="onQuickInput"
      @select="onQuickSelect"
    />
    <el-tooltip v-if="showEditorButton" content="配置组合表达式：可组合字段、方法、阈值和运算符" placement="top">
      <button type="button" class="expression-button" aria-label="配置组合表达式" @click="openEditor">fx</button>
    </el-tooltip>
    <expression-editor-dialog
      :visible.sync="editorVisible"
      :value="editorValue"
      :vars="vars"
      :functions="functions"
      :list-options="listOptions"
      :allowed-kinds="allowedKinds"
      :context="editorContext"
      :expected-type="expectedType"
      :title="editorTitle"
      @apply="onEditorApply"
    />
  </div>
</template>

<script>
import VarPicker from './VarPicker.vue'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'
import { cloneOperand, VALUE_OPERAND_KINDS } from '@/utils/operand'
import { createFunctionTemplate } from '@/components/expression/expressionTree'

export default {
  name: 'OperandPicker',
  inheritAttrs: false,
  components: { ExpressionEditorDialog, VarPicker },
  props: {
    value: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => VALUE_OPERAND_KINDS.slice() },
    expectedType: { type: String, default: '' },
    context: { type: String, default: '' },
    writableOnly: { type: Boolean, default: false },
    editorTitle: { type: String, default: '配置表达式' }
  },
  data() {
    return {
      editorVisible: false,
      editorValue: null,
      suppressQuickSelect: false
    }
  },
  computed: {
    forwardedListeners() {
      const listeners = { ...this.$listeners }
      delete listeners.input
      delete listeners.select
      return listeners
    },
    showEditorButton() {
      if (this.writableOnly) return false
      return this.allowedKinds.some(kind => ['FUNCTION', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY', 'LIST_QUERY'].includes(kind))
    },
    editorContext() {
      if (this.context) return this.context
      return this.writableOnly ? 'WRITE_TARGET' : 'READ_EXPRESSION'
    }
  },
  methods: {
    onQuickInput(operand) {
      if (operand && operand.kind === 'FUNCTION') {
        const template = createFunctionTemplate(this.findFunction(operand) || operand)
        if (template.args.length) {
          this.suppressQuickSelect = true
          this.editorValue = template
          this.editorVisible = true
          return
        }
      }
      this.$emit('input', operand)
    },
    onQuickSelect(operand) {
      if (this.suppressQuickSelect) {
        this.suppressQuickSelect = false
        return
      }
      this.$emit('select', operand)
    },
    openEditor() {
      this.editorValue = cloneOperand(this.value)
      this.editorVisible = true
    },
    onEditorApply(operand) {
      const value = cloneOperand(operand)
      this.$emit('input', value)
      this.$emit('select', value)
      this.editorVisible = false
    },
    findFunction(operand) {
      return this.functions.find(fn => {
        const id = fn.functionId != null ? fn.functionId : fn.id
        const code = fn.functionCode || fn.funcCode || fn.functionName || fn.funcName || fn.name || ''
        return (operand.functionId != null && String(id) === String(operand.functionId)) || code === operand.functionCode
      }) || null
    }
  }
}
</script>

<style scoped>
.operand-picker { display: flex; min-width: 0; align-items: center; gap: 5px; width: 100%; }
.operand-picker > .var-picker-wrap { flex: 1; min-width: 0; }
.expression-button { flex: none; width: 30px; height: 30px; padding: 0; border: 1px solid #cbd6e4; border-radius: 5px; background: #fff; color: #2878ff; font-family: Georgia, serif; font-size: 13px; font-style: italic; cursor: pointer; }
.expression-button:hover { border-color: #2878ff; background: #edf5ff; }
</style>
