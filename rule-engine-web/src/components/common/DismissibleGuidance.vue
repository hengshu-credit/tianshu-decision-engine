<template>
  <component :is="tag" v-if="visible" class="dismissible-guidance" :role="tag === 'li' ? undefined : 'note'" :aria-label="label">
    <component :is="tag === 'p' ? 'span' : 'div'" class="guidance-content" :class="{ 'with-heading': heading }">
      <strong v-if="heading" class="guidance-heading">{{ heading }}</strong>
      <component :is="tag === 'p' ? 'span' : 'div'" class="guidance-body"><slot /></component>
    </component>
    <button type="button" class="guidance-close" :aria-label="closeLabel" :title="closeLabel" @click="close">
      <Close aria-hidden="true" />
    </button>
  </component>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { hideGuidance, isGuidanceHidden } from '@/utils/pageGuidance'

const props = defineProps({
  tag: { type: String, default: 'div' },
  guideId: { type: String, default: '' },
  label: { type: String, required: true },
  heading: { type: String, default: '' },
})
const emit = defineEmits(['close'])
const closed = ref(false)
const visible = computed(() => props.guideId ? !isGuidanceHidden(props.guideId) : !closed.value)
function close() {
  if (props.guideId) hideGuidance(props.guideId)
  else closed.value = true
  emit('close')
}
const closeLabel = computed(() => '关闭' + props.label)
</script>

<style scoped>
.dismissible-guidance {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex-shrink: 0;
  margin: 0 0 12px;
  padding: 8px 10px 8px 14px;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 8px;
  background: var(--tianshu-info-bg);
  color: var(--tianshu-info-text);
  font-size: 13px;
  line-height: 1.6;
}
.guidance-content, .guidance-body {
  min-width: 0;
  color: var(--tianshu-text-secondary);
  overflow-wrap: anywhere;
}
.with-heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 2px 12px;
}
.with-heading .guidance-body {
  flex: 1 1 min(100%, 14rem);
}
.guidance-heading {
  color: var(--tianshu-info-text);
  font-weight: 600;
}
.guidance-close {
  align-self: start;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--tianshu-info-text);
  cursor: pointer;
}
.guidance-close svg {
  width: 16px;
  height: 16px;
}
.guidance-close:hover, .guidance-close:focus-visible {
  background: var(--tianshu-primary-soft-hover-bg);
}
.guidance-close:focus-visible {
  outline: 2px solid var(--tianshu-info-text);
  outline-offset: 1px;
}
@media (pointer: coarse) {
  .guidance-close { width: 36px; height: 36px; }
}
</style>
