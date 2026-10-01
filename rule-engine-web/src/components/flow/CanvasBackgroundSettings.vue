<template>
  <el-popover placement="bottom-end" width="320" trigger="click">
    <div class="canvas-settings">
      <div class="canvas-settings__section">
        <strong>网格设置</strong>
        <label><span>网格吸附</span><el-switch :model-value="snapGridEnabled" @change="change({ snapGridEnabled: $event })" /></label>
        <label><span>网格可见</span><el-switch :model-value="gridVisible" @change="change({ gridVisible: $event })" /></label>
        <label><span>网格大小</span><el-input-number :model-value="gridSize" :min="5" :max="100" :step="5" controls-position="right" @change="change({ gridSize: $event || 20 })" /></label>
        <label><span>网格类型</span><el-select :model-value="gridType" @change="change({ gridType: $event })"><el-option label="点状" value="dot" /><el-option label="网格线" value="mesh" /></el-select></label>
        <label><span>网格宽度</span><el-input-number :model-value="gridThickness" :min="1" :max="6" :step="1" controls-position="right" @change="change({ gridThickness: $event || 1 })" /></label>
        <label><span>网格颜色</span><el-color-picker :model-value="gridColor || '#53617d'" show-alpha @change="change({ gridColor: $event || '' })" /></label>
      </div>
      <div class="canvas-settings__section">
        <strong>画布背景</strong>
        <label><span>启用自定义背景</span><el-switch :model-value="backgroundEnabled" @change="change({ backgroundEnabled: $event })" /></label>
        <label><span>背景透明度</span><el-slider :disabled="!backgroundEnabled" :model-value="backgroundOpacity" :min="0" :max="1" :step="0.05" @change="change({ backgroundOpacity: $event })" /></label>
        <label><span>背景颜色</span><el-color-picker :disabled="!backgroundEnabled" :model-value="backgroundColor || '#0f1629'" show-alpha @change="change({ backgroundColor: $event || '' })" /></label>
      </div>
      <div v-if="showEdgeAnimation" class="canvas-settings__section">
        <strong>选中动画</strong>
        <label><span>节点/边选中后开启边动画</span><el-switch :model-value="edgeAnimationEnabled" @change="change({ edgeAnimationEnabled: $event })" /></label>
      </div>
    </div>
    <template #reference>
      <el-button class="toolbar-canvas-settings" size="small" :icon="ElIconGrid">画布背景</el-button>
    </template>
  </el-popover>
</template>

<script>
import { markRaw } from 'vue'
import { Grid as ElIconGrid } from '@element-plus/icons-vue'
import { ElColorPicker } from 'element-plus'

export default {
  name: 'CanvasBackgroundSettings',
  components: { ElColorPicker },
  props: {
    gridVisible: { type: Boolean, default: true },
    snapGridEnabled: { type: Boolean, default: true },
    gridSize: { type: Number, default: 20 },
    gridType: { type: String, default: 'dot' },
    gridThickness: { type: Number, default: 1 },
    gridColor: { type: String, default: '' },
    backgroundOpacity: { type: Number, default: 1 },
    backgroundColor: { type: String, default: '' },
    backgroundEnabled: { type: Boolean, default: false },
    edgeAnimationEnabled: { type: Boolean, default: true },
    showEdgeAnimation: { type: Boolean, default: true },
  },
  emits: ['change'],
  data: () => ({ ElIconGrid: markRaw(ElIconGrid) }),
  methods: {
    change(patch) {
      this.$emit('change', patch)
    },
  },
}
</script>

<style scoped>
.canvas-settings { display: grid; gap: 14px; color: var(--tianshu-text-primary); }
.canvas-settings__section { display: grid; gap: 10px; padding-bottom: 12px; border-bottom: 1px solid var(--tianshu-border-subtle); }
.canvas-settings__section:last-child { padding-bottom: 0; border-bottom: 0; }
.canvas-settings__section strong { color: var(--tianshu-text-primary); font-size: 13px; }
.canvas-settings__section label { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 28px; color: var(--tianshu-text-secondary); font-size: 12px; }
.canvas-settings__section label > :deep(.el-input-number), .canvas-settings__section label > :deep(.el-select) { width: 132px; }
.canvas-settings__section label > :deep(.el-slider) { width: 132px; }
.canvas-settings__section label > :deep(.el-color-picker) { flex: none; }
</style>
