<template>
  <section class="dashboard-section" :aria-label="title" :aria-busy="loading">
    <div class="dashboard-section__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="actions" />
    </div>
    <div v-if="loading && !loaded" class="dashboard-section__state" v-loading="true">正在加载</div>
    <div v-else-if="error" class="dashboard-section__state">
      <el-alert :title="error" type="error" :closable="false" show-icon />
      <el-button @click="$emit('retry')">重试本区域</el-button>
    </div>
    <div v-else v-loading="loading">
      <slot />
    </div>
  </section>
</template>

<script>
export default {
  name: 'DashboardSection',
  emits: ['retry'],
  props: {
    title: { type: String, required: true },
    description: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    loaded: { type: Boolean, default: false },
    error: { type: String, default: '' }
  }
}
</script>

<style scoped>
.dashboard-section {
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-bg-color);
  box-shadow: var(--tianshu-shadow-sm);
}

.dashboard-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.dashboard-section__header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 17px;
}

.dashboard-section__header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.dashboard-section__state {
  display: grid;
  min-height: 160px;
  place-content: center;
  gap: 12px;
  color: var(--el-text-color-secondary);
}
</style>
