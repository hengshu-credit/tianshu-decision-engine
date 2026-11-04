<template>
  <div class="ledger-markdown" :aria-busy="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="render">重试</el-button></el-alert>
    <div v-else class="vditor-reset ledger-markdown-content" v-html="html" />
  </div>
</template>
<script>
import { renderLedgerMarkdown } from '@/utils/ledgerMarkdown'
export default {
  name: 'LedgerMarkdown',
  props: { value: { type: String, default: '' } },
  data: () => ({ html: '', loading: false, error: '', generation: 0 }),
  watch: { value: { immediate: true, handler() { this.render() } } },
  beforeUnmount() { this.generation++ },
  methods: {
    async render() {
      const generation = ++this.generation
      this.loading = true; this.error = ''
      try { const html = await renderLedgerMarkdown(this.value); if (generation === this.generation) this.html = html }
      catch (error) { if (generation === this.generation) this.error = error.message || '正文加载失败' }
      finally { if (generation === this.generation) this.loading = false }
    },
  },
}
</script>
