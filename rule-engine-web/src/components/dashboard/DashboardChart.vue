<template>
  <div class="dashboard-chart-shell" :style="{ height }">
    <div
      v-show="!empty"
      ref="chart"
      class="dashboard-chart"
      role="img"
      :aria-label="ariaLabel"
    />
    <el-empty v-if="empty" :description="emptyDescription" :image-size="64" />
  </div>
</template>

<script>
import { createChartInstance } from '@/charts/dashboardCharts'

export default {
  name: 'DashboardChart',
  emits: ['region-click'],
  props: {
    option: { type: Object, default: null },
    empty: { type: Boolean, default: false },
    emptyDescription: { type: String, default: '暂无统计数据' },
    ariaLabel: { type: String, required: true },
    height: { type: String, default: '280px' }
  },
  watch: {
    option: {
      deep: true,
      handler() {
        this.scheduleRender()
      }
    },
    empty() {
      this.scheduleRender()
    }
  },
  mounted() {
    window.addEventListener('tianshu-theme-change', this.handleThemeChange)
    if (typeof ResizeObserver === 'function') {
      this.resizeObserver = new ResizeObserver(() => this.resize())
      this.resizeObserver.observe(this.$el)
    } else {
      window.addEventListener('resize', this.resize)
    }
    this.scheduleRender()
  },
  beforeUnmount() {
    window.removeEventListener('tianshu-theme-change', this.handleThemeChange)
    window.removeEventListener('resize', this.resize)
    if (this.renderTimer) window.clearTimeout(this.renderTimer)
    if (this.resizeTimer) window.clearTimeout(this.resizeTimer)
    if (this.resizeObserver) this.resizeObserver.disconnect()
    if (this.chart) this.chart.dispose()
  },
  methods: {
    scheduleRender() {
      if (this.renderTimer) window.clearTimeout(this.renderTimer)
      this.renderTimer = window.setTimeout(() => {
        this.renderChart()
        this.resize()
      }, 50)
    },
    renderChart() {
      if (this.empty || !this.option || !this.$refs.chart) return
      if (!this.chart) {
        this.chart = createChartInstance(this.$refs.chart)
        this.chart.on('click', this.handleChartClick)
      }
      this.chart.setOption(this.option, true)
    },
    handleChartClick(params) {
      if (params.componentType === 'geo' || (params.seriesType === 'map' && params.name)) {
        this.$emit('region-click', params.name)
      }
    },
    handleThemeChange() {
      this.scheduleRender()
    },
    resize() {
      if (!this.chart) return
      const width = this.$el.clientWidth
      const height = this.$el.clientHeight
      if (width > 0 && height > 0) this.chart.resize({ width, height })
    }
  }
}
</script>

<style scoped>
.dashboard-chart-shell {
  position: relative;
  min-height: 220px;
}

.dashboard-chart {
  width: 100%;
  height: 100%;
}
</style>
