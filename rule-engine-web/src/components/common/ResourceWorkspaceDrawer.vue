<template>
  <el-drawer class="resource-workspace-drawer" :class="{ 'fit-workspace-tables': fitTables }"
    :size="size" direction="rtl" append-to-body resizable>
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </el-drawer>
</template>

<script setup>
defineProps({
  size: { type: String, default: 'min(1120px, 100vw)' },
  fitTables: Boolean,
})
</script>

<style lang="scss">
.resource-workspace-drawer {
  min-width: min(360px, 100vw);
  max-width: 100vw;
  --el-drawer-dragger-size: 8px;

  > .el-drawer__header { margin-bottom: 0; padding-bottom: 16px; border-bottom: 1px solid var(--tianshu-border-subtle); }
  > .el-drawer__body { min-width: 0; }
  .version-compare-toolbar { flex-wrap: wrap; }
  .el-pagination { max-width: 100%; flex-wrap: wrap; }
}

html[data-table-scroll-mode='fixed'] .resource-workspace-drawer {
  // 历史版本列表下方还有快照，为两者保留可见空间。
  .workspace-history-table > .el-table__inner-wrapper > .el-table__body-wrapper > .el-scrollbar > .el-scrollbar__wrap {
    max-height: min(40vh, 360px);
  }

  &.fit-workspace-tables {
    > .el-drawer__body, .workspace-table-region, .workspace-table-region > .el-tabs__content {
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
    .workspace-table-region, .workspace-table-region > .el-tabs__content {
      flex: 1;
      overflow: visible;
    }
    > .el-drawer__body > *, .workspace-table-region > * { flex-shrink: 0; }
    .workspace-table {
      flex: 1 0 160px;
      min-height: 160px;
      > .el-table__inner-wrapper > .el-table__body-wrapper > .el-scrollbar { height: 100%; }
    }
  }
}
</style>
