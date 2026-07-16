<template>
  <div class="expression-canvas-node">
    <div v-if="showNodeRow" class="canvas-node-row">
      <button
        v-if="node && children.length"
        type="button"
        class="canvas-collapse"
        :title="collapsed ? '展开子表达式' : '折叠子表达式'"
        :aria-label="collapsed ? '展开子表达式' : '折叠子表达式'"
        @click.stop="$emit('toggleCollapse', path)"
      >
        <i :class="collapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-down'" />
        <span v-if="collapsed" class="canvas-collapse__count">{{ descendantCount }}</span>
      </button>
      <span v-else class="canvas-collapse-spacer" />
      <div
        type="button"
        class="canvas-node"
        :class="{ 'canvas-node--selected': isSelected(path), 'canvas-node--empty': !node }"
        role="button"
        :tabindex="isSelected(path) ? 0 : -1"
        :draggable="!!node"
        @click.stop="$emit('select', path)"
        @keydown.tab="handleTab"
        @dragstart.stop="onDragStart"
        @dragover.prevent
        @drop.stop.prevent="onDrop"
      >
        <template v-if="node">
          <span class="canvas-kind">{{ kindName(node.kind) }}</span>
          <div v-if="editingManual" class="canvas-inline-editor" @click.stop>
            <template v-if="node.kind === 'LITERAL'">
              <el-select
                :value="node.valueType || expectedType || 'STRING'"
                size="mini"
                popper-class="expression-editor-select-popper"
                @input="$emit('patchNode', { path: path.slice(), fields: { valueType: $event } })"
              >
                <el-option v-for="type in valueTypes" :key="type.value" :label="type.label" :value="type.value" />
              </el-select>
              <el-input
                ref="manualInput"
                :value="node.value"
                size="mini"
                placeholder="请输入阈值"
                @input="$emit('patchNode', { path: path.slice(), fields: { value: $event } })"
              />
            </template>
            <template v-else>
              <el-input
                ref="manualInput"
                class="canvas-path-editor"
                :value="node.value"
                size="mini"
                placeholder="例如 request.customer.age"
                @input="$emit('manualInput', { path: path.slice(), value: $event })"
                @keyup.enter.native="$emit('resolvePath', path.slice())"
                @blur="$emit('resolvePath', path.slice())"
              />
              <div v-if="candidatePathKey === pathKey && pathCandidates.length" class="canvas-path-candidates">
                <button
                  v-for="candidate in pathCandidates"
                  :key="candidateKey(candidate)"
                  type="button"
                  @mousedown.prevent
                  @click="$emit('selectPathCandidate', { path: path.slice(), candidate })"
                >
                  <span>{{ candidateLabel(candidate) }}</span><code>{{ candidateCode(candidate) }}</code>
                </button>
              </div>
            </template>
          </div>
          <strong v-else>{{ summary(node) }}</strong>
        </template>
        <template v-else><i class="el-icon-plus" /> 点击此位置后从左侧添加内容</template>
      </div>
      <div v-if="isSelected(path) && node" class="canvas-node-actions" @click.stop>
        <button type="button" title="上移" @click="move(-1)"><i class="el-icon-top" /></button>
        <button type="button" title="下移" @click="move(1)"><i class="el-icon-bottom" /></button>
        <button type="button" title="增加括号（Tab）" @click="$emit('indent', path.slice())"><i class="el-icon-right" /></button>
        <button type="button" title="取消括号（Shift+Tab）" @click="$emit('outdent', path.slice())"><i class="el-icon-back" /></button>
      </div>
    </div>
    <div v-if="node && children.length && !collapsed" class="canvas-children">
      <div v-for="entry in children" :key="entry.path.join('.')" class="canvas-child">
        <span v-if="entry.operator" class="canvas-edge-operator">{{ entry.operator }}</span>
        <span v-else class="canvas-edge-label">{{ entry.label }}</span>
        <expression-canvas
          :node="entry.value"
          :path="entry.path"
          :selected-path="selectedPath"
          :collapsed-path-keys="collapsedPathKeys"
          :expected-type="expectedType"
          :path-candidates="pathCandidates"
          :candidate-path-key="candidatePathKey"
          @select="$emit('select', $event)"
          @toggleCollapse="$emit('toggleCollapse', $event)"
          @patchNode="$emit('patchNode', $event)"
          @manualInput="$emit('manualInput', $event)"
          @resolvePath="$emit('resolvePath', $event)"
          @selectPathCandidate="$emit('selectPathCandidate', $event)"
          @indent="$emit('indent', $event)"
          @outdent="$emit('outdent', $event)"
          @move="$emit('move', $event)"
          @moveNode="$emit('moveNode', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { operandDisplay } from '@/utils/operand'
import { expressionChildEntries, expressionDescendantCount, expressionPathKey, pathsEqual } from './expressionTree'

export default {
  name: 'ExpressionCanvas',
  props: {
    node: { type: Object, default: null },
    path: { type: Array, default: () => [] },
    selectedPath: { type: Array, default: () => [] },
    collapsedPathKeys: { type: Array, default: () => [] },
    expectedType: { type: String, default: '' },
    pathCandidates: { type: Array, default: () => [] },
    candidatePathKey: { type: String, default: '' }
  },
  data() {
    return {
      valueTypes: [
        { label: '文本', value: 'STRING' },
        { label: '数字', value: 'NUMBER' },
        { label: '布尔', value: 'BOOLEAN' },
        { label: '日期', value: 'DATE' },
        { label: '日期时间', value: 'DATETIME' },
        { label: '数组', value: 'LIST' },
        { label: '字典', value: 'MAP' }
      ]
    }
  },
  computed: {
    children() { return expressionChildEntries(this.node, this.path) },
    collapsed() { return this.collapsedPathKeys.includes(expressionPathKey(this.path)) },
    descendantCount() { return expressionDescendantCount(this.node) },
    pathKey() { return expressionPathKey(this.path) },
    editingManual() { return this.isSelected(this.path) && this.node && ['LITERAL', 'PATH'].includes(this.node.kind) },
    showNodeRow() { return !(this.path.length === 0 && this.node && this.node.kind === 'OPERATION') }
  },
  watch: {
    editingManual(value) {
      if (value) this.focusManualInput()
    }
  },
  mounted() {
    if (this.editingManual) this.focusManualInput()
  },
  methods: {
    focusManualInput() {
      this.$nextTick(() => {
        if (this.$refs.manualInput && typeof this.$refs.manualInput.focus === 'function') this.$refs.manualInput.focus()
      })
    },
    isSelected(path) { return pathsEqual(path, this.selectedPath) },
    handleTab(event) {
      if (!this.isSelected(this.path) || !this.node) return
      event.preventDefault()
      event.stopPropagation()
      this.$emit(event.shiftKey ? 'outdent' : 'indent', this.path.slice())
    },
    move(offset) {
      this.$emit('move', { path: this.path.slice(), offset })
    },
    onDragStart(event) {
      if (!this.node || !event.dataTransfer) return
      const payload = JSON.stringify(this.path)
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('application/x-expression-path', payload)
      event.dataTransfer.setData('text/plain', payload)
    },
    onDrop(event) {
      if (!event.dataTransfer) return
      const payload = event.dataTransfer.getData('application/x-expression-path') || event.dataTransfer.getData('text/plain')
      try {
        const fromPath = JSON.parse(payload)
        if (Array.isArray(fromPath)) this.$emit('moveNode', { fromPath, toPath: this.path.slice() })
      } catch (e) {
        // Ignore unrelated native drag payloads.
      }
    },
    summary(node) { return operandDisplay(node) || '待配置' },
    kindName(kind) {
      return { LITERAL: '阈值', PATH: '路径', REFERENCE: '字段', FUNCTION: '方法', OPERATION: '运算', ACCESS: '取值', CAST: '转换', ARRAY: '数组', LIST_QUERY: '名单' }[kind] || kind
    },
    candidateKey(item) {
      const type = item._refType || item.refType || (item._ref && item._ref.refType) || ''
      const id = item._varId != null ? item._varId : (item.refId != null ? item.refId : item.id)
      return type + ':' + (id != null ? id : this.candidateCode(item))
    },
    candidateCode(item) { return item.varCode || item.refCode || item.code || '' },
    candidateLabel(item) { return item.varLabelText || item.varLabel || item.label || this.candidateCode(item) }
  }
}
</script>

<style scoped>
.expression-canvas-node { min-width: 240px; }
.canvas-node-row { display: flex; align-items: stretch; gap: 6px; }
.canvas-node { display: flex; min-width: 0; min-height: 48px; flex: 1; align-items: center; gap: 9px; padding: 9px 12px; border: 1px solid #ccd6e4; border-radius: 8px; background: #fff; color: #25344b; cursor: pointer; text-align: left; box-shadow: 0 2px 5px rgba(38, 57, 77, .05); }
.canvas-node--selected { border-color: #2878ff; box-shadow: 0 0 0 3px rgba(40, 120, 255, .12); }
.canvas-node--empty { border-style: dashed; color: #8794a7; }
.canvas-kind { flex: none; padding: 2px 5px; border-radius: 4px; background: #edf4ff; color: #2878ff; font-size: 11px; }
.canvas-node strong { min-width: 0; overflow: visible; font-weight: 500; overflow-wrap: anywhere; white-space: normal; }
.canvas-inline-editor { position: relative; display: flex; min-width: 0; flex: 1; align-items: center; gap: 6px; }
.canvas-inline-editor .el-select { width: 92px; flex: none; }
.canvas-inline-editor .el-input { min-width: 0; flex: 1; }
.canvas-node-actions { display: grid; width: 58px; flex: none; grid-template-columns: repeat(2, 26px); gap: 3px; }
.canvas-node-actions button { padding: 0; border: 1px solid #d8e1ec; border-radius: 4px; background: #fff; color: #607089; cursor: pointer; }
.canvas-node-actions button:hover { border-color: #2878ff; color: #2878ff; }
.canvas-collapse, .canvas-collapse-spacer { width: 24px; flex: none; }
.canvas-collapse { position: relative; padding: 0; border: 0; background: transparent; color: #607089; cursor: pointer; }
.canvas-collapse:hover { background: #edf5ff; color: #2878ff; }
.canvas-collapse__count { position: absolute; top: -7px; right: -7px; min-width: 18px; padding: 1px 4px; border-radius: 9px; background: #2878ff; color: #fff; font-size: 10px; line-height: 16px; }
.canvas-children { margin: 8px 0 0 26px; padding-left: 18px; border-left: 2px solid #dfe7f1; }
.canvas-child { position: relative; margin: 10px 0; }
.canvas-edge-label { display: block; margin-bottom: 4px; color: #7b8798; font-size: 11px; }
.canvas-edge-operator { display: inline-flex; min-width: 28px; justify-content: center; margin: 0 0 5px 30px; padding: 2px 7px; border-radius: 4px; background: #eef4ff; color: #2878ff; font-family: Consolas, monospace; font-size: 12px; font-weight: 700; }
.canvas-path-candidates { position: absolute; z-index: 5; top: calc(100% + 4px); right: 0; left: 0; padding: 6px; border: 1px solid #d7e3f2; border-radius: 6px; background: #fff; box-shadow: 0 8px 20px rgba(35, 55, 80, .14); }
.canvas-path-candidates button { display: flex; width: 100%; justify-content: space-between; gap: 10px; padding: 7px 8px; border: 0; background: transparent; color: #26364d; cursor: pointer; text-align: left; }
.canvas-path-candidates button:hover { background: #edf5ff; }
.canvas-path-candidates code { color: #718096; overflow-wrap: anywhere; }
</style>
