<template>
  <div class="expression-canvas-node">
    <button
      type="button"
      class="canvas-node"
      :class="{ 'canvas-node--selected': isSelected(path), 'canvas-node--empty': !node }"
      @click.stop="$emit('select', path)"
    >
      <template v-if="node"><span class="canvas-kind">{{ kindName(node.kind) }}</span><strong>{{ summary(node) }}</strong></template>
      <template v-else><i class="el-icon-plus" /> 点击此位置后从左侧添加内容</template>
    </button>
    <div v-if="node && children.length" class="canvas-children">
      <div v-for="entry in children" :key="entry.path.join('.')" class="canvas-child">
        <span class="canvas-edge-label">{{ entry.label }}</span>
        <expression-canvas
          :node="entry.value"
          :path="entry.path"
          :selected-path="selectedPath"
          @select="$emit('select', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { operandDisplay } from '@/utils/operand'
import { expressionChildEntries, pathsEqual } from './expressionTree'

export default {
  name: 'ExpressionCanvas',
  props: {
    node: { type: Object, default: null },
    path: { type: Array, default: () => [] },
    selectedPath: { type: Array, default: () => [] }
  },
  computed: {
    children() { return expressionChildEntries(this.node, this.path) }
  },
  methods: {
    isSelected(path) { return pathsEqual(path, this.selectedPath) },
    summary(node) { return operandDisplay(node) || '待配置' },
    kindName(kind) {
      return { LITERAL: '阈值', PATH: '路径', REFERENCE: '字段', FUNCTION: '方法', OPERATION: '运算', ACCESS: '取值', CAST: '转换', ARRAY: '数组', LIST_QUERY: '名单' }[kind] || kind
    }
  }
}
</script>

<style scoped>
.expression-canvas-node { min-width: 190px; }
.canvas-node { display: flex; width: 100%; min-height: 48px; align-items: center; gap: 9px; padding: 9px 12px; border: 1px solid #ccd6e4; border-radius: 8px; background: #fff; color: #25344b; cursor: pointer; text-align: left; box-shadow: 0 2px 5px rgba(38, 57, 77, .05); }
.canvas-node--selected { border-color: #2878ff; box-shadow: 0 0 0 3px rgba(40, 120, 255, .12); }
.canvas-node--empty { border-style: dashed; color: #8794a7; }
.canvas-kind { flex: none; padding: 2px 5px; border-radius: 4px; background: #edf4ff; color: #2878ff; font-size: 11px; }
.canvas-node strong { overflow: hidden; font-weight: 500; text-overflow: ellipsis; }
.canvas-children { margin: 8px 0 0 26px; padding-left: 18px; border-left: 2px solid #dfe7f1; }
.canvas-child { position: relative; margin: 10px 0; }
.canvas-edge-label { display: block; margin-bottom: 4px; color: #7b8798; font-size: 11px; }
</style>
