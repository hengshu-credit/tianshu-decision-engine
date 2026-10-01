<template>
  <section class="config-layer-guide" :data-testid="testId" :aria-label="title">
    <div class="config-layer-guide__heading">
      <div>
        <div class="config-layer-guide__title">{{ title }}</div>
        <p v-if="description" class="config-layer-guide__description">{{ description }}</p>
      </div>
      <span v-if="showProgress" class="config-layer-guide__progress" role="status">{{ readyCount }} / {{ items.length }} 已就绪</span>
    </div>
    <ol class="config-layer-guide__items">
      <li
        v-for="(item, index) in items"
        :key="item.key || item.label"
        class="config-layer-guide__step"
      >
        <component
          :is="interactive ? 'button' : 'div'"
          :type="interactive ? 'button' : undefined"
          class="config-layer-guide__item"
          :class="{ 'is-ready': isReady(item), 'is-active': index === currentIndex, 'is-interactive': interactive }"
          :aria-current="index === currentIndex ? 'step' : undefined"
          :data-testid="itemTestIdPrefix ? `${itemTestIdPrefix}-${item.key}` : undefined"
          @click="select(item, index)"
        >
          <b class="config-layer-guide__number" aria-hidden="true">{{ isReady(item) ? '✓' : index + 1 }}</b>
          <span class="config-layer-guide__copy">
            <strong>{{ item.label }}<span v-if="showProgress" class="config-layer-guide__state">{{ isReady(item) ? '已就绪' : index === currentIndex ? '待完成' : '待配置' }}</span></strong>
            <small v-if="item.help || item.detail">{{ item.help || item.detail }}</small>
          </span>
        </component>
      </li>
    </ol>
  </section>
</template>

<script>
export default {
  name: 'ConfigLayerGuide',
  props: {
    title: { type: String, default: '配置步骤' },
    description: { type: String, default: '' },
    items: { type: Array, default: () => [] },
    testId: { type: String, default: '' },
    showProgress: { type: Boolean, default: false },
    interactive: { type: Boolean, default: false },
    itemTestIdPrefix: { type: String, default: '' },
  },
  emits: ['select'],
  computed: {
    readyCount() { return this.items.filter(this.isReady).length },
    currentIndex() {
      if (!this.items.length) return -1
      return this.showProgress ? this.items.findIndex(item => !this.isReady(item)) : 0
    },
  },
  methods: {
    isReady(item) { return item.status === 'READY' || item.ready === true },
    select(item, index) { if (this.interactive) this.$emit('select', item, index) },
  },
}
</script>

<style scoped>
.config-layer-guide { container-type: inline-size; margin-bottom: 16px; padding: 16px; background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; }
.config-layer-guide__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.config-layer-guide__title { color: var(--tianshu-text-primary); font-size: 14px; font-weight: 600; line-height: 1.5; }
.config-layer-guide__description { margin: 4px 0 0; color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.6; }
.config-layer-guide__progress { flex: none; padding: 4px 10px; border-radius: 16px; background: var(--tianshu-info-bg); color: var(--tianshu-info-text); font-size: 12px; font-weight: 600; line-height: 1.5; }
.config-layer-guide__items { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 170px), 1fr)); gap: 12px; margin: 0; padding: 0; list-style: none; }
.config-layer-guide__step { min-width: 0; }
.config-layer-guide__item { box-sizing: border-box; display: flex; width: 100%; height: 100%; gap: 10px; align-items: flex-start; padding: 12px; border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; color: var(--tianshu-text-primary); background: var(--tianshu-bg-soft); font: inherit; text-align: left; }
.config-layer-guide__item.is-active { border-color: var(--tianshu-info-border); background: var(--tianshu-info-bg); }
.config-layer-guide__item.is-interactive { cursor: pointer; transition: background-color 160ms, border-color 160ms; }
.config-layer-guide__item.is-interactive:hover { border-color: var(--tianshu-info-text); background: var(--tianshu-bg-hover); }
.config-layer-guide__item:focus-visible { outline: 2px solid var(--tianshu-info-text); outline-offset: 2px; }
.config-layer-guide__number { display: inline-flex; flex: 0 0 26px; height: 26px; align-items: center; justify-content: center; color: var(--tianshu-text-secondary); background: var(--tianshu-bg-muted); border: 1px solid var(--tianshu-border); border-radius: 50%; font-size: 12px; }
.is-active .config-layer-guide__number { color: var(--tianshu-info-text); background: var(--tianshu-info-bg); border-color: var(--tianshu-info-border); }
.is-ready .config-layer-guide__number { color: var(--tianshu-success-text); background: var(--tianshu-success-bg); border-color: var(--tianshu-success-border); }
.config-layer-guide__copy { display: grid; min-width: 0; gap: 5px; }
.config-layer-guide__copy strong { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; font-size: 13px; font-weight: 600; line-height: 1.5; }
.config-layer-guide__state { color: var(--tianshu-text-tertiary); font-size: 11px; font-weight: 400; }
.is-ready .config-layer-guide__state { color: var(--tianshu-success-text); }
.is-active .config-layer-guide__state { color: var(--tianshu-info-text); }
.config-layer-guide__copy small { color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
@container (max-width: 480px) { .config-layer-guide__heading { flex-wrap: wrap; gap: 8px; } .config-layer-guide__items { grid-template-columns: 1fr; } }
</style>
