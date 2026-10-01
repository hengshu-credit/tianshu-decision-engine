<template>
  <article class="dashboard-chart-card">
    <header>
      <div class="dashboard-chart-card__heading">
        <h3>{{ title }}</h3>
        <p v-if="note">{{ note }}</p>
      </div>
      <slot name="actions" />
    </header>
    <slot />
  </article>
</template>

<script>
export default {
  name: 'ChartCard',
  props: {
    title: { type: String, required: true },
    note: { type: String, default: '' }
  }
}
</script>

<style scoped>
header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-chart-card__heading {
  min-width: 0;
}
</style>
