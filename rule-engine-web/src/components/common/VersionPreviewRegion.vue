<template>
  <div class="version-preview-region">
    <div v-if="tall" class="preview-size-actions">
      <el-button link type="primary" size="small" @click="toggleFullHeight">
        {{ fullHeight ? '恢复默认高度' : '显示完整高度' }}
      </el-button>
    </div>
    <div :id="regionId" class="preview-region-body" :tabindex="shownHeight < contentHeight ? 0 : undefined"
      :aria-label="label" role="region" :style="{ maxHeight: fullHeight ? 'none' : limit + 'px' }">
      <div ref="content" class="preview-region-content"><slot /></div>
    </div>
    <editor-height-handle v-if="tall" :aria-controls="regionId" :label="'调整' + label + '高度'" :model-value="shownHeight"
      :initial="defaultHeight" :min="Math.min(120, defaultHeight)" :max="contentHeight" @update:model-value="resize" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import EditorHeightHandle from './EditorHeightHandle.vue'

defineProps({ label: { type: String, required: true } })
const regionId = useId()
const content = ref(null)
const contentHeight = ref(0)
const defaultHeight = ref(480)
const requestedHeight = ref(null)
const fullHeight = ref(false)
const limit = computed(() => requestedHeight.value ?? defaultHeight.value)
const tall = computed(() => contentHeight.value > defaultHeight.value || contentHeight.value > limit.value)
const shownHeight = computed(() => fullHeight.value ? contentHeight.value : Math.min(limit.value, contentHeight.value))

function resize(height) {
  fullHeight.value = height === contentHeight.value
  requestedHeight.value = height === defaultHeight.value ? null : height
}
function toggleFullHeight() {
  fullHeight.value = !fullHeight.value
  requestedHeight.value = null
}
function measure() {
  defaultHeight.value = Math.min(480, Math.round(window.innerHeight * 0.6))
  contentHeight.value = Math.ceil(content.value.getBoundingClientRect().height)
}
let observer
onMounted(() => {
  if (typeof ResizeObserver !== 'function') {
    fullHeight.value = true
    return
  }
  measure()
  observer = new ResizeObserver(measure)
  observer.observe(content.value)
  window.addEventListener('resize', measure)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', measure)
})
</script>

<style scoped>
.version-preview-region { min-width: 0; }
.preview-region-content { display: flow-root; }
.preview-region-body { overflow: auto; }
.preview-size-actions { display: flex; justify-content: flex-end; padding-bottom: 4px; }
</style>
