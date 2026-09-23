<template>
  <div class="edge-properties-editor">
    <el-form size="small" label-width="80px" class="prop-form">
      <el-form-item label="连接线类型">
        <el-select
          :model-value="modelValue.edgeLineType || ''"
          placeholder="跟随全局"
          style="width: 100%"
          @update:model-value="updateProperty('edgeLineType', $event)"
        >
          <el-option label="跟随全局" value="" />
          <el-option label="折线" value="polyline" />
          <el-option label="直线" value="line" />
          <el-option label="弧线" value="bezier" />
        </el-select>
      </el-form-item>
      <el-form-item label="分支标签">
        <el-input
          :model-value="modelValue.conditionName"
          placeholder="如：是、否、金额>500"
          @update:model-value="updateProperty('conditionName', $event)"
        />
      </el-form-item>
      <el-form-item label="优先级">
        <el-input-number
          :model-value="modelValue.priority"
          :precision="0"
          controls-position="right"
          @update:model-value="updateProperty('priority', $event)"
        />
        <span class="priority-hint">数值越小越先判断</span>
      </el-form-item>
    </el-form>
    <div class="section-title">
      <span>条件表达式</span>
      <el-radio-group :model-value="mode" size="small" @update:model-value="$emit('update:mode', $event)">
        <el-radio-button value="visual">可视化</el-radio-button>
        <el-radio-button value="script">脚本</el-radio-button>
      </el-radio-group>
    </div>
    <div v-if="mode === 'visual'" class="cond-builder">
      <div v-if="!modelValue.conditionExpr && !conditionRoot?.children?.length" class="hint-box">
        当前为默认分支；添加条件后按条件判断。
      </div>
      <condition-group-editor
        v-if="conditionRoot"
        :group="conditionRoot"
        :vars="vars"
        :functions="functions"
        :list-options="listOptions"
        :get-var-options-fn="getVarOptionsFn"
        :selected-vars="selectedVars"
        @changed="$emit('condition-change')"
      />
      <el-button type="primary" size="small" :icon="Check" class="generate-condition" @click="$emit('generate')">
        生成表达式
      </el-button>
      <div v-if="modelValue.conditionExpr" class="generated-expr"><code>{{ modelValue.conditionExpr }}</code></div>
    </div>
    <el-input
      v-else
      :model-value="modelValue.conditionExpr"
      type="textarea"
      :rows="3"
      placeholder="QLExpress 表达式，如：amount > 100000"
      class="mono-input"
      @update:model-value="updateProperty('conditionExpr', $event)"
    />
    <div class="hint-box condition-hint">条件为空表示默认分支（else）</div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Check } from '@element-plus/icons-vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'

export default {
  name: 'EdgePropertiesEditor',
  components: { ConditionGroupEditor },
  props: {
    modelValue: { type: Object, required: true },
    conditionRoot: { type: Object, default: null },
    mode: { type: String, default: 'visual' },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    getVarOptionsFn: { type: Function, default: null },
  },
  emits: ['update:modelValue', 'update:mode', 'condition-change', 'generate'],
  data() {
    return { Check: markRaw(Check) }
  },
  methods: {
    updateProperty(field, value) {
      if (field === 'priority' && !Number.isFinite(value)) return
      const next = { ...this.modelValue, [field]: value }
      if (field === 'conditionExpr') {
        // 手写脚本成为条件来源，不能在再次打开时被旧的可视化配置覆盖。
        next.conditionConfig = null
        next.leftVarId = next.leftRefType = next.rightVarId = next.rightRefType = null
      }
      this.$emit('update:modelValue', next)
    },
  },
}
</script>

<style lang="scss" scoped>
.edge-properties-editor { min-width: 0; }
.priority-hint { margin-left: 8px; font-size: 12px; color: var(--tianshu-text-tertiary); }
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
}
.section-title :deep(.el-radio-button__inner) { min-width: 72px; padding: 6px 12px; }
.section-title :deep(.el-radio-button__orig-radio:checked + .el-radio-button__inner) {
  background: var(--tianshu-brand-background);
  border-color: var(--el-color-primary);
  color: var(--tianshu-brand-foreground);
  box-shadow: none;
}
.generate-condition { width: 100%; margin-top: 8px; }
.generated-expr {
  margin-top: 8px;
  padding: 6px 8px;
  background: var(--tianshu-designer-accent-bg);
  border: 1px solid var(--tianshu-designer-accent-border);
  border-radius: 4px;
  code { font-family: 'Consolas', monospace; font-size: 12px; color: var(--el-color-primary); overflow-wrap: anywhere; }
}
.hint-box { padding: 6px 8px; font-size: 12px; line-height: 1.6; color: var(--tianshu-text-tertiary); background: var(--tianshu-bg-muted); border-radius: 4px; }
.condition-hint { margin-top: 6px; }
.mono-input :deep(textarea) { font-family: 'Consolas', 'Monaco', 'Courier New', monospace; font-size: 13px; line-height: 1.5; }
</style>
