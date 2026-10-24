<template>
  <div class="expression-canvas-node" :class="{ 'expression-canvas-node--root-operation': !path.length && node?.kind === 'OPERATION' }">
    <div v-if="showNodeRow" class="canvas-node-row" @click.self="selectNode">
      <button
        v-if="node && children.length"
        type="button"
        class="canvas-collapse"
        :title="collapsed ? '展开子表达式' : '折叠子表达式'"
        :aria-label="collapsed ? '展开子表达式' : '折叠子表达式'"
        @click.stop="$emit('toggleCollapse', path)"
      >
        <app-icon :name="collapsed ? 'ArrowRight' : 'ArrowDown'" />
        <span v-if="collapsed" class="canvas-collapse__count">{{
          descendantCount
        }}</span>
      </button>
      <span v-else class="canvas-collapse-spacer" />
      <div
        type="button"
        class="canvas-node"
        :data-expression-path="pathKey"
        :class="{
          'canvas-node--selected': isSelected(path),
          'canvas-node--empty': !node,
        }"
        role="button"
        :tabindex="isSelected(path) ? 0 : -1"
        :draggable="!!node"
        @click.stop="selectNode"
        @keydown="handleNodeKeydown"
        @dragstart.stop="onDragStart"
        @dragover.prevent
        @drop.stop.prevent="onDrop"
      >
        <div class="canvas-node-content">
        <template v-if="node">
          <div v-if="editingManual" class="canvas-inline-editor" @click.stop>
            <template v-if="node.kind === 'LITERAL'">
              <el-select
                :model-value="node.valueType || expectedType || 'STRING'"
                size="small"
                :popper-class="fieldPickerEnabled ? '' : 'expression-editor-select-popper'"
                @update:model-value="
                  $emit('patchNode', {
                    path: path.slice(),
                    fields: { valueType: $event },
                  })
                "
              >
                <el-option
                  v-for="type in valueTypes"
                  :key="type.value"
                  :label="type.label"
                  :value="type.value"
                />
              </el-select>
              <monaco-editor v-if="['LIST', 'MAP', 'OBJECT'].includes(node.valueType)" class="canvas-json-editor" :value="typeof node.value === 'string' ? node.value : JSON.stringify(node.value ?? (node.valueType === 'LIST' ? [] : {}), null, 2)" language="json" height="130px" @input="$emit('patchNode', { path: path.slice(), fields: { value: $event } })" />
              <el-input v-else
                ref="manualInput"
                :model-value="node.value"
                size="small"
                placeholder="请输入阈值"
                @update:model-value="
                  $emit('patchNode', {
                    path: path.slice(),
                    fields: { value: $event },
                  })
                "
              />
            </template>
            <template v-else>
              <el-input
                ref="manualInput"
                class="canvas-path-editor"
                :model-value="node.value"
                size="small"
                placeholder="例如 request.customer.age"
                @update:model-value="
                  $emit('manualInput', { path: path.slice(), value: $event })
                "
                @keyup.enter="$emit('resolvePath', path.slice())"
                @blur="$emit('resolvePath', path.slice())"
              />
              <div
                v-if="candidatePathKey === pathKey && pathCandidates.length"
                class="canvas-path-candidates"
              >
                <button
                  v-for="candidate in pathCandidates"
                  :key="candidateKey(candidate)"
                  type="button"
                  @mousedown.prevent
                  @click="
                    $emit('selectPathCandidate', {
                      path: path.slice(),
                      candidate,
                    })
                  "
                >
                  <span>{{ candidateLabel(candidate) }}</span
                  ><code>{{ candidateCode(candidate) }}</code>
                </button>
              </div>
            </template>
          </div>
          <content-reference v-else :value="node" :options="vars" wrap />
          <expression-node-options v-if="fieldPickerEnabled" :node="node" :list-options="listOptions" @patch="$emit('patchNode', { path: path.slice(), fields: $event })" />
        </template>
        <template v-else
          ><el-icon><el-icon-plus /></el-icon>
          {{ fieldPickerEnabled ? '点击选择字段、阈值或方法' : '点击此位置后从左侧添加内容' }}</template
        >
        </div>
      <div
        class="canvas-node-actions"
        @click.stop
      >
        <button
          type="button"
          class="canvas-node-delete"
          title="删除当前项（Backspace）"
          aria-label="删除节点"
          @click="$emit('remove', path.slice())"
        >
          <app-icon name="Delete" />
        </button>
      </div>
      </div>
    </div>
    <div v-if="node && (children.length || appendChildren) && !collapsed" class="canvas-children">
      <div
        v-for="entry in children"
        :key="entry.path.join('.')"
        class="canvas-child"
        :class="{ 'canvas-child--operation': node.kind === 'OPERATION' }"
      >
        <div v-if="entry.operator" class="canvas-operator-row">
        <el-dropdown
          trigger="click"
          :popper-class="fieldPickerEnabled ? '' : 'expression-editor-select-popper'"
          @command="changeOperator(entry, $event)"
        >
          <button
            type="button"
            class="canvas-edge-operator"
            :aria-label="'修改运算符 ' + entry.operator"
            title="修改运算符"
            @keydown="handleOperatorKeydown(entry, $event)"
          >
            <span>运算符</span><strong>{{ operatorLabel(entry.operator) }}</strong><code>{{ entry.operator }}</code><span class="canvas-operator-hint">{{ entry.noValue ? '无需右值' : '选择运算方式' }}</span><app-icon name="ArrowDown" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="operator in operatorOptions(entry)" :key="operator.value" :command="operator.value">
                {{ operator.label }} <code>{{ operator.value }}</code>
              </el-dropdown-item>
              <el-dropdown-item command="remove" divided>删除运算符及后一项</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
        <span v-else-if="node.kind !== 'OPERATION'" class="canvas-edge-label">{{ entry.label }}</span>
        <expression-canvas v-if="!entry.noValue"
          :node="entry.value"
          :path="entry.path"
          :selected-path="selectedPath"
          :collapsed-path-keys="collapsedPathKeys"
          :expected-type="expectedType"
          :path-candidates="pathCandidates"
          :candidate-path-key="candidatePathKey"
          :field-picker-enabled="fieldPickerEnabled"
          :allow-operations="allowOperations"
          :append-expression="['FUNCTION', 'ACCESS', 'CAST'].includes(node.kind)"
          :list-options="listOptions"
          :vars="vars"
          @requestField="$emit('requestField', $event)"
          @appendRow="$emit('appendRow', $event)"
          @select="$emit('select', $event)"
          @toggleCollapse="$emit('toggleCollapse', $event)"
          @patchNode="$emit('patchNode', $event)"
          @manualInput="$emit('manualInput', $event)"
          @resolvePath="$emit('resolvePath', $event)"
          @selectPathCandidate="$emit('selectPathCandidate', $event)"
          @indent="$emit('indent', $event)"
          @outdent="$emit('outdent', $event)"
          @move="$emit('move', $event)"
          @navigate="$emit('navigate', $event)"
          @deleteKey="$emit('deleteKey', $event)"
          @moveNode="$emit('moveNode', $event)"
          @remove="$emit('remove', $event)"
          @replaceOperator="$emit('replaceOperator', $event)"
          @operatorKey="$emit('operatorKey', $event)"
        />
      </div>
      <button v-if="appendChildren" type="button" class="canvas-node-append" @click.stop="$emit('appendRow', path.slice())"><el-icon><el-icon-plus /></el-icon>{{ node.kind === 'ARRAY' ? '添加元素' : '添加运算项' }}</button>
    </div>
    <button v-if="fieldPickerEnabled && allowOperations && (!path.length || appendExpression) && node && !['OPERATION', 'ARRAY'].includes(node.kind)" type="button" class="canvas-node-append" @click.stop="$emit('appendRow', path.slice())"><el-icon><el-icon-plus /></el-icon>添加运算项</button>
  </div>
</template>

<script>
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import ContentReference from '@/components/common/ContentReference.vue'
import { operationLabel } from '@/utils/contentOperation'
import MonacoEditor from '@/components/MonacoEditor.vue'
import ExpressionNodeOptions from './ExpressionNodeOptions.vue'
import {
  expressionChildEntries,
  expressionDescendantCount,
  expressionPathKey,
  pathsEqual,
  expressionOperatorOptions,
} from './expressionTree'

export default {
  components: {
    MonacoEditor,
    ContentReference,
    ExpressionNodeOptions,
    ElIconPlus,
  },
  name: 'ExpressionCanvas',
  props: {
    node: { type: Object, default: null },
    path: { type: Array, default: () => [] },
    selectedPath: { type: Array, default: () => [] },
    collapsedPathKeys: { type: Array, default: () => [] },
    expectedType: { type: String, default: '' },
    pathCandidates: { type: Array, default: () => [] },
    candidatePathKey: { type: String, default: '' },
    fieldPickerEnabled: Boolean,
    allowOperations: { type: Boolean, default: true },
    appendExpression: Boolean,
    listOptions: { type: Array, default: () => [] },
    vars: { type: Array, default: () => [] },
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
        { label: '字典', value: 'MAP' },
        { label: '对象 JSON', value: 'OBJECT' },
      ],
    }
  },
  computed: {
    appendChildren() { return this.fieldPickerEnabled && (this.node?.kind === 'ARRAY' || this.allowOperations && this.node?.kind === 'OPERATION') },
    children() {
      return expressionChildEntries(this.node, this.path)
    },
    collapsed() {
      return this.collapsedPathKeys.includes(expressionPathKey(this.path))
    },
    descendantCount() {
      return expressionDescendantCount(this.node)
    },
    pathKey() {
      return expressionPathKey(this.path)
    },
    editingManual() {
      return (
        !this.fieldPickerEnabled &&
        this.isSelected(this.path) &&
        this.node &&
        ['LITERAL', 'PATH'].includes(this.node.kind)
      )
    },
    selected() {
      return this.isSelected(this.path)
    },
    showNodeRow() {
      return !(
        this.path.length === 0 &&
        this.node &&
        this.node.kind === 'OPERATION'
      )
    },
  },
  watch: {
    editingManual(value) {
      if (value) this.focusManualInput()
    },
    selected(value) {
      if (value && !this.editingManual) this.focusCard()
    },
  },
  mounted() {
    if (this.editingManual) this.focusManualInput()
    else if (this.selected) this.focusCard()
  },
  methods: {
    operatorLabel: operationLabel,
    operatorOptions(entry) { return expressionOperatorOptions(this.node, entry.termIndex) },
    focusCard() {
      this.$nextTick(() => this.$el.querySelector('.canvas-node--selected')?.focus())
    },
    selectNode(event) {
      this.$emit('select', this.path.slice())
      event.currentTarget.focus()
      if (this.fieldPickerEnabled) this.$emit('requestField', this.path.slice())
    },
    handleNodeKeydown(event) {
      if (!this.selected || event.isComposing || event.ctrlKey || event.metaKey || event.repeat) return
      if (event.target.closest('input, textarea, select, button, [contenteditable="true"], [role="combobox"]')) {
        if (event.key === 'Escape') {
          event.preventDefault()
          event.stopPropagation()
          event.currentTarget.focus()
        }
        return
      }
      if (['ArrowUp', 'ArrowDown'].includes(event.key)) {
        event.preventDefault()
        event.stopPropagation()
        this.$emit(event.altKey ? 'move' : 'navigate', { path: this.path.slice(), offset: event.key === 'ArrowUp' ? -1 : 1 })
      } else if (event.altKey) return
      else if (event.key === 'Tab') this.handleTab(event)
      else if (['Enter', 'F2'].includes(event.key)) {
        event.preventDefault()
        event.stopPropagation()
        this.selectNode(event)
        if (this.editingManual) this.focusManualInput()
      }
      else this.handleStructureKey(event, this.path)
    },
    handleOperatorKeydown(entry, event) {
      this.handleStructureKey(event, entry.path, true)
    },
    handleStructureKey(event, path, replace = false) {
      if (event.isComposing || event.ctrlKey || event.metaKey || event.altKey || event.repeat) return
      if (['Delete', 'Backspace'].includes(event.key)) {
        event.preventDefault()
        event.stopPropagation()
        this.$emit('deleteKey', { path: path.slice(), key: event.key, operator: replace })
      } else if (['+', '-', '*', '/', '%', '|', '&', '=', '!', '<', '>'].includes(event.key)) {
        event.preventDefault()
        event.stopPropagation()
        this.$emit('operatorKey', { path: path.slice(), key: event.key, replace })
      }
    },
    changeOperator(entry, operator) {
      if (operator === 'remove') this.$emit('remove', entry.path.slice())
      else this.$emit('replaceOperator', { path: entry.path.slice(), operator })
    },
    focusManualInput() {
      this.$nextTick(() => {
        if (
          this.$refs.manualInput &&
          typeof this.$refs.manualInput.focus === 'function'
        )
          this.$refs.manualInput.focus()
      })
    },
    isSelected(path) {
      return pathsEqual(path, this.selectedPath)
    },
    handleTab(event) {
      if (!this.isSelected(this.path)) return
      event.preventDefault()
      event.stopPropagation()
      this.$emit(event.shiftKey ? 'outdent' : 'indent', this.path.slice())
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
      const payload =
        event.dataTransfer.getData('application/x-expression-path') ||
        event.dataTransfer.getData('text/plain')
      try {
        const fromPath = JSON.parse(payload)
        if (Array.isArray(fromPath))
          this.$emit('moveNode', { fromPath, toPath: this.path.slice() })
      } catch (e) {
        // Ignore unrelated native drag payloads.
      }
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
  emits: [
    'appendRow',
    'requestField',
    'toggleCollapse',
    'select',
    'patchNode',
    'manualInput',
    'resolvePath',
    'selectPathCandidate',
    'indent',
    'outdent',
    'move',
    'navigate',
    'deleteKey',
    'moveNode',
    'remove',
    'replaceOperator',
    'operatorKey',
  ],
}
</script>

<style scoped>
.expression-canvas-node {
  min-width: 240px;
}
.canvas-node-row {
  display: flex;
  align-items: stretch;
  gap: 6px;
}
.canvas-node {
  display: flex;
  min-width: 0;
  min-height: 36px;
  flex: 1;
  align-items: stretch;
  border: 1px solid var(--tianshu-border);
  border-radius: 0;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-primary);
  cursor: pointer;
  text-align: left;
}
.canvas-node-content { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-width: 0; flex: 1; padding: 8px 10px; }
.canvas-node-append { display: flex; align-items: center; justify-content: center; gap: 6px; width: calc(100% - 30px); min-height: 36px; margin: 10px 0 0 30px; border: 1px dashed var(--tianshu-border); border-radius: 0; background: transparent; color: var(--tianshu-text-secondary); cursor: pointer; font: inherit; }
.canvas-node-append:hover, .canvas-node-append:focus-visible { color: var(--el-color-primary); border-color: var(--el-color-primary); background: var(--tianshu-designer-accent-bg); }
.expression-canvas-node--root-operation > .canvas-children { margin: 0; padding: 0; border: 0; }
.canvas-node--selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px rgba(var(--el-color-primary-rgb), 0.18);
}
.canvas-node--empty {
  border-style: dashed;
  color: var(--tianshu-text-tertiary);
}

.canvas-node strong {
  min-width: 0;
  overflow: visible;
  font-weight: 500;
  overflow-wrap: anywhere;
  white-space: normal;
}
.canvas-inline-editor {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
}
.canvas-inline-editor .el-select {
  width: 92px;
  flex: none;
}
.canvas-inline-editor .el-input {
  min-width: 0;
  flex: 1;
}
.canvas-json-editor { flex: 1; min-width: 180px; }
.canvas-node-actions {
  display: flex;
  width: 32px;
  flex: none;
  border-left: 1px solid var(--tianshu-border-subtle);
}
.canvas-node-actions button {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--tianshu-text-secondary);
  cursor: pointer;
}
.canvas-node-actions button:hover {
  background: var(--tianshu-danger-bg);
}
.canvas-node-actions .canvas-node-delete {
  width: 100%;
  min-height: 32px;
  color: var(--el-color-danger);
}
.canvas-collapse,
.canvas-collapse-spacer {
  width: 24px;
  flex: none;
}
.canvas-collapse {
  position: relative;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--tianshu-text-secondary);
  cursor: pointer;
}
.canvas-collapse:hover {
  background: var(--tianshu-designer-accent-bg);
  color: var(--el-color-primary);
}
.canvas-collapse__count {
  position: absolute;
  top: -7px;
  right: -7px;
  min-width: 18px;
  padding: 1px 4px;
  border-radius: 9px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
}
.canvas-children {
  margin: 8px 0 0 26px;
  padding-left: 18px;
  border-left: 2px solid var(--tianshu-border-subtle);
}
.canvas-child {
  position: relative;
  margin: 10px 0;
}
.canvas-child--operation { margin: 0; }
.canvas-operator-row { display: flex; align-items: stretch; min-height: 44px; margin: 8px 0 8px 30px; }
.canvas-operator-row > .el-dropdown { display: flex; width: 100%; }
.canvas-operator-hint { margin-left: auto; font-weight: 400; color: var(--tianshu-text-secondary); }

.canvas-edge-label {
  display: block;
  margin-bottom: 4px;
  color: var(--tianshu-text-tertiary);
  font-size: 11px;
}
.canvas-edge-operator {
  display: inline-flex;
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  justify-content: flex-start;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 0 12px;
  border-radius: 0;
  border: 1px solid var(--tianshu-border);
  background: var(--tianshu-designer-accent-bg);
  color: var(--el-color-primary);
  font-family: Consolas, monospace;
  font-size: 12px;
  line-height: 20px;
  font-weight: 700;
  cursor: pointer;
}
.canvas-edge-operator:hover,
.canvas-edge-operator:focus-visible {
  border-color: var(--el-color-primary);
}
.canvas-path-candidates {
  position: absolute;
  z-index: 5;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  padding: 6px;
  border: 1px solid var(--tianshu-border);
  border-radius: 6px;
  background: var(--tianshu-bg-surface);
  box-shadow: var(--tianshu-shadow-medium);
}
.canvas-path-candidates button {
  display: flex;
  width: 100%;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 8px;
  border: 0;
  background: transparent;
  color: var(--tianshu-text-primary);
  cursor: pointer;
  text-align: left;
}
.canvas-path-candidates button:hover {
  background: var(--tianshu-designer-accent-bg);
}
.canvas-path-candidates code {
  color: var(--tianshu-text-secondary);
  overflow-wrap: anywhere;
}
</style>
