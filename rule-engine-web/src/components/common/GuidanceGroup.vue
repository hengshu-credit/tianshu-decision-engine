<template>
  <section v-if="visible" :aria-label="label" class="guidance-group">
    <slot name="header" />
    <ol class="guidance-group__items">
      <dismissible-guidance
        v-for="(item, index) in items"
        :key="itemId(item)"
        tag="li"
        :guide-id="itemId(item)"
        :label="item.label"
        :heading="$slots.default ? '' : item.label"
      >
        <slot :item="item" :index="index">{{ item.detail }}</slot>
      </dismissible-guidance>
    </ol>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import DismissibleGuidance from './DismissibleGuidance.vue'
import { isGuidanceHidden } from '@/utils/pageGuidance'

const props = defineProps({
  guideId: { type: String, required: true },
  label: { type: String, required: true },
  items: { type: Array, required: true },
})
const itemId = item => props.guideId + '.' + (item.key || item.label)
// 已在旧版关闭整组引导的账户，仍可从个人设置恢复。
const visible = computed(() => !isGuidanceHidden(props.guideId) &&
  props.items.some(item => !isGuidanceHidden(itemId(item))))
</script>

<style scoped>
.guidance-group {
  min-width: 0;
  margin-bottom: 12px;
}
.guidance-group__items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.guidance-group__items > li {
  margin: 0;
  align-items: start;
}
.guidance-group__items :deep(.with-heading) {
  display: grid;
  gap: 4px;
}
</style>
