<template>
  <span
    v-if="visible"
    class="dialog-resize-handle"
    role="separator"
    tabindex="0"
    aria-label="拖拽调整弹窗大小"
    title="拖拽调整弹窗大小"
    @mousedown.prevent.stop="start"
    @touchstart.prevent.stop="startTouch"
    @keydown="onKeydown"
  />
</template>

<script>
export default {
  name: 'DialogResizeHandle',
  props: {
    visible: { type: Boolean, default: true },
    minWidth: { type: Number, default: 520 },
    minHeight: { type: Number, default: 360 },
  },
  data: () => ({ resizing: false, startX: 0, startY: 0, startWidth: 0, startHeight: 0, initialHeight: 0, dialog: null, bodyCursor: '', bodyUserSelect: '' }),
  watch: { visible(value) { if (!value) this.stop() } },
  beforeUnmount() { this.stop() },
  methods: {
    start(event) { if (event.button === 0) this.begin(event.clientX, event.clientY) },
    startTouch(event) {
      const touch = event.touches && event.touches[0]
      if (touch) this.begin(touch.clientX, touch.clientY)
    },
    begin(clientX, clientY) {
      if (this.resizing) return
      const dialog = this.$el && this.$el.closest ? this.$el.closest('.el-dialog') : null
      if (!dialog) return
      const rect = dialog.getBoundingClientRect()
      this.dialog = dialog
      this.resizing = true
      this.startX = clientX
      this.startY = clientY
      this.startWidth = rect.width
      this.startHeight = rect.height
      if (!this.initialHeight) this.initialHeight = rect.height
      this.bodyCursor = document.body.style.cursor
      this.bodyUserSelect = document.body.style.userSelect
      document.body.style.cursor = 'nwse-resize'
      document.body.style.userSelect = 'none'
      window.addEventListener('mousemove', this.move)
      window.addEventListener('mouseup', this.stop)
      window.addEventListener('touchmove', this.moveTouch, { passive: false })
      window.addEventListener('touchend', this.stop)
      window.addEventListener('touchcancel', this.stop)
      window.addEventListener('blur', this.stop)
    },
    move(event) { this.resize(event.clientX, event.clientY) },
    moveTouch(event) {
      const touch = event.touches && event.touches[0]
      if (!touch) return
      event.preventDefault()
      this.resize(touch.clientX, touch.clientY)
    },
    resize(clientX, clientY) {
      if (!this.resizing || !this.dialog) return
      const maxWidth = Math.max(0, window.innerWidth - 32)
      const maxHeight = Math.max(0, window.innerHeight - 32)
      // 弹窗保持居中，两侧同步伸缩，让右下角跟随指针。
      const width = Math.min(Math.max(this.startWidth + 2 * (clientX - this.startX), this.minWidth), maxWidth)
      const height = Math.min(Math.max(this.startHeight + 2 * (clientY - this.startY), this.minHeight), maxHeight)
      this.dialog.style.width = `${Math.round(width)}px`
      this.dialog.style.height = `${Math.round(height)}px`
      this.dialog.style.setProperty('--dialog-resize-height-delta', `${Math.round(height - this.initialHeight)}px`)
    },
    onKeydown(event) {
      const delta = { ArrowRight: [10, 0], ArrowLeft: [-10, 0], ArrowDown: [0, 10], ArrowUp: [0, -10] }[event.key]
      if (!delta) return
      event.preventDefault()
      event.stopPropagation()
      this.begin(0, 0)
      this.resize(...delta)
      this.stop()
    },
    stop() {
      window.removeEventListener('mousemove', this.move)
      window.removeEventListener('mouseup', this.stop)
      window.removeEventListener('touchmove', this.moveTouch)
      window.removeEventListener('touchend', this.stop)
      window.removeEventListener('touchcancel', this.stop)
      window.removeEventListener('blur', this.stop)
      if (!this.resizing) return
      this.resizing = false
      document.body.style.cursor = this.bodyCursor
      document.body.style.userSelect = this.bodyUserSelect
      this.dialog = null
    },
  },
}
</script>

<style>
.el-dialog.resizable-config-dialog { position: relative; overflow: hidden; }
.resizable-config-dialog > .el-dialog__body { flex: 1 1 auto; min-height: 0; overflow: auto; }
</style>

<style scoped>
.dialog-resize-handle {
  position: absolute;
  right: 2px;
  bottom: 2px;
  z-index: 10;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none;
}
.dialog-resize-handle::after {
  content: '';
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 9px;
  height: 9px;
  border-right: 2px solid var(--tianshu-text-tertiary);
  border-bottom: 2px solid var(--tianshu-text-tertiary);
}
.dialog-resize-handle:hover::after { border-color: var(--el-color-primary); }
.dialog-resize-handle:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
</style>
