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
          :list-options="listOptions"
          :allowed-kinds="effectiveAllowedKinds"
          :expected-type="expectedType || contextMeta.expectedType"
          @insert="insertTemplate"
        />
        <section class="expression-workspace">
          <div class="formula-preview">
            <span>公式预览</span>
            <code>{{ formulaPreview || '请选择中间位置并添加内容' }}</code>
          </div>
          <div class="workspace-tools">
            <span>复杂公式可折叠子表达式，当前编辑位置会自动展开。</span>
            <div>
              <el-button size="mini" @click="collapseToOverview">折叠到两层</el-button>
              <el-button size="mini" @click="expandAll">全部展开</el-button>
            </div>
          </div>
          <el-alert v-if="validationErrors.length" type="error" :closable="false" show-icon :title="validationErrors[0].message" />
          <div class="canvas-scroll">
            <expression-canvas
              :node="draft"
              :selected-path="selectedPath"
              :collapsed-path-keys="collapsedPathKeys"
              :expected-type="expectedType || contextMeta.expectedType"
              :path-candidates="pathCandidates"
              :candidate-path-key="candidatePathKey"
              @select="selectPath"
              @toggleCollapse="toggleCollapse"
              @patchNode="patchCanvasNode"
              @manualInput="updateManualPath"
              @resolvePath="resolveManualPath"
              @selectPathCandidate="selectPathCandidate"
            />
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
import { compileOperand, cloneOperand, createPathOperand, resolvePathOperand, validateOperand } from '@/utils/operand'
import { getExpressionContext } from '@/constants/expressionContexts'
import {
  collapsedExpressionPaths,
  createFunctionTemplate,
  existingCollapsedPaths,
  expressionAncestorKeys,
  expressionChildEntries,
  expressionPathKey,
  firstEditablePath,
  getExpressionNode,
  insertExpressionOperation,
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
      collapsedPathKeys: [],
      validationErrors: [],
      pathCandidates: [],
      candidatePathKey: '',
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
      this.collapsedPathKeys = collapsedExpressionPaths(this.draft, 2)
      this.revealPath(this.selectedPath)
      this.history = [cloneOperand(this.draft)]
      this.historyIndex = 0
      this.validationErrors = []
      this.clearPathCandidates()
    },
    selectPath(path) {
      this.selectedPath = (path || []).slice()
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    insertTemplate(template) {
      if (template && template.kind === 'OPERATION') {
        const term = (template.terms || [])[1]
        const result = insertExpressionOperation(this.draft, this.selectedPath, term && term.operator)
        this.commit(result.root)
        this.selectedPath = result.selectedPath
        this.revealPath(this.selectedPath)
        this.validationErrors = []
        this.clearPathCandidates()
        return
      }
      const current = getExpressionNode(this.draft, this.selectedPath)
      const nextNode = ['ACCESS', 'CAST'].includes(template && template.kind)
        ? wrapExpressionNode(current, template)
        : cloneOperand(template)
      const next = setExpressionNode(this.draft, this.selectedPath, nextNode)
      const insertedPath = this.selectedPath.slice()
      this.commit(next)
      this.selectedPath = firstEditablePath(nextNode, insertedPath)
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    updateSelected(node) {
      const previous = getExpressionNode(this.draft, this.selectedPath)
      const basePath = this.selectedPath.slice()
      this.commit(setExpressionNode(this.draft, basePath, node))
      const previousCount = previous && previous.kind === 'OPERATION' ? (previous.terms || []).length : 0
      const nextCount = node && node.kind === 'OPERATION' ? (node.terms || []).length : 0
      if (nextCount > previousCount) {
        this.selectedPath = basePath.concat(['terms', nextCount - 1, 'operand'])
        this.revealPath(this.selectedPath)
      }
      this.clearPathCandidates()
    },
    removeSelected() {
      const path = this.selectedPath.slice()
      this.commit(removeExpressionNode(this.draft, path))
      this.selectedPath = path
      this.clearPathCandidates()
    },
    patchCanvasNode({ path, fields }) {
      const current = getExpressionNode(this.draft, path)
      this.commit(setExpressionNode(this.draft, path, { ...cloneOperand(current), ...fields }))
      this.selectedPath = path.slice()
    },
    updateManualPath({ path, value }) {
      this.commit(setExpressionNode(this.draft, path, createPathOperand(value)))
      this.selectedPath = path.slice()
      this.clearPathCandidates()
    },
    resolveManualPath(path) {
      const current = getExpressionNode(this.draft, path)
      if (!current || current.kind !== 'PATH' || !current.value) return
      const result = resolvePathOperand(current, this.vars)
      this.pathCandidates = result.candidates
      this.candidatePathKey = expressionPathKey(path)
      this.commit(setExpressionNode(this.draft, path, result.operand))
      this.selectedPath = path.slice()
    },
    selectPathCandidate({ path, candidate }) {
      const current = getExpressionNode(this.draft, path)
      const result = resolvePathOperand(current, [candidate])
      this.commit(setExpressionNode(this.draft, path, result.operand))
      this.selectedPath = path.slice()
      this.clearPathCandidates()
    },
    clearPathCandidates() {
      this.pathCandidates = []
      this.candidatePathKey = ''
    },
    commit(value) {
      const snapshot = cloneOperand(value)
      this.history = this.history.slice(0, this.historyIndex + 1).concat([snapshot])
      this.historyIndex = this.history.length - 1
      this.draft = cloneOperand(snapshot)
      this.collapsedPathKeys = existingCollapsedPaths(this.draft, this.collapsedPathKeys)
    },
    undo() {
      if (!this.canUndo) return
      this.historyIndex -= 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.collapsedPathKeys = existingCollapsedPaths(this.draft, this.collapsedPathKeys)
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    redo() {
      if (!this.canRedo) return
      this.historyIndex += 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.collapsedPathKeys = existingCollapsedPaths(this.draft, this.collapsedPathKeys)
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    toggleCollapse(path) {
      const key = expressionPathKey(path)
      if (this.collapsedPathKeys.includes(key)) {
        this.collapsedPathKeys = this.collapsedPathKeys.filter(item => item !== key)
      } else if (expressionChildEntries(getExpressionNode(this.draft, path), path).length) {
        this.collapsedPathKeys = this.collapsedPathKeys.concat([key])
      }
    },
    collapseToOverview() {
      this.collapsedPathKeys = collapsedExpressionPaths(this.draft, 2)
      this.revealPath(this.selectedPath)
    },
    expandAll() {
      this.collapsedPathKeys = []
    },
    revealPath(path) {
      const ancestors = new Set(expressionAncestorKeys(path))
      this.collapsedPathKeys = this.collapsedPathKeys.filter(key => !ancestors.has(key))
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
.expression-editor__body { display: flex; min-width: 0; min-height: 0; overflow: auto; }
.expression-workspace { display: flex; min-width: 420px; min-height: 0; flex: 1; flex-direction: column; padding: 18px; background: #f4f7fb; }
.formula-preview { display: grid; min-height: 52px; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 12px; margin-bottom: 12px; padding: 9px 13px; border: 1px solid #dce5ef; border-radius: 7px; background: #fff; }
.formula-preview span { color: #7d8a9d; font-size: 12px; }
.formula-preview code { max-height: 96px; overflow: auto; color: #174ea6; font-family: Consolas, monospace; overflow-wrap: anywhere; white-space: normal; }
.workspace-tools { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: -4px 0 10px; color: #7d8a9d; font-size: 12px; }
.workspace-tools > div { display: flex; flex: none; gap: 6px; }
.canvas-scroll { flex: 1; overflow: auto; padding: 12px 8px 50px; }
.expression-editor__body > .expression-inspector { box-sizing: border-box; width: 320px; flex: 0 0 320px; }
.expression-editor__footer { border-top: 1px solid #e7ecf2; border-bottom: 0; }
.expression-editor__footer > span { color: #7d8a9d; font-size: 12px; }
.expression-fade-enter-active, .expression-fade-leave-active { transition: opacity .16s ease; }
.expression-fade-enter, .expression-fade-leave-to { opacity: 0; }
</style>

<style>
.expression-editor-select-popper { z-index: 3300 !important; }
</style>
