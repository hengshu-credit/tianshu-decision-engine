<template>
  <div class="operand-picker">
    <div v-if="manualKind" class="operand-manual-editor" @click.stop>
      <el-select
        v-if="manualKind === 'LITERAL'"
        :model-value="manualOperand.valueType"
        :size="$attrs.size || 'small'"
        class="operand-manual-type"
        @update:model-value="patchManualOperand({ valueType: $event })"
      >
        <el-option
          v-for="type in valueTypes"
          :key="type.value"
          :label="type.label"
          :value="type.value"
        />
      </el-select>
      <el-input
        ref="manualInput"
        :model-value="manualOperand.value"
        :size="$attrs.size || 'small'"
        clearable
        :placeholder="manualKind === 'PATH' ? '请输入字段路径' : '请输入阈值'"
        @update:model-value="updateManualValue"
        @keyup.enter="resolveManualPath"
        @blur="resolveManualPath"
      />
      <el-tooltip content="返回字段选择" placement="top">
        <button
          type="button"
          class="manual-back-button"
          aria-label="返回字段选择"
          @mousedown.prevent
          @click="returnToPicker"
        >
          <el-icon><el-icon-collection /></el-icon>
        </button>
      </el-tooltip>
      <div v-if="manualPathCandidates.length" class="operand-path-candidates">
        <button
          v-for="candidate in manualPathCandidates"
          :key="candidateKey(candidate)"
          type="button"
          @mousedown.prevent
          @click="selectManualPathCandidate(candidate)"
        >
          <span>{{ candidateLabel(candidate) }}</span
          ><code>{{ candidateCode(candidate) }}</code>
        </button>
      </div>
    </div>
    <var-picker
      v-bind="$attrs"
      v-else
      ref="varPicker"
      :value="value"
      :vars="vars"
      :functions="functions"
      :allowed-kinds="allowedKinds"
      :expected-type="expectedType"
      :writable-only="writableOnly"
      :operand-mode="true"
      @input="onQuickInput"
      @select="onQuickSelect"
      @manual-edit="openManualInput"
    />
    <el-tooltip
      v-if="showEditorButton"
      content="配置组合表达式：可组合字段、方法、阈值和运算符"
      placement="top"
    >
      <button
        type="button"
        class="expression-button"
        aria-label="配置组合表达式"
        @click="openEditor"
      >
        fx
      </button>
    </el-tooltip>
    <expression-editor-dialog
      v-model:visible="editorVisible"
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
import { Collection as ElIconCollection } from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import VarPicker from './VarPicker.vue'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'
import {
  cloneOperand,
  createLiteralOperand,
  createPathOperand,
  resolvePathOperand,
  VALUE_OPERAND_KINDS,
} from '@/utils/operand'
import { createFunctionTemplate } from '@/components/expression/expressionTree'
import {
  createExpressionSessionId,
  createExpressionSessionTitle,
} from '@/utils/expressionSession'

function inheriltClassAndStyle() {
  const attrs = this.$attrs
  attrs.class && this.$el.classList.add(attrs.class)
  attrs.style &&
    Object.entries(attrs.style).forEach(([k, v]) => {
      this.$el.style[k] = v
    })
}

let operandPickerSequence = 0

export default {
  components: {
    ExpressionEditorDialog,
    VarPicker,
    ElIconCollection,
  },
  mounted() {
    inheriltClassAndStyle.call(this)
  },
  name: 'OperandPicker',
  inheritAttrs: false,
  props: {
    value: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => VALUE_OPERAND_KINDS.slice() },
    expectedType: { type: String, default: '' },
    context: { type: String, default: '' },
    writableOnly: { type: Boolean, default: false },
    editorTitle: { type: String, default: '配置表达式' },
  },
  data() {
    return {
      editorVisible: false,
      editorValue: null,
      suppressQuickSelect: false,
      manualKind: '',
      manualOperand: null,
      manualPathCandidates: [],
      expressionSourceKey: `operand-picker-${++operandPickerSequence}`,
      expressionSessionId: '',
      lastAppliedExpressionRevision: 0,
      valueTypes: [
        { label: '文本', value: 'STRING' },
        { label: '数字', value: 'NUMBER' },
        { label: '布尔', value: 'BOOLEAN' },
        { label: '日期', value: 'DATE' },
        { label: '日期时间', value: 'DATETIME' },
        { label: '数组', value: 'LIST' },
        { label: '字典', value: 'MAP' },
      ],
    }
  },
  computed: {
    showEditorButton() {
      if (this.writableOnly) return false
      return this.allowedKinds.some((kind) =>
        [
          'FUNCTION',
          'OPERATION',
          'ACCESS',
          'CAST',
          'ARRAY',
          'LIST_QUERY',
        ].includes(kind)
      )
    },
    editorContext() {
      if (this.context) return this.context
      return this.writableOnly ? 'WRITE_TARGET' : 'READ_EXPRESSION'
    },
  },
  activated() {
    this.consumePendingExpression()
  },
  methods: {
    onQuickInput(operand) {
      if (operand && operand.kind === 'FUNCTION') {
        const template = createFunctionTemplate(
          this.findFunction(operand) || operand
        )
        if (template.args.length) {
          this.suppressQuickSelect = true
          this.openExpressionEditor(template)
          return
        }
      }
      $emit(this, 'update:value', operand)
    },
    onQuickSelect(operand) {
      if (this.suppressQuickSelect) {
        this.suppressQuickSelect = false
        return
      }
      $emit(this, 'select', operand)
    },
    openEditor() {
      return this.openExpressionEditor(this.manualOperand || this.value)
    },
    async openExpressionEditor(operand) {
      const editorValue = cloneOperand(operand)
      const ruleId = this.designerRuleId()
      if (ruleId == null || !this.$store || !this.$router) {
        this.editorValue = editorValue
        this.editorVisible = true
        return
      }

      const sourceKey = this.expressionSourceKey
      const sessionId = createExpressionSessionId(ruleId, sourceKey)
      const routeTitle =
        this.$route && this.$route.meta && this.$route.meta.title
      await this.$store.dispatch('expressionSessions/openSession', {
        sessionId,
        ruleId,
        sourceKey,
        draft: editorValue,
        vars: this.vars,
        functions: this.functions,
        listOptions: this.listOptions,
        allowedKinds: this.allowedKinds,
        context: this.editorContext,
        expectedType: this.expectedType,
        title: createExpressionSessionTitle(
          routeTitle,
          this.editorTitle,
          this.$attrs.placeholder
        ),
      })
      this.expressionSessionId = sessionId
      this.editorVisible = false
      await this.$router.push({
        name: 'ExpressionEditor',
        params: { ruleId: String(ruleId), sessionId },
      })
    },
    designerRuleId() {
      const route = this.$route
      if (
        !route ||
        !route.path ||
        !route.path.startsWith('/designer/') ||
        route.path.startsWith('/designer/expression/')
      )
        return null
      const value = route.params && route.params.id
      if (value == null || value === '') return null
      const numeric = Number(value)
      return Number.isNaN(numeric) ? value : numeric
    },
    async consumePendingExpression() {
      if (!this.expressionSessionId || !this.$store) return
      const getter =
        this.$store.getters['expressionSessions/pendingCompiledResult']
      if (typeof getter !== 'function') return
      const result = getter(this.expressionSessionId)
      if (!result || result.revision <= this.lastAppliedExpressionRevision)
        return

      this.lastAppliedExpressionRevision = result.revision
      const value = cloneOperand(result.operand)
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      await this.$store.dispatch('expressionSessions/markApplied', {
        sessionId: this.expressionSessionId,
        revision: result.revision,
      })
    },
    openManualInput(kind) {
      this.manualKind = kind
      this.manualOperand =
        kind === 'PATH'
          ? createPathOperand('')
          : createLiteralOperand('', this.expectedType || 'STRING')
      this.manualPathCandidates = []
      this.$nextTick(this.focusManualInput)
    },
    focusManualInput() {
      if (
        this.$refs.manualInput &&
        typeof this.$refs.manualInput.focus === 'function'
      )
        this.$refs.manualInput.focus()
    },
    patchManualOperand(fields) {
      this.manualOperand = { ...cloneOperand(this.manualOperand), ...fields }
      this.emitManualOperand()
    },
    updateManualValue(value) {
      if (this.manualKind === 'PATH') {
        this.manualOperand = createPathOperand(value)
        this.manualPathCandidates = []
        this.emitManualOperand()
        return
      }
      this.patchManualOperand({ value })
    },
    resolveManualPath() {
      if (
        this.manualKind !== 'PATH' ||
        !this.manualOperand ||
        !this.manualOperand.value
      )
        return
      const result = resolvePathOperand(this.manualOperand, this.vars)
      this.manualOperand = result.operand
      this.manualPathCandidates = result.candidates
      this.emitManualOperand()
      this.emitPathResolve(result)
    },
    selectManualPathCandidate(candidate) {
      const result = resolvePathOperand(this.manualOperand, [candidate])
      this.manualOperand = result.operand
      this.manualPathCandidates = []
      this.emitManualOperand()
      this.emitPathResolve(result)
    },
    emitPathResolve(result) {
      $emit(this, 'path-resolve', {
        operand: cloneOperand(result && result.operand),
        candidates: ((result && result.candidates) || []).slice(),
      })
    },
    emitManualOperand() {
      const value = cloneOperand(this.manualOperand)
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
    },
    returnToPicker() {
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      this.$nextTick(() => {
        if (
          this.$refs.varPicker &&
          typeof this.$refs.varPicker.onInputClick === 'function'
        )
          this.$refs.varPicker.onInputClick()
      })
    },
    onEditorApply(operand) {
      const value = cloneOperand(operand)
      $emit(this, 'update:value', value)
      $emit(this, 'select', value)
      this.manualKind = ''
      this.manualOperand = null
      this.manualPathCandidates = []
      this.editorVisible = false
    },
    findFunction(operand) {
      return (
        this.functions.find((fn) => {
          const id = fn.functionId != null ? fn.functionId : fn.id
          const code =
            fn.functionCode ||
            fn.funcCode ||
            fn.functionName ||
            fn.funcName ||
            fn.name ||
            ''
          return (
            (operand.functionId != null &&
              String(id) === String(operand.functionId)) ||
            code === operand.functionCode
          )
        }) || null
      )
    },
    candidateKey(item) {
      const type =
        item._refType || item.refType || (item._ref && item._ref.refType) || ''
      const id =
        item._varId != null
          ? item._varId
          : item.refId != null
          ? item.refId
          : item.id
      return type + ':' + (id != null ? id : this.candidateCode(item))
    },
    candidateCode(item) {
      return item.varCode || item.refCode || item.code || ''
    },
    candidateLabel(item) {
      return (
        item.varLabelText ||
        item.varLabel ||
        item.label ||
        this.candidateCode(item)
      )
    },
  },
  emits: ['input', 'update:value', 'select', 'path-resolve'],
}
</script>

<style scoped>
.operand-picker {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  width: 100%;
}
.operand-picker > .var-picker-wrap {
  flex: 1;
  min-width: 0;
}
.operand-manual-editor {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 5px;
}
.operand-manual-type {
  width: 96px;
  flex: none;
}
.operand-manual-editor > .el-input {
  min-width: 0;
  flex: 1;
}
.manual-back-button {
  width: 30px;
  height: 30px;
  flex: none;
  padding: 0;
  border: 1px solid #cbd6e4;
  border-radius: 5px;
  background: #fff;
  color: #607089;
  cursor: pointer;
}
.manual-back-button:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.operand-path-candidates {
  position: absolute;
  z-index: 20;
  top: calc(100% + 4px);
  right: 35px;
  left: 0;
  padding: 6px;
  border: 1px solid #d7e3f2;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(35, 55, 80, 0.14);
}
.operand-path-candidates button {
  display: flex;
  width: 100%;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 8px;
  border: 0;
  background: transparent;
  color: #26364d;
  cursor: pointer;
  text-align: left;
}
.operand-path-candidates button:hover {
  background: #edf5ff;
}
.operand-path-candidates code {
  color: #718096;
  overflow-wrap: anywhere;
}
.expression-button {
  flex: none;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid #cbd6e4;
  border-radius: 5px;
  background: #fff;
  color: var(--el-color-primary);
  font-family: Georgia, serif;
  font-size: 13px;
  font-style: italic;
  cursor: pointer;
}
.expression-button:hover {
  border-color: var(--el-color-primary);
  background: #edf5ff;
}
</style>
