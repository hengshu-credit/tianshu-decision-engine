<template>
  <el-table ref="table" :data="visibleRows" :class="{ 'windowed-field-table': windowed }" :style="spacing">
    <slot />
  </el-table>
</template>

<script>
import { markRaw } from 'vue'

const ROW_HEIGHT = 42
const BUFFER = 8

export default {
  name: 'LazyFieldTable',
  props: {
    data: { type: Array, required: true },
    more: Boolean,
    expandedKeys: { type: Array, default: null },
  },
  emits: ['load-more'],
  data: () => ({ start: 0, visibleCount: 32 }),
  computed: {
    rows() {
      if (!this.expandedKeys) return this.data
      const expanded = new Set(this.expandedKeys.map(String))
      const rows = []
      const stack = this.data.map(field => ({ field, depth: 0 })).reverse()
      while (stack.length) {
        const item = stack.pop()
        item.expanded = expanded.has(String(item.field.id))
        // 展开状态变化会重建投影；表格的深度监听无需遍历未展开的 children。
        rows.push(markRaw(item))
        if (!item.expanded) continue
        const children = item.field.children || []
        for (let i = children.length - 1; i >= 0; i--) stack.push({ field: children[i], depth: item.depth + 1 })
      }
      return rows
    },
    windowed() { return this.rows.length > 50 },
    first() { return this.windowed ? Math.min(this.start, Math.max(0, this.rows.length - this.visibleCount)) : 0 },
    visibleRows() { return this.windowed ? this.rows.slice(this.first, this.first + this.visibleCount) : this.rows },
    spacing() {
      if (!this.windowed) return null
      return {
        '--field-row-height': ROW_HEIGHT + 'px',
        '--field-before': this.first * ROW_HEIGHT + 'px',
        '--field-after': (this.rows.length - this.first - this.visibleRows.length) * ROW_HEIGHT + 'px',
      }
    },
    tracksScroll() { return this.windowed || this.more },
  },
  watch: {
    tracksScroll() { this.$nextTick(this.observe) },
    rows() { if (this.tracksScroll) this.$nextTick(this.scheduleMeasure) },
    more(value) { if (value) this.$nextTick(this.scheduleMeasure) },
  },
  mounted() { this.observe() },
  activated() { this.observe() },
  deactivated() { this.disconnect() },
  beforeUnmount() { this.disconnect() },
  methods: {
    observe() {
      this.disconnect()
      const table = this.$refs.table?.$el
      if (!this.tracksScroll || !table?.isConnected) return
      const wrap = table.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')
      if (!wrap) return
      this.scrollView = wrap.firstElementChild
      this.clipParents = []
      // 固定表格由内部滚动条滚动，自适应高度由工作区滚动；两者也可能嵌套。
      for (let el = wrap; el; el = el.parentElement) {
        if (/auto|scroll|hidden|clip/.test(getComputedStyle(el).overflowY)) this.clipParents.push(el)
      }
      window.addEventListener('scroll', this.scheduleMeasure, { capture: true, passive: true })
      window.addEventListener('resize', this.scheduleMeasure)
      if (typeof ResizeObserver !== 'undefined') {
        this.sizeObserver = new ResizeObserver(this.scheduleMeasure)
        this.clipParents.forEach(el => this.sizeObserver.observe(el))
      }
      this.scheduleMeasure()
    },
    disconnect() {
      window.removeEventListener('scroll', this.scheduleMeasure, true)
      window.removeEventListener('resize', this.scheduleMeasure)
      this.sizeObserver?.disconnect()
      if (this.measureFrame) cancelAnimationFrame(this.measureFrame)
      this.measureFrame = 0
      this.scrollView = null
    },
    scheduleMeasure() {
      if (!this.scrollView || this.measureFrame) return
      this.measureFrame = requestAnimationFrame(this.measure)
    },
    measure() {
      this.measureFrame = 0
      if (!this.scrollView?.isConnected || !this.scrollView.clientWidth) return
      const wrap = this.scrollView.parentElement
      let top, bottom
      if (wrap.scrollHeight > wrap.clientHeight + 1) {
        // 内表独立滚动时，预备整个内表视口，包括暂时被外表裁切的部分。
        top = wrap.getBoundingClientRect().top
        bottom = top + wrap.clientHeight
      } else {
        top = 0
        bottom = window.innerHeight
        for (const el of this.clipParents) {
          const rect = el.getBoundingClientRect()
          top = Math.max(top, rect.top)
          bottom = Math.min(bottom, rect.bottom)
        }
      }
      if (bottom <= top) return
      const origin = this.scrollView.getBoundingClientRect().top
      this.start = Math.max(0, Math.floor((top - origin) / ROW_HEIGHT) - BUFFER)
      this.visibleCount = Math.ceil((bottom - top) / ROW_HEIGHT) + 2 * BUFFER + 1
      if (this.more && bottom >= this.scrollView.getBoundingClientRect().bottom - BUFFER * ROW_HEIGHT) this.$emit('load-more')
    },
  },
}
</script>

<style scoped>
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view) { overflow-anchor: none; }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::before,
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::after { display: block; content: ''; }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::before { height: var(--field-before); }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::after { height: var(--field-after); }
.windowed-field-table :deep(.el-table__body .el-table__row) { height: var(--field-row-height); }
.windowed-field-table :deep(.el-table__row > .el-table__cell) { padding-block: 0; }
</style>
