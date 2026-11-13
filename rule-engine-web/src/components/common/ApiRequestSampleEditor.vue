<template>
  <el-collapse class="api-request-sample-editor">
    <el-collapse-item :title="title">
      <p class="field-help">粘贴{{ kind }} JSON 以生成可选字段目录。</p>
      <textarea v-model="text" class="el-textarea__inner" rows="8" :aria-label="kind + ' JSON 样例'" spellcheck="false" wrap="off" />
      <el-button @click="apply">导入{{ kind }}字段目录</el-button>
      <p v-if="error" role="alert">{{ error }}</p>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
export default {
  name: 'ApiRequestSampleEditor',
  props: { value: { default: undefined }, kind: { type: String, default: '请求' }, title: { type: String, default: '请求样例与字段目录' } },
  emits: ['update:value'],
  data: () => ({ text: '{}', error: '', sourceText: '' }),
  watch: {
    value: { immediate: true, handler(value) {
      const source = JSON.stringify(value === undefined ? {} : value, null, 2)
      if (source !== this.sourceText) { this.sourceText = source; this.text = source; this.error = '' }
    } },
  },
  methods: {
    apply() {
      try { const sample = JSON.parse(this.text); this.error = ''; this.$emit('update:value', sample && typeof sample === 'object' ? Object.freeze(sample) : sample) }
      catch { this.error = `请输入合法 JSON ${this.kind}样例` }
    },
  },
}
</script>

<style scoped>
.field-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
[role="alert"] { color: var(--el-color-danger); }
</style>
