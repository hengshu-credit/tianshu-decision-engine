<template>
  <section class="config-layer-guide" :data-testid="testId" :aria-label="title">
    <div class="config-layer-guide__title">{{ title }}</div>
    <div class="config-layer-guide__items">
      <div
        v-for="(item, index) in items"
        :key="item.label"
        class="config-layer-guide__item"
        :class="{ 'is-active': index === 0 }"
      >
        <b>{{ index + 1 }}</b>
        <span><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></span>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'ConfigLayerGuide',
  props: {
    title: { type: String, default: '配置步骤' },
    items: { type: Array, default: () => [] },
    testId: { type: String, default: '' },
  },
}
</script>

<style scoped>
.config-layer-guide { margin-bottom: 12px; padding: 12px 14px; background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; }
.config-layer-guide__title { margin-bottom: 8px; color: var(--tianshu-text-primary); font-size: 13px; font-weight: 700; }
.config-layer-guide__items { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.config-layer-guide__item { display: flex; gap: 8px; align-items: center; padding: 8px; color: var(--tianshu-text-secondary); background: var(--tianshu-bg-muted); border-radius: 5px; }
.config-layer-guide__item.is-active { color: var(--tianshu-text-primary); border: 1px solid var(--el-color-primary-light-5); }
.config-layer-guide__item b { display: inline-flex; width: 20px; height: 20px; align-items: center; justify-content: center; color: #fff; background: var(--el-color-primary); border-radius: 50%; font-size: 11px; }
.config-layer-guide__item span { display: grid; min-width: 0; gap: 2px; }
.config-layer-guide__item small { color: var(--tianshu-text-tertiary); font-size: 11px; }
@media (max-width: 760px) { .config-layer-guide__items { grid-template-columns: 1fr; } }
</style>
