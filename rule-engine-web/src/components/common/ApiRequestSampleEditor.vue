<template>
  <el-collapse class="api-request-sample-editor">
    <el-collapse-item :title="title">
      <p class="field-help">粘贴供应商请求 JSON，生成可选路径；选定路径后单独配置取值，不自动复制样例值。</p>
      <monaco-editor v-model:value="text" language="json" height="160px" />
      <el-button @click="apply">导入请求字段目录</el-button>
      <p v-if="error" role="alert">{{ error }}</p>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
import MonacoEditor from '@/components/MonacoEditor.vue'

export default {
  name: 'ApiRequestSampleEditor',
  components: { MonacoEditor },
  props: { value: { default: undefined }, title: { type: String, default: '请求样例与字段目录' } },
  emits: ['update:value'],
  data: () => ({ text: '{}', error: '', sourceText: '' }),
  watch: {
    value: { immediate: true, deep: true, handler(value) {
      const source = JSON.stringify(value === undefined ? {} : value, null, 2)
      if (source !== this.sourceText) { this.sourceText = source; this.text = source; this.error = '' }
    } },
  },
  methods: {
    apply() {
      try { const sample = JSON.parse(this.text); this.error = ''; this.$emit('update:value', sample) }
      catch { this.error = '请输入合法 JSON 请求样例' }
    },
  },
}
</script>

<style scoped>
.field-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
[role="alert"] { color: var(--el-color-danger); }
</style>
