<template>
  <div class="nd" :class="{ 'is-dim': dimmed, 'snapshot-branch': snapshot }">
    <component :is="toggleable ? 'button' : 'div'" v-if="showHeading" class="nd-box"
      :type="toggleable ? 'button' : undefined" :aria-expanded="toggleable ? expanded : undefined"
      @click="toggle">
      <span v-if="toggleable" class="branch-arrow" aria-hidden="true">{{ expanded ? '▾' : '▸' }}</span>
      <slot name="heading" />
    </component>
    <div v-if="hasChildren && expanded" class="nd-kids" :class="{ 'no-stem': !showHeading }">
      <slot />
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
const props = defineProps({ showHeading: { type: Boolean, default: true }, hasChildren: Boolean, dimmed: Boolean, snapshot: Boolean })
const expanded = ref(true)
const toggleable = computed(() => props.snapshot && props.showHeading && props.hasChildren)
function toggle() { if (toggleable.value) expanded.value = !expanded.value }
</script>
<style scoped>

.nd {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 10px;
}
.nd.is-dim {
  opacity: 0.7;
}
.nd.is-dim > .nd-box {
  border-style: dashed;
}
.nd-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 6px 12px;
  background: var(--tianshu-bg-surface);
  border: 1px solid var(--tianshu-border);
  border-radius: 6px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--tianshu-text-primary);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.nd-box:hover {
  border-color: var(--tianshu-info-border);
  box-shadow: 0 2px 6px var(--tianshu-primary-shadow);
}
.nd-kids {
  display: flex;
  justify-content: center;
  position: relative;
  padding-top: 28px;
}
.nd-kids:not(.no-stem)::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 1px;
  height: 14px;
  background: var(--tianshu-border);
}
.nd-kids > :deep(.nd) {
  position: relative;
}
.nd-kids > :deep(.nd)::before {
  content: '';
  position: absolute;
  top: -14px;
  left: 50%;
  width: 1px;
  height: 14px;
  background: var(--tianshu-border);
}
.nd-kids > :deep(.nd):first-child:not(:last-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 50%;
  right: 0;
  height: 1px;
  background: var(--tianshu-border);
}
.nd-kids > :deep(.nd):last-child:not(:first-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 0;
  right: 50%;
  height: 1px;
  background: var(--tianshu-border);
}
.nd-kids > :deep(.nd):not(:first-child):not(:last-child)::after {
  content: '';
  position: absolute;
  top: -14px;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--tianshu-border);
}
.nd:not(.is-dim) > .nd-kids:not(.no-stem)::before {
  width: 2px;
  margin-left: -0.5px;
  background: var(--el-color-primary);
}
.nd-kids > :deep(.nd):not(.is-dim)::before {
  width: 2px;
  margin-left: -0.5px;
  background: var(--el-color-primary);
}
.nd-kids > :deep(.nd):not(.is-dim):first-child:not(:last-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}
.nd-kids > :deep(.nd):not(.is-dim):last-child:not(:first-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}
.nd-kids > :deep(.nd):not(.is-dim):not(:first-child):not(:last-child)::after {
  height: 2px;
  background: var(--el-color-primary);
}

.snapshot-branch { min-width: 0; max-width: 100%; padding: 0 4px; box-sizing: border-box; }
.snapshot-branch > .nd-box { flex-wrap: wrap; max-width: 100%; min-width: 0; height: auto; box-sizing: border-box; white-space: normal; overflow-wrap: anywhere; text-align: start; font-family: inherit; }
button.nd-box { cursor: pointer; }
button.nd-box:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.branch-arrow { color: var(--el-color-primary); }
.snapshot-branch > .nd-kids { min-width: 0; max-width: 100%; }
@container version-panel (max-width: 760px) {
  .snapshot-branch { align-items: flex-start; padding: 0; }
  .snapshot-branch > .nd-kids { box-sizing: border-box; flex-direction: column; align-items: stretch; gap: 6px; padding: 8px 0 0 14px; margin-left: 7px; border-left: 1px solid var(--tianshu-border-strong); }
  .snapshot-branch > .nd-kids::before, .snapshot-branch > .nd-kids > :deep(.nd)::after { display: none; }
  .snapshot-branch > .nd-kids > :deep(.nd)::before { top: 15px; left: -14px; height: 1px; width: 14px; background: var(--tianshu-border-strong); }
}
</style>
