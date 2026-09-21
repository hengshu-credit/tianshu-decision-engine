<template>
  <el-tooltip
    v-if="hoverTarget"
    :virtual-ref="hoverTarget"
    virtual-triggering
    :visible="visible"
    placement="top"
    popper-class="designer-input-tooltip"
    :enterable="true"
  >
    <template #content><div>{{ content }}</div></template>
  </el-tooltip>
</template>

<script>
export default {
  name: 'DesignerInputTooltip',
  props: { enabled: { type: Boolean, default: false } },
  data() {
    return { hoverTarget: null, content: '', visible: false, hoverTimer: null }
  },
  watch: {
    enabled(value) { if (!value) this.hide() },
  },
  mounted() {
    document.addEventListener('mouseover', this.onMouseOver)
    document.addEventListener('mouseout', this.onMouseOut)
    document.addEventListener('focusin', this.hide)
    document.addEventListener('input', this.hide)
    document.addEventListener('pointerdown', this.hide)
    window.addEventListener('scroll', this.onScroll, true)
  },
  beforeUnmount() {
    this.hide()
    document.removeEventListener('mouseover', this.onMouseOver)
    document.removeEventListener('mouseout', this.onMouseOut)
    document.removeEventListener('focusin', this.hide)
    document.removeEventListener('input', this.hide)
    document.removeEventListener('pointerdown', this.hide)
    window.removeEventListener('scroll', this.onScroll, true)
  },
  methods: {
    onScroll(event) {
      if (!event.target?.closest?.('.designer-input-tooltip')) this.hide()
    },
    hide() {
      clearTimeout(this.hoverTimer)
      this.hoverTimer = null
      this.visible = false
      this.hoverTarget = null
      this.content = ''
    },
    onMouseOver(event) {
      if (!this.enabled || !event.target?.closest) return
      if (event.target.closest('.designer-input-tooltip')) return
      const input = event.target.closest('input, textarea')
        || event.target.closest('.el-input, .el-select')?.querySelector('input')
      if (!input || !input.closest('.uiue-compact-designer, .expression-editor')
          || input.closest('.monaco-editor') || input.type === 'password'
          || input.getAttribute('aria-expanded') === 'true') return this.hide()
      if (input === this.hoverTarget) return
      const text = input.value
      if (!text || input.clientWidth === 0
          || (input.scrollWidth <= input.clientWidth && input.scrollHeight <= input.clientHeight)) return this.hide()
      this.hide()
      this.hoverTarget = input
      this.content = text
      this.hoverTimer = setTimeout(() => { this.visible = true }, 300)
    },
    onMouseOut(event) {
      const next = event.relatedTarget
      if (next === this.hoverTarget || next?.closest?.('.designer-input-tooltip')) return
      if (event.target === this.hoverTarget || event.target?.closest?.('.designer-input-tooltip')) this.hide()
    },
  },
}
</script>

<style>
.designer-input-tooltip {
  max-width: min(640px, calc(100vw - 32px));
  max-height: min(320px, 45vh);
  overflow: auto;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  line-height: 1.5;
}
</style>
