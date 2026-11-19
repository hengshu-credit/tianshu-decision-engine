<template>
  <section ref="card" class="block-item" :class="{ 'is-selected': selected, 'is-collapsed': collapsed }"
    @pointerdown="emit('select')" @focusin="emit('select')" @pointerenter="preview">
    <header class="block-header">
      <span class="block-type-tag" :style="{ background: color }">{{ label }}</span>
      <span v-if="collapsed" class="block-summary"><slot name="summary" /></span>
      <div class="block-header-actions">
        <slot name="actions" />
        <el-button link size="small" :icon="collapsed ? ArrowDown : ArrowUp" :aria-expanded="!collapsed"
          :aria-controls="panelId" :aria-label="collapsed ? '展开动作' : '折叠动作'" :title="collapsed ? '展开动作' : '折叠动作'"
          @click="toggleCollapsed" />
      </div>
    </header>
    <Teleport to="body" :disabled="!floating">
      <div v-show="!collapsed || floating" :id="panelId" ref="panel" :class="{ 'action-block-window': floating }"
        :style="floating ? windowStyle : undefined" :role="floating ? 'dialog' : undefined"
        :aria-label="floating ? label + '配置' : undefined" @focusin="emit('select')" @pointerdown="emit('select')"
        @keydown.esc="escapeWindow">
        <header v-if="floating" class="window-header" @pointerdown="startMove" @pointermove="move"
          @pointerup="endMove" @lostpointercapture="endMove">
          <strong>{{ label }}配置</strong>
          <div class="window-actions" @pointerdown.stop>
            <el-button link size="small" class="window-pin" :aria-pressed="pinned"
              :aria-label="pinned ? '取消固定' : '固定'" :title="pinned ? '取消固定' : '固定'" @click="pinned = !pinned">
              <svg viewBox="0 0 24 24" width="17" height="17" aria-hidden="true" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round">
                <path d="M8 3h8l-1 3v5l3 3v1H6v-1l3-3V6z" :fill="pinned ? 'currentColor' : 'none'" />
                <path d="M12 15v6" />
              </svg>
            </el-button>
            <el-button link size="small" :icon="Close" aria-label="关闭浮窗" title="关闭浮窗" @click="closeWindow" />
          </div>
        </header>
        <div class="action-block-content"><slot /></div>
      </div>
    </Teleport>
  </section>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onDeactivated, provide, ref, useId, watch } from 'vue'
import { ArrowDown, ArrowUp, Close } from '@element-plus/icons-vue'
import { useZIndex } from 'element-plus'

defineProps({ label: { type: String, required: true }, color: { type: String, required: true }, selected: Boolean })
const emit = defineEmits(['select'])
const card = ref(null)
const panel = ref(null)
const panelId = useId()
const collapsed = ref(false)
const floating = ref(false)
const pinned = ref(false)
const parentVisible = inject('actionBlockContentVisible', null)
provide('actionBlockContentVisible', computed(() => parentVisible?.value !== false && (!collapsed.value || floating.value)))
if (parentVisible) watch(parentVisible, visible => { if (!visible) closeWindow() })
const position = ref({ left: 0, top: 0 })
const zIndex = ref(0)
const { nextZIndex } = useZIndex()
const gap = 8
let closeTimer
let drag
const windowStyle = computed(() => ({
  '--window-left': position.value.left + 'px',
  '--window-top': position.value.top + 'px',
  zIndex: zIndex.value,
}))

function place(left, top, width, height) {
  position.value = {
    left: Math.max(gap, Math.min(left, window.innerWidth - width - gap)),
    top: Math.max(gap, Math.min(top, window.innerHeight - height - gap)),
  }
}
function fitWindow() {
  if (!floating.value) return
  const rect = panel.value.getBoundingClientRect()
  place(rect.left, rect.top, rect.width, rect.height)
}
async function preview(event) {
  if (!collapsed.value || floating.value || event.pointerType === 'touch') return
  const anchor = card.value.getBoundingClientRect()
  position.value = { left: gap, top: gap }
  zIndex.value = nextZIndex()
  floating.value = true
  await nextTick()
  if (!floating.value) return
  const rect = panel.value.getBoundingClientRect()
  const left = anchor.right + gap + rect.width <= window.innerWidth ? anchor.right + gap
    : anchor.left >= rect.width + gap ? anchor.left - rect.width - gap : anchor.left
  place(left, left === anchor.left ? anchor.bottom + gap : anchor.top, rect.width, rect.height)
  document.addEventListener('pointerout', trackHover)
  document.addEventListener('pointerup', trackHover)
  window.addEventListener('resize', fitWindow)
}
// 选择器、下拉列表和嵌套动作浮窗会传送到 body；鼠标移入这些窗口仍在编辑当前动作。
function hoveringEditor() {
  const surfaces = new Set([panel.value])
  for (const surface of surfaces) {
    if (surface.matches(':hover')) return true
    if (surface !== panel.value && surface.matches('.action-block-window') && surface.querySelector('.window-pin[aria-pressed="true"]')) return true
    for (const control of surface.querySelectorAll('[aria-controls], [aria-describedby], [aria-owns]')) {
      for (const attr of ['aria-controls', 'aria-describedby', 'aria-owns']) {
        for (const id of (control.getAttribute(attr) || '').split(/\s+/).filter(Boolean)) {
          const owned = document.getElementById(id)
          if (owned && owned.getClientRects().length) surfaces.add(owned)
        }
      }
    }
  }
  return !!document.querySelector('.el-popper:hover') &&
    !!panel.value.querySelector('[aria-haspopup][aria-expanded="true"], [role="combobox"][aria-expanded="true"]')
}
function trackHover(event) {
  clearTimeout(closeTimer)
  if (pinned.value || drag || event.buttons) return
  closeTimer = setTimeout(() => {
    if (floating.value && !pinned.value && !drag && !card.value.matches(':hover') && !hoveringEditor()) closeWindow()
  }, 260)
}
function closeWindow() {
  clearTimeout(closeTimer)
  document.removeEventListener('pointerout', trackHover)
  document.removeEventListener('pointerup', trackHover)
  window.removeEventListener('resize', fitWindow)
  if (floating.value && panel.value?.contains(document.activeElement)) document.activeElement.blur()
  drag = null
  pinned.value = false
  floating.value = false
}
function toggleCollapsed() {
  closeWindow()
  collapsed.value = !collapsed.value
}
function escapeWindow(event) {
  if (!floating.value || event.defaultPrevented || event.target.closest('[aria-expanded="true"]')) return
  event.stopPropagation()
  closeWindow()
  nextTick(() => card.value?.querySelector('[aria-controls]')?.focus())
}
function startMove(event) {
  if (event.button !== 0) return
  event.preventDefault()
  const rect = panel.value.getBoundingClientRect()
  drag = { x: event.clientX - rect.left, y: event.clientY - rect.top, width: rect.width, height: rect.height }
  event.currentTarget.setPointerCapture(event.pointerId)
}
function move(event) {
  if (drag) place(event.clientX - drag.x, event.clientY - drag.y, drag.width, drag.height)
}
function endMove() { drag = null }
onDeactivated(closeWindow)
onBeforeUnmount(closeWindow)
</script>

<style scoped>
.block-item {
  border: 1px solid var(--tianshu-designer-accent-border);
  border-radius: 5px;
  margin-bottom: 8px;
  background: var(--tianshu-bg-muted);
  overflow: hidden;
}
.block-item.is-selected { border-color: var(--el-color-primary); }
.block-header, .window-header, .block-header-actions, .window-actions { display: flex; align-items: center; gap: 4px; }
.block-header, .window-header { justify-content: space-between; padding: 4px 8px; background: var(--tianshu-bg-soft); }
.block-header { border-bottom: 1px solid var(--tianshu-designer-accent-border); }
.is-collapsed > .block-header { border-bottom: 0; }
.block-summary { flex: 1; min-width: 0; margin-inline: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--tianshu-text-secondary); }
.block-type-tag, .block-header-actions { flex: none; }
.window-pin svg { transform: rotate(35deg); }
.window-pin[aria-pressed="true"] svg { transform: none; color: var(--el-color-primary); }
.block-type-tag { font-size: 11px; font-weight: 600; color: white; padding: 2px 8px; border-radius: 3px; }
.action-block-window {
  position: fixed;
  left: var(--window-left);
  top: var(--window-top);
  width: min(540px, calc(100vw - 16px));
  max-width: calc(100vw - var(--window-left) - 8px);
  max-height: calc(100dvh - var(--window-top) - 8px);
  min-width: min(280px, calc(100vw - 16px));
  min-height: min(110px, calc(100dvh - 16px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  resize: both;
  overflow: hidden;
  border: 1px solid var(--el-color-primary);
  border-radius: 6px;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-primary);
  box-shadow: var(--tianshu-shadow-medium);
}
.window-header { flex: none; border-bottom: 1px solid var(--tianshu-designer-accent-border); cursor: move; touch-action: none; user-select: none; font-size: 13px; }
.action-block-window > .action-block-content { overflow: auto; min-height: 0; padding-bottom: 8px; }
</style>
