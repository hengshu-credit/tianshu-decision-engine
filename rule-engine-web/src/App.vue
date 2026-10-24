<template>
  <div id="app">
    <router-view v-slot="{ Component }">
      <component :is="Component" v-if="Component" />
      <route-page-skeleton v-else-if="navigation.pendingRoute" :title="navigation.pendingRoute.meta.title" />
      <el-alert v-else-if="navigation.error" title="页面加载失败，请刷新重试" type="error" :closable="false" />
    </router-view>
    <console-watermark />
  </div>
</template>

<script>
import { navigationState } from '@/router/navigationState'
import RoutePageSkeleton from '@/components/common/RoutePageSkeleton.vue'
import ConsoleWatermark from '@/components/common/ConsoleWatermark.vue'

export default {
  name: 'App',
  components: { RoutePageSkeleton, ConsoleWatermark },
  data() { return { navigation: navigationState } },
}
</script>
