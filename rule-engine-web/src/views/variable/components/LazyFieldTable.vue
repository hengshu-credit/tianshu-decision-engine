<template>
  <el-table ref="table" :data="visibleRows"
    :class="{ 'windowed-field-table': windowed, 'uniform-field-rows': windowed || stickyParents }"
    :row-key="stickyParents && windowed ? treeRowKey : rowKey"
    :row-class-name="stickyParents ? parentRowClass : undefined"
    :row-style="stickyParents ? parentRowStyle : undefined"
    :style="spacing"
  >
    <slot />
  </el-table>
</template>

<script>
const ROW_HEIGHT = 42
const BUFFER = 8
const TREE_WINDOW_STEP = 4

export default {
  name: 'LazyFieldTable',
  props: {
    data: { type: Array, required: true },
    rowKey: [String, Function],
    more: Boolean,
    stickyParents: Boolean,
  },
  emits: ['load-more'],
  data: () => ({ start: 0, visibleCount: 32, topRow: 0, parentLimit: 0, innerScroll: false }),
  computed: {
    windowed() { return this.data.length > 50 },
    first() { return this.windowed ? Math.min(this.start, Math.max(0, this.data.length - this.visibleCount)) : 0 },
    // 记录每个展开分组的父行和结束位置。滚动时只沿视口首行的祖先链查找。
    treeBounds() {
      const parents = [], ends = [], stack = []
      this.data.forEach((row, index) => {
        while (stack.length && this.data[stack.at(-1)].depth >= row.depth) ends[stack.pop()] = index
        parents[index] = stack.at(-1)
        if (row.expandable && row.expanded) stack.push(index)
      })
      while (stack.length) ends[stack.pop()] = this.data.length
      return { parents, ends }
    },
    pinnedIndexes(_, previous) {
      const pinned = []
      if (!this.stickyParents || !this.parentLimit) return pinned
      const { parents, ends } = this.treeBounds
      let end = this.data.length
      for (let depth = 0; depth < this.parentLimit; depth++) {
        let index = this.topRow + depth
        if (index >= end) break
        while (this.data[index].depth > depth) index = parents[index]
        if (this.data[index].depth !== depth || ends[index] == null) break
        pinned.push(index)
        end = ends[index]
      }
      // 同一分组内逐行滚动时沿用已渲染的父行，避免触发表体重新渲染。
      if (previous?.length === pinned.length && pinned.every((index, i) => index === previous[i])) return previous
      return pinned
    },
    retainedParents() {
      return this.pinnedIndexes.filter(index => index < this.first)
    },
    visibleRows() {
      if (!this.windowed) return this.data
      const rows = this.data.slice(this.first, this.first + this.visibleCount)
      return this.retainedParents.map(index => this.data[index]).concat(rows)
    },
    parentStyles() {
      const styles = new Map()
      this.pinnedIndexes.forEach((index, level) => {
        const last = this.treeBounds.ends[index] - 1
        const zIndex = this.pinnedIndexes.length - level + 2
        if (this.innerScroll) {
          styles.set(this.data[index], {
            position: 'sticky', zIndex,
            top: `min(${level * ROW_HEIGHT}px, calc(${last * ROW_HEIGHT}px - var(--field-scroll-offset, 0px)))`,
          })
        } else {
          const retained = this.retainedParents.indexOf(index)
          const physical = retained < 0 ? index : this.first - this.retainedParents.length + retained
          const offset = (level - physical) * ROW_HEIGHT
          const limit = (last - physical) * ROW_HEIGHT
          styles.set(this.data[index], {
            position: 'relative', zIndex,
            transform: `translateY(clamp(0px, calc(var(--field-scroll-offset, 0px) + ${offset}px), ${limit}px))`,
          })
        }
      })
      return styles
    },
    spacing() {
      if (!this.windowed && !this.stickyParents) return null
      return {
        '--field-row-height': ROW_HEIGHT + 'px',
        '--field-before': (this.first - this.retainedParents.length) * ROW_HEIGHT + 'px',
        '--field-after': Math.max(0, this.data.length - this.first - this.visibleCount) * ROW_HEIGHT + 'px',
      }
    },
    tracksScroll() { return this.windowed || this.more || this.stickyParents },
  },
  watch: {
    tracksScroll() { this.$nextTick(this.observe) },
    data() { if (this.tracksScroll) this.$nextTick(this.scheduleMeasure) },
    more(value) { if (value) this.$nextTick(this.scheduleMeasure) },
  },
  mounted() { this.observe() },
  activated() { this.observe() },
  deactivated() { this.disconnect() },
  beforeUnmount() { this.disconnect() },
  methods: {
    treeRowKey(row) {
      // 普通字段复用同一对象内的表格行。父行保留稳定标识，行内操作仍绑定真实字段 ID。
      if (row.field && !row.expandable) return `slot:${row.node.object.id}:${row.index % this.visibleCount}`
      return row.key
    },
    parentRowClass({ row }) {
      if (this.parentStyles.has(row)) return 'field-pinned-parent'
    },
    parentRowStyle({ row }) {
      return this.parentStyles.get(row)
    },
    paintParents() {
      // 位移只作用于固定行，避免每帧在整张表的单元格与操作控件上重新计算继承样式。
      for (const row of this.scrollView?.querySelectorAll('.field-pinned-parent') || []) {
        row.style.setProperty('--field-scroll-offset', this.paintOffset + 'px')
      }
    },
    restorePinnedPosition(row) {
      const level = this.pinnedIndexes.findIndex(index => this.data[index] === row)
      if (level < 0 || !this.scrollView) return
      const scroller = this.clipParents.find(el =>
        el.scrollHeight > el.clientHeight + 1 && /auto|scroll/.test(getComputedStyle(el).overflowY))
      if (!scroller) return
      const naturalOffset = (this.pinnedIndexes[level] - level) * ROW_HEIGHT
      if (this.scrollOffset > naturalOffset) scroller.scrollTop -= this.scrollOffset - naturalOffset
    },
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
      const innerScroll = wrap.scrollHeight > wrap.clientHeight + 1
      if (innerScroll) {
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
      const offset = Math.max(0, top - origin)
      const topRow = Math.floor(offset / ROW_HEIGHT)
      if (this.stickyParents) {
        this.scrollOffset = offset
        this.innerScroll = innerScroll
        this.topRow = topRow
        this.parentLimit = Math.max(0, Math.floor((bottom - top) / ROW_HEIGHT) - 1)
        const nearGroupEnd = this.pinnedIndexes.some((index, level) => topRow + level >= this.treeBounds.ends[index] - 1)
        // 内部滚动由原生 sticky 定位，只在分组交接时需要额外位移。
        const paintOffset = innerScroll && !nearGroupEnd ? 0 : offset
        if (paintOffset || this.paintOffset) this.$nextTick(this.paintParents)
        this.paintOffset = paintOffset
      }
      // 预留一批已渲染的行，跨过缓冲区时再移动窗口。
      const windowRow = this.stickyParents ? topRow - topRow % TREE_WINDOW_STEP : topRow
      this.start = Math.max(0, windowRow - BUFFER)
      this.visibleCount = Math.ceil((bottom - top) / ROW_HEIGHT) + 2 * BUFFER + 1
      if (this.more && bottom >= this.scrollView.getBoundingClientRect().bottom - BUFFER * ROW_HEIGHT) this.$emit('load-more')
    },
  },
}
</script>

<style scoped>
@property --field-scroll-offset {
  syntax: '<length>';
  inherits: false;
  initial-value: 0px;
}
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view) { overflow-anchor: none; }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::before,
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::after { display: block; content: ''; }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::before { height: var(--field-before); }
.windowed-field-table :deep(.el-table__body-wrapper .el-scrollbar__view)::after { height: var(--field-after); }
.uniform-field-rows :deep(.el-table__body .el-table__row) { height: var(--field-row-height); }
.uniform-field-rows :deep(.el-table__row > .el-table__cell) { padding-block: 0; }
</style>
