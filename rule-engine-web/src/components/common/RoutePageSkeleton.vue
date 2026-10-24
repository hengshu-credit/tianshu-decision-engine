<template>
  <section class="route-page-skeleton" data-testid="route-page-skeleton" role="status" aria-live="polite" aria-busy="true">
    <header class="route-page-skeleton__heading">
      <app-loading-icon :size="32" />
      <h2>{{ title || '页面加载中' }}</h2>
      <span>正在加载，请稍候…</span>
    </header>
    <div class="route-page-skeleton__toolbar" aria-hidden="true">
      <i v-for="item in 3" :key="item" class="route-page-skeleton__block" />
      <i class="route-page-skeleton__block route-page-skeleton__action" />
    </div>
    <div class="route-page-skeleton__body" aria-hidden="true">
      <div v-for="row in 7" :key="row" class="route-page-skeleton__row">
        <i v-for="column in 4" :key="column" class="route-page-skeleton__block" />
      </div>
    </div>
  </section>
</template>

<script>
import AppLoadingIcon from './AppLoadingIcon.vue'

export default {
  name: 'RoutePageSkeleton',
  components: { AppLoadingIcon },
  props: { title: { type: String, default: '' } },
}
</script>

<style scoped lang="scss">
.route-page-skeleton {
  min-width: 0;
  padding: 24px;
  background: var(--tianshu-bg-container, var(--el-bg-color));
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}
.route-page-skeleton__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  h2 { margin: 0; color: var(--el-text-color-primary); font-size: 18px; }
  span { color: var(--el-text-color-secondary); font-size: 13px; }
}
.route-page-skeleton__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
  .route-page-skeleton__block { width: 144px; max-width: 100%; height: 32px; }
  .route-page-skeleton__action { width: 96px; margin-left: auto; }
}
.route-page-skeleton__row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  &:first-child { padding-top: 0; }
}
.route-page-skeleton__block {
  display: block;
  height: 16px;
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color);
}
@media (max-width: 600px) {
  .route-page-skeleton { padding: 16px; }
  .route-page-skeleton__row { gap: 12px; }
}
</style>
