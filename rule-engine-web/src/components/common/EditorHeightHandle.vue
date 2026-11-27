<template>
  <div class="editor-height-handle" role="separator" tabindex="0" aria-orientation="horizontal"
    :aria-label="label" :aria-valuemin="min" :aria-valuemax="max" :aria-valuenow="modelValue"
    title="拖动或用上下方向键调整高度，双击恢复默认"
    @pointerdown="start" @pointermove="move" @lostpointercapture="drag = null"
    @keydown.up.prevent="setHeight(modelValue - 32)" @keydown.down.prevent="setHeight(modelValue + 32)"
    @keydown.home.prevent="setHeight(min)" @keydown.end.prevent="setHeight(max)" @dblclick="setHeight(initial)" />
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: Number, required: true },
  initial: { type: Number, required: true },
  min: { type: Number, required: true },
  max: { type: Number, default: 1000 },
  label: { type: String, required: true },
})
const emit = defineEmits(['update:modelValue'])
const drag = ref(null)
function setHeight(height) {
  emit('update:modelValue', Math.min(props.max, Math.max(props.min, Math.round(height))))
}
function start(event) {
  if (event.button !== 0 || !event.isPrimary) return
  event.currentTarget.setPointerCapture(event.pointerId)
  drag.value = { id: event.pointerId, top: event.clientY, height: props.modelValue }
}
function move(event) {
  if (drag.value?.id === event.pointerId) setHeight(drag.value.height + event.clientY - drag.value.top)
}
</script>

<style scoped>
.editor-height-handle {
  display: grid;
  height: 12px;
  place-items: center;
  cursor: row-resize;
  touch-action: none;
  user-select: none;
  background: var(--tianshu-bg-muted);
}
.editor-height-handle::after {
  content: '';
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--tianshu-text-tertiary);
}
.editor-height-handle:hover::after,
.editor-height-handle:focus-visible::after { background: var(--el-color-primary); }
</style>
