<template>
  <div class="threshold-list" :class="{ 'is-compact': compact }">
    <div v-for="(threshold, index) in thresholds" :key="index" class="threshold-item">
      <div class="thresh-color-bar" :style="{ background: thresholdColor(index) }" />
      <div class="thresh-range">
        <el-input-number
          :model-value="threshold.min"
          :aria-label="`等级 ${index + 1} 最小分数`"
          size="small"
          :min="0"
          :controls="false"
          style="width: 100px"
          @update:model-value="value => $emit('change-min', threshold, value)"
        />
        <span class="thresh-sep">≤ 分数 &lt;</span>
        <el-input-number
          :model-value="threshold.max"
          :aria-label="`等级 ${index + 1} 最大分数`"
          size="small"
          :min="threshold.min"
          :controls="false"
          style="width: 100px"
          @update:model-value="value => $emit('change-max', threshold, value)"
        />
      </div>
      <div class="thresh-result"><slot name="result" :threshold="threshold" /></div>
      <el-tag :color="thresholdColor(index)" effect="dark" size="small" class="thresh-badge">
        {{ threshold.result || '等级 ' + (index + 1) }}
      </el-tag>
      <el-button link size="small" :icon="ElIconDelete" class="btn-delete"
        :aria-label="`删除等级 ${index + 1}`" @click="$emit('remove', index)" />
    </div>
    <slot v-if="thresholds.length === 0" name="empty" />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Delete as ElIconDelete } from '@element-plus/icons-vue'

const THRESHOLD_COLORS = [
  '#52c41a', 'var(--el-color-primary)', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96',
]

export default {
  name: 'ScoreThresholdEditor',
  props: { thresholds: { type: Array, required: true }, compact: Boolean },
  emits: ['change-min', 'change-max', 'remove'],
  data: () => ({ ElIconDelete: markRaw(ElIconDelete) }),
  methods: {
    thresholdColor(index) { return THRESHOLD_COLORS[index % THRESHOLD_COLORS.length] },
  },
}
</script>

<style scoped>
.threshold-list { display: flex; flex-direction: column; gap: 10px; }
.threshold-list.is-compact { gap: 8px; margin-bottom: 12px; }
.threshold-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-muted);
}
:where(.is-compact) .threshold-item { gap: 10px; padding: 8px 10px; }
.thresh-color-bar { width: 4px; height: 36px; border-radius: 2px; flex-shrink: 0; }
.thresh-range { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.thresh-sep { font-size: 12px; color: #888; white-space: nowrap; }
:where(.is-compact) .thresh-sep { color: var(--tianshu-text-tertiary); }
.thresh-result { flex: 1; }
.thresh-badge { flex-shrink: 0; border-color: transparent !important; }
</style>
