<template>
  <transition name="expression-fade">
    <div v-if="visible" class="expression-editor" role="dialog" aria-modal="true" :aria-label="title" @keydown.esc.stop.prevent="cancel">
      <header class="expression-editor__header">
        <div>
          <h2>{{ title }}</h2>
          <p>先选中中间位置，再从左侧点击字段、方法或运算符；方法参数会自动展开。</p>
        </div>
        <div class="header-actions">
          <el-button size="small" :disabled="!canUndo" @click="undo">撤销</el-button>
          <el-button size="small" :disabled="!canRedo" @click="redo">重做</el-button>
          <button ref="closeButton" type="button" class="close-button" aria-label="关闭表达式编辑器" @click="cancel"><i class="el-icon-close" /></button>
        </div>
      </header>

      <main class="expression-editor__body">
        <expression-palette
          :vars="vars"
          :functions="functions"
          :allowed-kinds="effectiveAllowedKinds"
          :expected-type="expectedType || contextMeta.expectedType"
          @insert="insertTemplate"
        />
        <section class="expression-workspace">
          <div class="formula-preview">
            <span>公式预览</span>
            <code>{{ formulaPreview || '请选择中间位置并添加内容' }}</code>
          </div>
          <el-alert v-if="validationErrors.length" type="error" :closable="false" show-icon :title="validationErrors[0].message" />
          <div class="canvas-scroll">
            <expression-canvas :node="draft" :selected-path="selectedPath" @select="selectPath" />
          </div>
        </section>
        <expression-node-inspector
          :node="selectedNode"
          :list-options="listOptions"
          @input="updateSelected"
          @remove="removeSelected"
        />
      </main>

      <footer class="expression-editor__footer">
        <span>提示：支持多层函数、字段、阈值、四则运算、取 Key / Index 和类型转换。</span>
        <div><el-button @click="cancel">取消</el-button><el-button type="primary" @click="apply">应用公式</el-button></div>
      </footer>
    </div>
  </transition>
</template>

<script>
import ExpressionCanvas from './ExpressionCanvas.vue'
import ExpressionNodeInspector from './ExpressionNodeInspector.vue'
import ExpressionPalette from './ExpressionPalette.vue'
import { compileOperand, cloneOperand, validateOperand } from '@/utils/operand'
import { getExpressionContext } from '@/constants/expressionContexts'
import {
  createFunctionTemplate,
  firstEditablePath,
  getExpressionNode,
  removeExpressionNode,
  setExpressionNode,
  wrapExpressionNode
} from './expressionTree'

export default {
  name: 'ExpressionEditorDialog',
  components: { ExpressionCanvas, ExpressionNodeInspector, ExpressionPalette },
  props: {
    visible: { type: Boolean, default: false },
    value: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => [] },
    context: { type: String, default: 'READ_EXPRESSION' },
    expectedType: { type: String, default: '' },
    title: { type: String, default: '配置表达式' }
  },
  data() {
    return {
      draft: null,
      selectedPath: [],
      history: [],
      historyIndex: -1,
      validationErrors: [],
      previousFocus: null
    }
  },
  computed: {
    contextMeta() { return getExpressionContext(this.context) },
    effectiveAllowedKinds() { return this.allowedKinds.length ? this.allowedKinds : this.contextMeta.allowedKinds },
    selectedNode() { return getExpressionNode(this.draft, this.selectedPath) },
    canUndo() { return this.historyIndex > 0 },
    canRedo() { return this.historyIndex >= 0 && this.historyIndex < this.history.length - 1 },
    formulaPreview() {
      try { return compileOperand(this.draft) } catch (e) { return '' }
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        if (value) this.open()
      }
    },
    value: {
      deep: true,
      handler(value) {
        if (this.visible && this.historyIndex <= 0) this.reset(value)
      }
    }
  },
  mounted() {
    document.addEventListener('keydown', this.onKeydown)
  },
  beforeDestroy() {
    document.removeEventListener('keydown', this.onKeydown)
  },
  methods: {
    functionTemplate(fn) { return createFunctionTemplate(fn) },
    open() {
      this.previousFocus = document.activeElement
      this.reset(this.value)
      this.$nextTick(() => {
        if (this.$refs.closeButton) this.$refs.closeButton.focus()
      })
    },
    reset(value) {
      this.draft = cloneOperand(value)
      this.selectedPath = firstEditablePath(this.draft)
      this.history = [cloneOperand(this.draft)]
      this.historyIndex = 0
      this.validationErrors = []
    },
    selectPath(path) {
      this.selectedPath = (path || []).slice()
      this.validationErrors = []
    },
    insertTemplate(template) {
      const current = getExpressionNode(this.draft, this.selectedPath)
      const nextNode = ['OPERATION', 'ACCESS', 'CAST'].includes(template && template.kind)
        ? wrapExpressionNode(current, template)
        : cloneOperand(template)
      const next = setExpressionNode(this.draft, this.selectedPath, nextNode)
      const insertedPath = this.selectedPath.slice()
      this.commit(next)
      this.selectedPath = firstEditablePath(nextNode, insertedPath)
      this.validationErrors = []
    },
    updateSelected(node) {
      this.commit(setExpressionNode(this.draft, this.selectedPath, node))
    },
    removeSelected() {
      const path = this.selectedPath.slice()
      this.commit(removeExpressionNode(this.draft, path))
      this.selectedPath = path
    },
    commit(value) {
      const snapshot = cloneOperand(value)
      this.history = this.history.slice(0, this.historyIndex + 1).concat([snapshot])
      this.historyIndex = this.history.length - 1
      this.draft = cloneOperand(snapshot)
    },
    undo() {
      if (!this.canUndo) return
      this.historyIndex -= 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.validationErrors = []
    },
    redo() {
      if (!this.canRedo) return
      this.historyIndex += 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.validationErrors = []
    },
    apply() {
      this.validationErrors = validateOperand(this.draft, { allowedKinds: this.effectiveAllowedKinds })
      if (this.validationErrors.length) return
      const result = cloneOperand(this.draft)
      this.$emit('input', result)
      this.$emit('apply', result)
      this.close('apply')
    },
    cancel() {
      this.$emit('cancel')
      this.close('cancel')
    },
    close() {
      if (document.activeElement && this.$el && this.$el.contains(document.activeElement)) document.activeElement.blur()
      this.$emit('update:visible', false)
      this.$nextTick(() => {
        if (this.previousFocus && typeof this.previousFocus.focus === 'function' && document.body.contains(this.previousFocus)) this.previousFocus.focus()
      })
    },
    onKeydown(event) {
      if (this.visible && event.key === 'Escape') this.cancel()
    }
  }
}
</script>

<style scoped>
.expression-editor { position: fixed; z-index: 3200; inset: 0; display: grid; grid-template-rows: 68px minmax(0, 1fr) 64px; background: #fff; color: #26364d; }
.expression-editor__header, .expression-editor__footer { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 22px; border-bottom: 1px solid #e7ecf2; background: #fff; }
.expression-editor__header h2 { margin: 0; font-size: 19px; }
.expression-editor__header p { margin: 4px 0 0; color: #8290a3; font-size: 12px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.close-button { width: 34px; height: 34px; margin-left: 5px; border: 0; border-radius: 6px; background: #f3f6f9; color: #526278; cursor: pointer; }
.expression-editor__body { display: grid; min-height: 0; grid-template-columns: 270px minmax(420px, 1fr) 320px; }
.expression-workspace { display: flex; min-width: 0; min-height: 0; flex-direction: column; padding: 18px; background: #f4f7fb; }
.formula-preview { display: grid; min-height: 52px; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 12px; margin-bottom: 12px; padding: 9px 13px; border: 1px solid #dce5ef; border-radius: 7px; background: #fff; }
.formula-preview span { color: #7d8a9d; font-size: 12px; }
.formula-preview code { overflow: hidden; color: #174ea6; font-family: Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.canvas-scroll { flex: 1; overflow: auto; padding: 12px 8px 50px; }
.expression-editor__footer { border-top: 1px solid #e7ecf2; border-bottom: 0; }
.expression-editor__footer > span { color: #7d8a9d; font-size: 12px; }
.expression-fade-enter-active, .expression-fade-leave-active { transition: opacity .16s ease; }
.expression-fade-enter, .expression-fade-leave-to { opacity: 0; }
@media (max-width: 1000px) { .expression-editor__body { grid-template-columns: 220px minmax(360px, 1fr) 280px; } }
</style>
