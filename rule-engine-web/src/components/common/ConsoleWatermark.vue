<template>
  <teleport :to="target">
    <div v-if="visible" class="console-watermark" aria-hidden="true">
      <watermark-pattern :config="config" :user="user" />
    </div>
  </teleport>
</template>

<script>
import WatermarkPattern from '@/components/common/WatermarkPattern.vue'
import { getCurrentUser, hasPermission } from '@/security/permissionState'
import { PUBLIC_AUTH_PATHS } from '@/router/authGuard'
import { systemBrand } from '@/system/systemBrand'
import { getWatermarkPreview } from '@/system/watermarkPreview'

export default {
  name: 'ConsoleWatermark',
  components: { WatermarkPattern },
  data() {
    return { brand: systemBrand, target: document.fullscreenElement || 'body' }
  },
  computed: {
    user() { return getCurrentUser() },
    config() { return this.$route.path === '/system/settings' && hasPermission('system:edit') ? getWatermarkPreview(this.user) || this.brand : this.brand },
    visible() { return this.config.watermarkEnabled && Boolean(this.user?.username) && !PUBLIC_AUTH_PATHS.has(this.$route.path) },
  },
  mounted() {
    document.addEventListener('fullscreenchange', this.syncFullscreen)
  },
  beforeUnmount() {
    document.removeEventListener('fullscreenchange', this.syncFullscreen)
  },
  methods: {
    syncFullscreen() { this.target = document.fullscreenElement || 'body' },
  },
}
</script>

<style scoped>
.console-watermark {
  position: fixed;
  inset: 0;
  z-index: 2147483647;
  pointer-events: none;
  overflow: hidden;
}
</style>
