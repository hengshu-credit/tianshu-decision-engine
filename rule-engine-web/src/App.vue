<template>
  <div id="app">
    <router-view v-slot="{ Component }">
      <component :is="Component" v-if="Component" />
      <div v-else-if="navigation.pendingRoute" class="app-bootstrap" role="status" aria-label="正在加载系统">
        <span class="tianshu-loading" aria-hidden="true" />
      </div>
      <el-alert v-else-if="navigation.error" title="页面加载失败，请刷新重试" type="error" :closable="false" />
    </router-view>
    <console-watermark />
  </div>
</template>

<script>
import { navigationState } from '@/router/navigationState'
import ConsoleWatermark from '@/components/common/ConsoleWatermark.vue'

export default {
  name: 'App',
  components: { ConsoleWatermark },
  data() { return { navigation: navigationState } },
}
</script>
