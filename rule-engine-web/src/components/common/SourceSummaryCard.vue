<template>
  <div class="source-summary-card">
    <div v-if="title" class="source-summary-title">{{ title }}</div>
    <div v-if="description" class="source-summary-desc">{{ description }}</div>
    <div class="source-summary-grid">
      <div v-for="item in rows" :key="item.key || item.label" class="source-summary-item">
        <div class="source-summary-label">{{ item.label }}</div>
        <div class="source-summary-value"><slot name="value" :item="item">{{ item.value }}</slot></div>
      </div>
    </div>
  </div>
</template>
<script setup>
defineProps({
  title: { type: String, default: '' }, description: { type: String, default: '' },
  rows: { type: Array, required: true },
})
</script>
<style scoped>
.source-summary-card {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--tianshu-info-border);
  background: var(--tianshu-info-bg);
  border-radius: 6px;
}
.source-summary-title {
  color: var(--tianshu-info-text);
  font-weight: 700;
  margin-bottom: 4px;
}
.source-summary-desc {
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 12px;
}
.source-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.source-summary-item {
  background: var(--tianshu-bg-surface);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  padding: 8px 10px;
  min-width: 0;
}
.source-summary-label {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  margin-bottom: 4px;
}
.source-summary-value {
  color: var(--tianshu-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-all;
}
</style>
