<template>
  <component :is="dashboard" v-if="dashboard" />
  <div v-else class="uiue-list-page dashboard-entry">
    <div class="module-hint"><span class="hint-title">数据看板</span></div>
    <async-state
      compact
      :loading="!failed"
      loading-text="正在加载看板内容"
      :error="failed ? '看板内容下载失败，请重新加载页面' : ''"
      @retry="reload"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, shallowRef } from 'vue'
import AsyncState from '@/components/common/AsyncState.vue'

// 路由仅等待轻量入口。看板专属图表和地图依赖在进入页签后加载，侧栏可立即操作。
const dashboard = shallowRef(null)
const failed = ref(false)
onMounted(async () => {
  try {
    dashboard.value = (await import('./DashboardHome.vue')).default
  } catch {
    failed.value = true
  }
})
// 更新发布后旧资源可能已经失效，重新读取入口和当前版本的资源地址。
function reload() { window.location.reload() }
</script>
