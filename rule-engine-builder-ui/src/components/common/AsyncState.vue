<template>
  <div class="async-state" :class="{ 'is-compact': compact }">
    <div v-if="loading" class="state-content state-loading">
      <el-icon><el-icon-loading /></el-icon>
      <span>{{ loadingText }}</span>
    </div>
    <div v-else-if="error" class="state-content state-error">
      <el-icon><el-icon-warning-outline /></el-icon>
      <span>{{ error }}</span>
      <el-button link size="small" @click="retry">重新加载</el-button>
    </div>
    <el-empty
      v-else-if="empty"
      :description="emptyText"
      :image-size="compact ? 56 : 90"
    />
    <slot v-else />
  </div>
</template>

<script>
import {
  Loading as ElIconLoading,
  Warning as ElIconWarningOutline,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
export default {
  components: {
    ElIconLoading,
    ElIconWarningOutline,
  },
  name: 'AsyncState',
  props: {
    loading: { type: Boolean, default: false },
    loadingText: { type: String, default: '加载中...' },
    error: { type: String, default: '' },
    empty: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无数据' },
    compact: { type: Boolean, default: false },
  },
  methods: {
    retry() {
      $emit(this, 'retry')
    },
  },
  emits: ['retry'],
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
