<template>
  <article class="dashboard-chart-card dashboard-resource-card">
    <header><h3>{{ title }}</h3></header>
    <div class="dashboard-resource-count">
      <span>{{ countLabel }}</span>
      <strong>{{ number(count) }}</strong>
    </div>
    <div v-if="calls" class="dashboard-resource-stats">
      <span>请求数 <b>{{ number(calls.requestCount) }}</b></span>
      <span>成功数 <b>{{ number(calls.successCount) }}</b></span>
      <span v-if="showFound">查得数 <b>{{ number(calls.foundCount) }}</b></span>
      <span v-if="showFound">查得率 <b>{{ percent(calls.foundRate) }}</b></span>
      <span>平均时效 <b>{{ ms(calls.timing && calls.timing.avgMs) }}</b></span>
      <span>P95 <b>{{ ms(calls.timing && calls.timing.p95Ms) }}</b></span>
    </div>
  </article>
</template>

<script>
export default {
  name: 'ResourceCard',
  props: {
    title: { type: String, required: true },
    count: { type: Number, default: 0 },
    countLabel: { type: String, required: true },
    calls: { type: Object, default: null },
    showFound: { type: Boolean, default: false }
  },
  methods: {
    number(value) { return Number(value || 0).toLocaleString('zh-CN') },
    percent(value) { return `${(Number(value || 0) * 100).toFixed(2)}%` },
    ms(value) { return `${Number(value || 0).toFixed(2)} ms` }
  }
}
</script>
