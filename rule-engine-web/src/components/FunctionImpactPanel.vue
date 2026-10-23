<template>
  <section class="function-impact" aria-label="函数调用说明与影响">
    <p>按参数列表顺序传入值，返回一个所选类型的结果。请在说明中写明用途、输入单位、空值处理和输出含义。</p>
    <p v-if="form.funcCode">调用示意：<code>{{ form.funcCode }}({{ params.map((item, index) => item.label || item.name || `参数${index + 1}`).join('，') }})</code></p>
    <el-button size="small" :loading="loading" @click="check">检查签名与引用影响</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="stale" title="配置已变更，请重新检查签名与引用影响" type="warning" :closable="false" show-icon />
    <template v-if="report && !stale">
      <p>生效签名：<code>{{ report.beforeSignature }}</code></p>
      <p>本次签名：<code>{{ report.afterSignature }}</code></p>
      <p>{{ report.changes.length ? `需要复核：${report.changes.join('、')}` : '声明签名与实现内容未改变' }}</p>
      <p>{{ report.message }}</p>
      <ul v-if="report.consumers.length"><li v-for="item in report.consumers" :key="item.id">
        <el-button v-if="item.type === 'RULE'" link @click="$router.push('/rule/' + item.refId)">{{ item.label || item.code }}</el-button>
        <span v-else>{{ item.label || item.code }}</span>（{{ typeLabel(item.type) }}，ID {{ item.refId }}）
      </li></ul>
      <p v-else>当前可识别范围内没有引用方。</p>
    </template>
  </section>
</template>

<script>
import { inspectFunctionImpact } from '@/api/function'

export default {
  name: 'FunctionImpactPanel',
  props: { form: { type: Object, required: true }, params: { type: Array, required: true } },
  data: () => ({ report: null, checked: '', error: '', loading: false, requestId: 0 }),
  computed: {
    fingerprint() { return JSON.stringify([this.form, this.params]) },
    stale() { return Boolean(this.report && this.checked !== this.fingerprint) },
  },
  watch: { 'form.id'() { this.requestId++; this.loading = false; this.report = null; this.error = '' } },
  beforeUnmount() { this.requestId++ },
  methods: {
    typeLabel(type) { return ({ RULE: '规则', VARIABLE: '变量', MODEL: '模型', FUNCTION: '函数' })[type] || type },
    async check() {
      const id = ++this.requestId, fingerprint = this.fingerprint
      this.loading = true; this.error = ''
      try {
        const response = await inspectFunctionImpact({ ...this.form, paramsJson: JSON.stringify(this.params) })
        if (id !== this.requestId || fingerprint !== this.fingerprint) return
        this.report = response.data; this.checked = fingerprint
      } catch (error) {
        if (id === this.requestId && fingerprint === this.fingerprint) this.error = error.message || '影响检查失败，请重试'
      } finally { if (id === this.requestId) this.loading = false }
    },
  },
}
</script>

<style scoped>
.function-impact { padding: 12px; margin-bottom: 16px; background: var(--tianshu-bg-muted); color: var(--tianshu-text-primary); border-radius: 6px; overflow-wrap: anywhere; }
.function-impact p { margin: 8px 0; }
</style>
