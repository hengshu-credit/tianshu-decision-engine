<template>
  <div class="async-state" :class="{ 'is-compact': compact }">
    <div v-if="loading" class="state-content state-loading">
      <i class="el-icon-loading" />
      <span>{{ loadingText }}</span>
    </div>
    <div v-else-if="error" class="state-content state-error">
      <i class="el-icon-warning-outline" />
      <span>{{ error }}</span>
      <el-button type="text" size="small" @click="retry">重新加载</el-button>
    </div>
    <el-empty v-else-if="empty" :description="emptyText" :image-size="compact ? 56 : 90" />
    <slot v-else />
  </div>
</template>

<script>
export default {
  name: 'AsyncState',
  props: {
    loading: { type: Boolean, default: false },
    loadingText: { type: String, default: '加载中...' },
    error: { type: String, default: '' },
    empty: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无数据' },
    compact: { type: Boolean, default: false }
  },
  methods: {
    retry() {
      this.$emit('retry')
    }
  }
}
</script>

<style scoped>
.state-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  color: #909399;
}
.is-compact .state-content {
  min-height: 72px;
}
.state-loading i {
  color: #2639e9;
  font-size: 22px;
}
.state-error {
  color: #f56c6c;
}
</style>
