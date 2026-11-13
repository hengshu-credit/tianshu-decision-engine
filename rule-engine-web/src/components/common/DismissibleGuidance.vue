<template>
  <component :is="tag" v-if="!isGuidanceHidden(guideId)" class="dismissible-guidance">
    <slot />
    <button type="button" class="guidance-close" :aria-label="closeLabel" :title="closeLabel" @click="hideGuidance(guideId)">
      <span aria-hidden="true">×</span>
    </button>
  </component>
</template>

<script setup>
import { computed } from 'vue'
import { hideGuidance, isGuidanceHidden } from '@/utils/pageGuidance'

const props = defineProps({
  tag: { type: String, default: 'div' },
  guideId: { type: String, required: true },
  label: { type: String, required: true },
})
const closeLabel = computed(() => '关闭' + props.label)
</script>

<style scoped>
.dismissible-guidance {
  position: relative;
  min-height: 24px;
}
.dismissible-guidance:not(.guidance-alert), .guidance-alert :deep(.el-alert) {
  padding-inline-end: 36px !important;
}
.guidance-close {
  position: absolute;
  inset-block-start: 3px;
  inset-inline-end: 4px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--tianshu-text-secondary);
  cursor: pointer;
  font: 18px/1 sans-serif;
}
.guidance-close:hover, .guidance-close:focus-visible {
  background: var(--tianshu-bg-hover);
  color: var(--tianshu-text-primary);
}
.guidance-close:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
</style>
