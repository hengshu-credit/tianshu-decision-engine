<template>
  <transition name="expression-fade">
    <div
      v-show="visible"
      class="expression-editor"
      :class="{ 'expression-editor--embedded': embedded, 'expression-editor--compact': compact }"
      role="dialog"
      :aria-modal="embedded ? null : 'true'"
      :aria-label="title"
      @keydown.esc.stop.prevent="!compact && cancel()"
    >
      <header v-if="!embedded" class="expression-editor__header">
        <div>
          <h2>{{ title }}</h2>
          <p>
            先选中中间位置，再从左侧点击字段、方法或运算符；方法参数会自动展开。
          </p>
        </div>
        <div class="header-actions">
          <el-button size="small" :disabled="!canUndo" @click="undo"
            >撤销</el-button
          >
          <el-button size="small" :disabled="!canRedo" @click="redo"
            >重做</el-button
          >
          <button
            ref="closeButton"
            type="button"
            class="close-button"
            aria-label="关闭表达式编辑器"
            @click="cancel"
          >
            <el-icon><el-icon-close /></el-icon>
          </button>
        </div>
      </header>

      <main class="expression-editor__body">
        <expression-palette
          v-if="!compact"
          :vars="vars"
          :functions="functions"
          :list-options="listOptions"
          :allowed-kinds="effectiveAllowedKinds"
          :expected-type="expectedType || contextMeta.expectedType"
          @insert="insertTemplate"
        />
        <section class="expression-workspace">
          <component :is="nodePickerComponent" v-if="compact && pickerAnchor && nodePickerComponent" :key="pathKey(pickerPath)" ref="fieldPicker" :virtual-ref="pickerAnchor" :value="pickerNode" :vars="vars" :functions="functions" :list-options="listOptions" :allowed-kinds="effectiveAllowedKinds" :expected-type="selectedExpectedType" :visible-tags="visibleTags" :selectable-tags="selectableTags" dismiss-on-outside @input="insertPicked" />
          <div class="workspace-tools">
            <span title="↑/↓ 选择节点；Alt+↑/↓ 调整顺序；Tab / Shift+Tab 调整缩进；Backspace 删除当前项，Del 删除后一项；Ctrl/Cmd+Z 撤销，Ctrl/Cmd+Shift+Z 重做；Enter 编辑节点。输入框内保留文本快捷键，Esc 返回节点。">↑↓ 选择 · Alt+↑↓ 调序 · Tab 缩进 · Backspace 删当前 · Del 删后一项</span>
            <div>
              <el-button size="small" @click="collapseToOverview"
                >折叠到两层</el-button
              >
              <el-button size="small" @click="expandAll">全部展开</el-button>
            </div>
          </div>
          <el-alert
            v-if="validationErrors.length"
            type="error"
            :closable="false"
            show-icon
            :title="validationErrors[0].message"
          />
          <div ref="canvasScroll" class="canvas-scroll">
            <expression-canvas
              :node="draft"
              :selected-path="selectedPath"
              :collapsed-path-keys="collapsedPathKeys"
              :expected-type="expectedType || contextMeta.expectedType"
              :path-candidates="pathCandidates"
              :candidate-path-key="candidatePathKey"
              :field-picker-enabled="compact"
              :allow-operations="effectiveAllowedKinds.includes('OPERATION')"
              :list-options="listOptions"
              @requestField="openFieldPicker"
              @appendRow="appendRow"
              @select="selectPath"
              @toggleCollapse="toggleCollapse"
              @patchNode="patchCanvasNode"
              @manualInput="updateManualPath"
              @resolvePath="resolveManualPath"
              @selectPathCandidate="selectPathCandidate"
              @indent="indentPath"
              @outdent="outdentPath"
              @move="movePath"
              @navigate="navigatePath"
              @deleteKey="deleteAtCursor"
              @moveNode="moveNode"
              @remove="removePath"
              @replaceOperator="replaceOperator"
              @operatorKey="operatorKey"
            />
          </div>
        </section>
        <expression-node-inspector
          v-if="!compact"
          :node="selectedNode"
          :list-options="listOptions"
          @input="updateSelected"
          @remove="removeSelected"
        />
      </main>

      <expression-formula-preview
        class="expression-editor__preview"
        :operand="draft"
        :vars="vars"
        :functions="functions"
        @confirm="replaceDraftFromScript"
      />

      <footer v-if="!embedded || compact" class="expression-editor__footer">
        <span
          >{{ compact ? '关闭后保留草稿，应用后才回填输入框。' : '支持多层函数、字段、阈值、四则运算、取 Key / Index 和类型转换。' }}</span
        >
        <div>
          <el-button @click="cancel">{{ compact ? '暂存并关闭' : '取消' }}</el-button
          ><el-button type="primary" @click="apply">应用公式</el-button>
        </div>
      </footer>
    </div>
  </transition>
</template>

<script>
import { markRaw } from 'vue'
import { Close as ElIconClose } from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import ExpressionCanvas from './ExpressionCanvas.vue'
import ExpressionNodeInspector from './ExpressionNodeInspector.vue'
import ExpressionPalette from './ExpressionPalette.vue'
import ExpressionFormulaPreview from './ExpressionFormulaPreview.vue'
import {
  cloneOperand,
  createPathOperand,
  resolvePathOperand,
  validateOperand,
  functionArgumentExpectedType,
} from '@/utils/operand'
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
  appendExpressionRow,
  indentExpressionTerm,
  moveExpressionNode,
  moveExpressionSibling,
  outdentExpressionOperation,
  removeExpressionSelection,
  removeExpressionAtCursor,
  replaceExpressionOperator,
  setExpressionNode,
  wrapExpressionNode,
} from './expressionTree'

export default {
  components: {
    ExpressionCanvas,
    ExpressionFormulaPreview,
    ExpressionNodeInspector,
    ExpressionPalette,
    ElIconClose,
  },
  name: 'ExpressionEditorDialog',
  props: {
    visible: { type: Boolean, default: false },
    value: { type: Object, default: null },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    allowedKinds: { type: Array, default: () => [] },
    context: { type: String, default: 'READ_EXPRESSION' },
    expectedType: { type: String, default: '' },
    title: { type: String, default: '配置表达式' },
    embedded: { type: Boolean, default: false },
    compact: Boolean,
    preserveDraft: Boolean,
    visibleTags: { type: [Array, Object], default: null },
    selectableTags: { type: [Array, Object], default: null },
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
      previousFocus: null,
      initialized: false,
      nodePickerComponent: null,
      pickerAnchor: null,
      pickerPath: [],
    }
  },
  computed: {
    contextMeta() {
      return getExpressionContext(this.context)
    },
    effectiveAllowedKinds() {
      return this.allowedKinds.length
        ? this.allowedKinds
        : this.contextMeta.allowedKinds
    },
    selectedNode() {
      return getExpressionNode(this.draft, this.selectedPath)
    },
    pickerNode() { return getExpressionNode(this.draft, this.pickerPath) },
    canUndo() {
      return this.historyIndex > 0
    },
    canRedo() {
      return (
        this.historyIndex >= 0 && this.historyIndex < this.history.length - 1
      )
    },
    selectedExpectedType() {
      const path = this.selectedPath
      if (path.at(-2) === 'args') return functionArgumentExpectedType(getExpressionNode(this.draft, path.slice(0, -2)), path.at(-1))
      return ''
    },
  },
  watch: {
    visible: {
      deep: true,
      immediate: true,

      handler(value) {
        if (value) this.open()
        else this.$refs.fieldPicker?.$refs.varPicker?.closePopover?.({ restoreFocus: false })
      },
    },
    value: {
      deep: true,
      handler(value) {
        if (this.preserveDraft || this.visible && this.historyIndex <= 0) this.reset(value)
      },
    },
  },
  mounted() {
    document.addEventListener('keydown', this.onKeydown)
  },
  beforeUnmount() {
    document.removeEventListener('keydown', this.onKeydown)
  },
  methods: {
    pathKey: expressionPathKey,
    functionTemplate(fn) {
      return createFunctionTemplate(fn)
    },
    open() {
      this.previousFocus = document.activeElement
      if (!this.preserveDraft || !this.initialized) this.reset(this.value)
      this.$nextTick(() => {
        if (this.$refs.closeButton) this.$refs.closeButton.focus()
      })
    },
    reset(value) {
      this.initialized = true
      this.draft = cloneOperand(value)
      this.selectedPath = firstEditablePath(this.draft)
      this.collapsedPathKeys = collapsedExpressionPaths(this.draft, 2)
      this.revealPath(this.selectedPath)
      this.history = [cloneOperand(this.draft)]
      this.historyIndex = 0
      this.validationErrors = []
      this.clearPathCandidates()
    },
    async openFieldPicker(path) {
      this.selectPath(path)
      this.pickerPath = path.slice()
      // 延迟加载以复用完整选择器，并避开 OperandPicker 与表达式编辑器的循环依赖。
      if (!this.nodePickerComponent) this.nodePickerComponent = markRaw((await import('@/components/common/OperandPicker.vue')).default)
      if (!this.visible) return
      await this.$nextTick()
      this.pickerAnchor = [...(this.$refs.canvasScroll?.querySelectorAll('.canvas-node') || [])].find(element => element.dataset.expressionPath === expressionPathKey(this.pickerPath)) || null
      await this.$nextTick()
      if (this.visible) this.$refs.fieldPicker?.$refs.varPicker?.openPopover?.()
    },
    insertPicked(operand) {
      this.commit(setExpressionNode(this.draft, this.pickerPath, operand))
      this.selectedPath = firstEditablePath(operand, this.pickerPath)
      this.revealPath(this.selectedPath)
      this.clearPathCandidates()
      this.validationErrors = []
    },
    appendRow(path) {
      const result = appendExpressionRow(this.draft, path)
      this.applyTreeResult(result)
      this.openFieldPicker(result.selectedPath)
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
        const result = insertExpressionOperation(
          this.draft,
          this.selectedPath,
          term && term.operator
        )
        this.applyTreeResult(result)
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
      const previousCount =
        previous && previous.kind === 'OPERATION'
          ? (previous.terms || []).length
          : 0
      const nextCount =
        node && node.kind === 'OPERATION' ? (node.terms || []).length : 0
      if (nextCount > previousCount) {
        this.selectedPath = basePath.concat(['terms', nextCount - 1, 'operand'])
        this.revealPath(this.selectedPath)
      } else if (nextCount < previousCount) {
        this.selectedPath = firstEditablePath(node, basePath)
        this.revealPath(this.selectedPath)
      }
      this.clearPathCandidates()
    },
    removeSelected() {
      this.removePath(this.selectedPath)
    },
    removePath(path) {
      this.applyTreeResult(removeExpressionSelection(this.draft, path))
    },
    deleteAtCursor({ path, key, operator }) {
      this.applyTreeResult(removeExpressionAtCursor(this.draft, path, key, operator))
    },
    navigatePath({ path, offset }) {
      const paths = []
      const visit = (node, current = []) => {
        if (current.length || node?.kind !== 'OPERATION') paths.push(current)
        if (!this.collapsedPathKeys.includes(expressionPathKey(current))) expressionChildEntries(node, current).forEach(entry => visit(entry.value, entry.path))
      }
      visit(this.draft)
      const index = paths.findIndex(item => expressionPathKey(item) === expressionPathKey(path))
      if (!paths[index + offset]) return
      this.selectPath(paths[index + offset])
      this.focusCanvasSelection()
    },
    replaceOperator({ path, operator }) {
      this.applyTreeResult(replaceExpressionOperator(this.draft, path, operator))
    },
    operatorKey({ path, key, replace = false }) {
      if (!this.effectiveAllowedKinds.includes('OPERATION')) return
      const current = getExpressionNode(this.draft, path)
      const term = getExpressionNode(this.draft, path.slice(0, -1))
      const previous = (replace || !current) && term && term.operator
      const operator = key === '=' && ['>', '<', '!=', '>=', '<='].includes(previous)
        ? (previous.length === 1 ? previous + '=' : previous)
        : ({ '|': '||', '&': '&&', '=': '==', '!': '!=' }[key] || key)
      this.applyTreeResult(replace
        ? replaceExpressionOperator(this.draft, path, operator)
        : insertExpressionOperation(this.draft, path, operator))
    },
    patchCanvasNode({ path, fields }) {
      const current = getExpressionNode(this.draft, path)
      this.commit(
        setExpressionNode(this.draft, path, {
          ...cloneOperand(current),
          ...fields,
        })
      )
      this.selectedPath = path.slice()
    },
    updateManualPath({ path, value }) {
      const current = getExpressionNode(this.draft, path)
      const result = resolvePathOperand(current?.versionMode === 'FIXED' ? { ...current, value } : createPathOperand(value), this.vars)
      this.commit(setExpressionNode(this.draft, path, result.operand))
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
    applyTreeResult(result) {
      if (!result || !result.changed) return
      this.commit(result.root)
      this.selectedPath = result.selectedPath.slice()
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
      this.focusCanvasSelection()
    },
    async focusCanvasSelection() {
      // Newly mounted inline inputs focus themselves; keep structural shortcuts on the card.
      await this.$nextTick()
      await this.$nextTick()
      this.$refs.canvasScroll?.querySelector('.canvas-node--selected')?.focus()
    },
    replaceDraftFromScript(operand) {
      this.commit(operand)
      this.selectedPath = firstEditablePath(this.draft)
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    getDraft() {
      return cloneOperand(this.draft)
    },
    validateDraft() {
      this.validationErrors = validateOperand(this.draft, {
        allowedKinds: this.effectiveAllowedKinds,
      })
      const validateJson = (node, path = []) => {
        if (node?.kind === 'LITERAL' && ['LIST', 'MAP', 'OBJECT'].includes(node.valueType)) {
          try {
            const value = typeof node.value === 'string' ? JSON.parse(node.value) : node.value
            const valid = node.valueType === 'LIST' ? Array.isArray(value) : value && typeof value === 'object' && !Array.isArray(value)
            if (!valid) throw new Error()
          } catch { this.validationErrors.push({ path: expressionPathKey(path), message: node.valueType === 'LIST' ? '请输入合法的 JSON 数组' : '请输入合法的 JSON 对象' }) }
        }
        expressionChildEntries(node, path).forEach(entry => validateJson(entry.value, entry.path))
      }
      validateJson(this.draft)
      return this.validationErrors.slice()
    },
    indentPath(path) {
      this.applyTreeResult(indentExpressionTerm(this.draft, path))
    },
    outdentPath(path) {
      this.applyTreeResult(outdentExpressionOperation(this.draft, path))
    },
    movePath({ path, offset }) {
      this.applyTreeResult(moveExpressionSibling(this.draft, path, offset))
    },
    moveNode({ fromPath, toPath }) {
      let result = moveExpressionNode(this.draft, fromPath, toPath)
      if (!result.changed) {
        const siblingLocation = (path) => {
          const operationPath =
            path.length >= 3 &&
            path[path.length - 3] === 'terms' &&
            path[path.length - 1] === 'operand'
          return operationPath
            ? { index: path[path.length - 2], keyPath: path.slice(0, -2) }
            : { index: path[path.length - 1], keyPath: path.slice(0, -1) }
        }
        const source = siblingLocation(fromPath)
        const target = siblingLocation(toPath)
        if (
          Number.isInteger(source.index) &&
          Number.isInteger(target.index) &&
          JSON.stringify(source.keyPath) === JSON.stringify(target.keyPath)
        ) {
          result = moveExpressionSibling(
            this.draft,
            fromPath,
            target.index - source.index
          )
        }
      }
      this.applyTreeResult(result)
    },
    commit(value) {
      const snapshot = cloneOperand(value)
      if (JSON.stringify(snapshot) === JSON.stringify(this.draft)) return
      this.history = this.history
        .slice(0, this.historyIndex + 1)
        .concat([snapshot])
      this.historyIndex = this.history.length - 1
      this.draft = cloneOperand(snapshot)
      this.collapsedPathKeys = existingCollapsedPaths(
        this.draft,
        this.collapsedPathKeys
      )
    },
    undo() {
      if (!this.canUndo) return
      this.historyIndex -= 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.collapsedPathKeys = existingCollapsedPaths(
        this.draft,
        this.collapsedPathKeys
      )
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    redo() {
      if (!this.canRedo) return
      this.historyIndex += 1
      this.draft = cloneOperand(this.history[this.historyIndex])
      this.selectedPath = firstEditablePath(this.draft)
      this.collapsedPathKeys = existingCollapsedPaths(
        this.draft,
        this.collapsedPathKeys
      )
      this.revealPath(this.selectedPath)
      this.validationErrors = []
      this.clearPathCandidates()
    },
    toggleCollapse(path) {
      const key = expressionPathKey(path)
      if (this.collapsedPathKeys.includes(key)) {
        this.collapsedPathKeys = this.collapsedPathKeys.filter(
          (item) => item !== key
        )
      } else if (
        expressionChildEntries(getExpressionNode(this.draft, path), path).length
      ) {
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
      this.collapsedPathKeys = this.collapsedPathKeys.filter(
        (key) => !ancestors.has(key)
      )
    },
    apply() {
      this.validateDraft()
      if (this.validationErrors.length) return
      const result = cloneOperand(this.draft)
      $emit(this, 'update:value', result)
      $emit(this, 'apply', result)
      this.close('apply')
    },
    cancel() {
      $emit(this, 'cancel')
      this.close('cancel')
    },
    close() {
      if (
        document.activeElement &&
        this.$el &&
        this.$el.contains(document.activeElement)
      )
        document.activeElement.blur()
      $emit(this, 'update:visible', false)
      this.$nextTick(() => {
        if (
          this.previousFocus &&
          typeof this.previousFocus.focus === 'function' &&
          document.body.contains(this.previousFocus)
        )
          this.previousFocus.focus()
      })
    },
    onKeydown(event) {
      if (!this.visible || !this.$el?.contains?.(event.target)) return
      const editingText = event.target?.closest?.('input, textarea, select, [contenteditable="true"], [role="combobox"]')
      if (!editingText && !event.isComposing && (event.ctrlKey || event.metaKey) && ['z', 'y'].includes(event.key.toLowerCase())) {
        event.preventDefault()
        event.stopPropagation()
        if (event.shiftKey || event.key.toLowerCase() === 'y') this.redo()
        else this.undo()
        this.focusCanvasSelection()
        return
      }
      if (!this.compact && this.visible && event.key === 'Escape') this.cancel()
    },
  },
  emits: ['input', 'update:value', 'apply', 'update:visible', 'cancel'],
}
</script>

<style scoped>
.expression-editor {
  position: fixed;
  z-index: 3200;
  inset: 0;
  display: grid;
  grid-template-rows: 68px minmax(180px, 1fr) auto 64px;
  overflow: auto;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-primary);
}
.expression-editor--embedded {
  position: relative;
  z-index: auto;
  inset: auto;
  width: 100%;
  height: 100%;
  min-height: 0;
  grid-template-rows: minmax(180px, 1fr) auto;
  overflow: auto;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 8px;
  container-type: inline-size;
}
.expression-editor--compact { display: flex; flex-direction: column; border: 0; border-radius: 0; }
.expression-editor--compact > .expression-editor__body { flex: 1 0 180px; }
.expression-editor--compact > .expression-editor__preview { flex: none; }
.expression-editor--compact .expression-workspace { min-width: 0; padding: 10px; }
.expression-editor--compact .expression-editor__footer { flex: none; min-height: 48px; padding: 8px 12px; gap: 8px; flex-wrap: wrap; }
.expression-editor--compact .expression-editor__footer > div { display: flex; gap: 8px; margin-left: auto; }
.expression-editor--compact .canvas-scroll { padding: 4px 0 20px; }
.expression-editor--compact :deep(.canvas-node), .expression-editor--compact :deep(.canvas-kind), .expression-editor--compact :deep(.canvas-node-actions button) { border-radius: 0; }
.expression-editor--compact .workspace-tools { gap: 4px; font-size: 11px; }
.expression-editor__header,
.expression-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 22px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
  background: var(--tianshu-bg-surface);
}
.expression-editor__header h2 {
  margin: 0;
  font-size: 19px;
}
.expression-editor__header p {
  margin: 4px 0 0;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.close-button {
  width: 34px;
  height: 34px;
  margin-left: 5px;
  border: 0;
  border-radius: 6px;
  background: var(--tianshu-bg-muted);
  color: var(--tianshu-text-secondary);
  cursor: pointer;
}
.expression-editor__body {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}
.expression-editor__preview {
  min-height: min-content;
  overflow: visible;
}
.expression-workspace {
  display: flex;
  min-width: 300px;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 18px;
  background: var(--tianshu-bg-workspace);
}
.workspace-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -4px 0 10px;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.workspace-tools > span {
  flex: 1 1 240px;
}
.workspace-tools > div {
  display: flex;
  flex: none;
  gap: 6px;
  margin-left: auto;
}
.canvas-scroll {
  flex: 1;
  overflow: auto;
  padding: 12px 8px 50px;
}
.expression-editor__body > .expression-inspector {
  box-sizing: border-box;
  width: 260px;
  flex: 0 0 260px;
}
@container (max-width: 900px) {
  .expression-editor__body > .expression-inspector {
    width: 230px;
    flex-basis: 230px;
  }
  .expression-workspace {
    min-width: 260px;
    padding: 12px;
  }
}
.expression-editor__footer {
  border-top: 1px solid var(--tianshu-border-subtle);
  border-bottom: 0;
}
.expression-editor__footer > span {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.expression-fade-enter-active,
.expression-fade-leave-active {
  transition: opacity 0.16s ease;
}
.expression-fade-enter-from,
.expression-fade-leave-to {
  opacity: 0;
}
</style>

<style>
.expression-editor-select-popper {
  z-index: 3300 !important;
}
</style>
