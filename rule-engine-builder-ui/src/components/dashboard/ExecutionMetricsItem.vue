<template>
  <div class="execution-metric-item" :data-tone="tone">
    <span>{{ label }}</span>
    <strong>{{ value }}</strong>
    <small v-if="detail">{{ detail }}</small>
  </div>
</template>

<script>
export default {
  name: 'ExecutionMetricsItem',
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
    detail: { type: String, default: '' },
    tone: { type: String, default: '' }
  }
}
</script>

<style scoped>
.execution-metric-item {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-bg-color);
}

.execution-metric-item span,
.execution-metric-item small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.execution-metric-item strong {
  color: var(--el-color-primary);
  font-size: 20px;
  line-height: 1.15;
}

.execution-metric-item[data-tone="warning"] strong { color: var(--el-color-warning); }
.execution-metric-item[data-tone="success"] strong { color: var(--el-color-success); }
</style>
