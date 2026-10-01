<template>
  <div class="dashboard-metric" :data-tone="tone">
    <span>{{ label }}</span>
    <strong>{{ value }}</strong>
  </div>
</template>

<script>
export default {
  name: 'MetricCard',
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number], default: '' },
    tone: { type: String, default: '' }
  }
}
</script>
