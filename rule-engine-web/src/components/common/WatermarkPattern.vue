<template>
  <el-watermark v-bind="options" style="width: 100%; height: 100%"><slot /></el-watermark>
</template>

<script>
import { createWatermarkOptions } from '@/system/watermark'

export default {
  name: 'WatermarkPattern',
  props: { config: { type: Object, required: true }, user: { type: Object, default: null } },
  data: () => ({ dark: document.documentElement.dataset.theme === 'dark' }),
  computed: { options() { return createWatermarkOptions(this.config, this.user, this.dark) } },
  mounted() { window.addEventListener('tianshu-theme-change', this.syncTheme) },
  beforeUnmount() { window.removeEventListener('tianshu-theme-change', this.syncTheme) },
  methods: { syncTheme() { this.dark = document.documentElement.dataset.theme === 'dark' } },
}
</script>
