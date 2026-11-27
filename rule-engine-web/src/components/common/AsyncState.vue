<template>
  <div class="async-state" :class="{ 'is-compact': compact }">
    <div v-if="loading" class="state-content state-loading" role="status" :aria-label="loadingText">
      <el-icon-loading size="48px" />
      <span class="state-loading-label">{{ loadingText }}</span>
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
import ElIconLoading from '@/components/common/AppLoadingIcon.vue'
import {
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
  color: var(--tianshu-text-tertiary);
}
.is-compact .state-content {
  min-height: 72px;
}
.state-loading { position: relative; }
.state-loading-label {
  position: absolute;
  top: calc(50% + 26px);
  font-size: 12px;
}
.is-compact .state-loading { min-height: 96px; }
.state-error {
  color: #f56c6c;
}
</style>
