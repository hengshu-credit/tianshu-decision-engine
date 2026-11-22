<template>
  <el-drawer v-model="visible" class="resource-workspace-drawer" :class="{ 'fit-workspace-tables': fitTables }"
    :style="{ height: drawerHeight + 'px' }"
    :size="size" direction="rtl" append-to-body resizable>
    <slot />
    <template #footer>
      <div v-if="$slots.footer" class="workspace-footer-actions"><slot name="footer" /></div>
      <editor-height-handle label="调整弹窗高度" :model-value="drawerHeight" :initial="viewportHeight"
        :min="Math.min(280, viewportHeight)" :max="viewportHeight"
        @update:model-value="customHeight = $event === viewportHeight ? null : $event" />
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import EditorHeightHandle from './EditorHeightHandle.vue'

const visible = defineModel({ type: Boolean })
defineProps({
  size: { type: String, default: 'min(1120px, 100vw)' },
  fitTables: Boolean,
})
const viewportHeight = ref(window.innerHeight)
const customHeight = ref(null)
const drawerHeight = computed(() => Math.min(customHeight.value ?? viewportHeight.value, viewportHeight.value))
watch(visible, (open, _, onCleanup) => {
  if (!open) return
  const measure = () => { viewportHeight.value = window.innerHeight }
  measure()
  window.addEventListener('resize', measure)
  onCleanup(() => window.removeEventListener('resize', measure))
}, { immediate: true })
</script>

<style lang="scss">
.resource-workspace-drawer {
  min-width: min(360px, 100vw);
  max-width: 100vw;
  --el-drawer-dragger-size: 8px;
  transition-property: transform, opacity;

  > .el-drawer__header { margin-bottom: 0; padding-bottom: 16px; border-bottom: 1px solid var(--tianshu-border-subtle); }
  > .el-drawer__body { min-width: 0; min-height: 0; }
  > .el-drawer__footer { padding: 0; flex-shrink: 0; }
  .workspace-footer-actions { padding: 12px var(--el-drawer-padding-primary); }
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
