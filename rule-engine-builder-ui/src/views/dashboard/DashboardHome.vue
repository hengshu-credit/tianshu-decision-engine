<template>
  <div class="uiue-list-page dashboard-page">
    <div class="module-hint dashboard-heading">
      <div class="dashboard-heading-line">
        <span class="hint-title">数据看板</span>
        <span class="dashboard-heading-divider" aria-hidden="true" />
        <span
          class="hint-text"
          title="基于执行、外数、名单、数据库与账单日志自动聚合，仅展示当前用户有权限的模块。"
        >基于执行、外数、名单、数据库与账单日志自动聚合，仅展示当前用户有权限的模块。</span>
      </div>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar dashboard-filters">
      <el-form
        class="dashboard-filter-form"
        :inline="true"
        size="small"
        @keyup.enter="refreshAll"
      >
        <el-form-item label="项目">
          <el-select
            v-model="filters.projectId"
            clearable
            filterable
            placeholder="全部有权限项目"
            style="width: 190px"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码">
          <remote-filter-select
            v-model:value="filters.ruleCode"
            :fetch-options="fetchRuleCodeOptions"
            option-label-key="ruleCode"
            option-value-key="ruleCode"
            allow-free-input
            placeholder="全部规则编码"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="规则名称">
          <remote-filter-select
            v-model:value="filters.ruleName"
            :fetch-options="fetchRuleNameOptions"
            option-label-key="ruleName"
            option-value-key="ruleName"
            allow-free-input
            placeholder="全部规则名称"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="统计时间">
          <el-date-picker
            v-model="dateRange"
            :shortcuts="dateShortcuts"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="refreshAll">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <el-button
        class="dashboard-settings-button"
        size="small"
        @click="$router.push('/dashboard/settings')"
      >看板设置</el-button>
    </div>

    <el-alert
      v-if="rangeError"
      :title="rangeError"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="dashboard-sections">
      <dashboard-section
        title="进件与决策"
        description="同一项目优先按请求编号、其次按 Trace ID 去重，保留时间最新的一条。"
        :loading="sections.applications.loading"
        :loaded="sections.applications.loaded"
        :error="sections.applications.error"
        @retry="loadApplications"
      >
        <template v-if="applications.visible !== false">
          <div v-if="applications.mappingIssues && applications.mappingIssues.length" class="dashboard-warning">
            <el-alert
              title="部分字段映射已失效，对应指标暂不统计，请前往字段映射设置处理。"
              type="warning"
              :closable="false"
              show-icon
            />
          </div>
          <div class="dashboard-metrics">
            <metric-card label="进件数" :value="number(summary.applicationCount)" />
            <metric-card label="通过数" :value="number(summary.passCount)" tone="success" />
            <metric-card label="人审数" :value="number(summary.reviewCount)" tone="warning" />
            <metric-card label="拒绝数" :value="number(summary.rejectCount)" tone="danger" />
            <metric-card label="人审率" :value="percent(summary.reviewRate)" tone="warning" />
            <metric-card label="通过率" :value="percent(summary.passRate)" tone="success" />
            <metric-card label="进件设备数" :value="number(summary.deviceCount)" />
            <metric-card label="未分类数" :value="number(summary.unclassifiedCount)" tone="muted" />
          </div>
          <div class="dashboard-chart-grid">
            <chart-card title="期数占比" :note="distributionNote(applications.periods)">
              <dashboard-chart
                aria-label="期数占比柱状图"
                :empty="emptyDistribution(applications.periods)"
                :option="periodOption"
              />
            </chart-card>
            <chart-card title="金额占比" :note="distributionNote(applications.amounts)">
              <dashboard-chart
                aria-label="金额占比柱状图"
                :empty="emptyDistribution(applications.amounts)"
                :option="amountOption"
              />
            </chart-card>
            <chart-card class="dashboard-chart-card--wide" title="进件地图热力图" :note="geoNote">
              <template #actions>
                <el-button
                  data-testid="dashboard-map-reset"
                  size="small"
                  @click="resetMapView"
                >恢复视角</el-button>
              </template>
              <dashboard-chart
                :key="mapViewVersion"
                aria-label="进件地图热力图"
                height="360px"
                :empty="!mapReady || !applications.geo || !applications.geo.points.length"
                :empty-description="mapReady ? '暂无合法经纬度记录' : '本地地图正在加载'"
                :option="geoOption"
              />
            </chart-card>
          </div>
        </template>
        <el-empty v-else description="无规则查看权限，进件分析已隐藏" />
      </dashboard-section>

      <dashboard-section
        title="数据资源与调用"
        description="查询时效基于运行调用日志；外数只统计实际向供应商发起的 API 请求。"
        :loading="sections.operations.loading"
        :loaded="sections.operations.loaded"
        :error="sections.operations.error"
        @retry="loadOperations"
      >
        <div v-if="hasOperations" class="dashboard-operation-grid">
          <chart-card v-if="operations.ruleExecution && operations.ruleExecution.visible" title="规则执行耗时分布" :note="timingNote(operations.ruleExecution.timing)">
            <dashboard-chart
              aria-label="规则执行耗时分布"
              :empty="!sampleCount(operations.ruleExecution.timing)"
              :option="timingOption(operations.ruleExecution.timing)"
            />
          </chart-card>
          <resource-card
            v-if="operations.database && operations.database.visible"
            title="数据库数据源"
            :count="operations.database.resourceCount"
            count-label="数据源个数"
            :calls="operations.database.calls"
          />
          <chart-card v-if="operations.lists && operations.lists.visible" title="名单分类个数和占比" :note="timingNote(operations.lists.calls && operations.lists.calls.timing)">
            <div class="dashboard-inline-metric">名单库 <strong>{{ number(operations.lists.libraryCount) }}</strong> 个</div>
            <dashboard-chart
              aria-label="名单分类占比"
              :empty="emptyDistribution(operations.lists.categories)"
              :option="listCategoryOption"
            />
          </chart-card>
          <resource-card
            v-if="operations.datasource && operations.datasource.visible"
            title="上游数据源 API"
            :count="operations.datasource.resourceCount"
            count-label="API 个数"
            :calls="operations.datasource.calls"
            show-found
          />
          <resource-card
            v-if="operations.downstreamRules && operations.downstreamRules.visible"
            title="下游项目规则"
            :count="operations.downstreamRules.count"
            count-label="规则个数"
          />
          <chart-card v-if="operations.billing && operations.billing.visible" title="账单调用查得情况" note="计费总额按币种分别展示，不跨币种相加。">
            <div v-if="operations.datasource && operations.datasource.visible" class="dashboard-billing-summary">
              <span>请求数 <strong>{{ number(operations.datasource.calls.requestCount) }}</strong></span>
              <span>查得数 <strong>{{ number(operations.datasource.calls.foundCount) }}</strong></span>
              <span>查得率 <strong>{{ percent(operations.datasource.calls.foundRate) }}</strong></span>
            </div>
            <div class="billing-amounts">
              <div v-for="item in operations.billing.amounts" :key="item.currency">
                <span>{{ item.currency }}</span>
                <strong>{{ money(item.amount) }}</strong>
              </div>
              <el-empty v-if="!operations.billing.amounts.length" description="暂无计费记录" :image-size="48" />
            </div>
          </chart-card>
        </div>
        <el-empty v-else description="无相关模块查看权限" />
      </dashboard-section>

      <dashboard-section
        title="规则命中与审批"
        description="规则集按命中次数排序取前 10；审批分布固定展示六种生命周期状态。"
        :loading="sections.governance.loading"
        :loaded="sections.governance.loaded"
        :error="sections.governance.error"
        @retry="loadGovernance"
      >
        <div v-if="hasGovernance" class="dashboard-chart-grid">
          <chart-card v-if="governance.ruleSetHits && governance.ruleSetHits.visible" title="规则集命中规则 TOP" note="进件数 / 命中数 / 命中率">
            <dashboard-chart
              aria-label="规则集命中规则 TOP"
              :empty="!governance.ruleSetHits.items.length"
              :option="ruleSetOption"
            />
          </chart-card>
          <chart-card v-if="governance.approvals && governance.approvals.visible" title="规则审批情况分布" :note="`审批总数 ${number(governance.approvals.totalCount)}`">
            <dashboard-chart
              aria-label="规则审批情况分布"
              :empty="!governance.approvals.totalCount"
              :option="approvalOption"
            />
          </chart-card>
        </div>
        <el-empty v-else description="无规则或审批查看权限" />
      </dashboard-section>
    </div>
  </div>
</template>

<script>
import {
  getDashboardApplications,
  getDashboardGovernance,
  getDashboardOperations
} from '@/api/dashboard'
import { listDefinitions, listProjectDefinitions } from '@/api/definition'
import { listProjects } from '@/api/project'
import {
  distributionBarOption,
  distributionPieOption,
  geoHeatmapOption,
  registerDashboardMap,
  ruleSetHitOption
} from '@/charts/dashboardCharts'
import DashboardChart from '@/components/dashboard/DashboardChart.vue'
import ChartCard from '@/components/dashboard/DashboardChartCard.vue'
import MetricCard from '@/components/dashboard/DashboardMetricCard.vue'
import ResourceCard from '@/components/dashboard/DashboardResourceCard.vue'
import DashboardSection from '@/components/dashboard/DashboardSection.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import {
  dashboardQuickRange,
  defaultDashboardFilters,
  readDashboardMapView,
  readDashboardFilters,
  validateDashboardRange,
  writeDashboardFilters
} from '@/utils/dashboardFilters'

export default {
  name: 'DashboardHome',
  components: {
    DashboardChart,
    DashboardSection,
    RemoteFilterSelect,
    MetricCard,
    ChartCard,
    ResourceCard
  },
  data() {
    const filters = readDashboardFilters(window.sessionStorage)
    return {
      filters,
      dateRange: [filters.startTime, filters.endTime],
      dateShortcuts: [
        { text: '今天', value: () => dashboardQuickRange(1) },
        { text: '近7天', value: () => dashboardQuickRange(7) },
        { text: '近30天', value: () => dashboardQuickRange(30) }
      ],
      projects: [],
      rangeError: '',
      mapReady: false,
      mapView: readDashboardMapView(window.sessionStorage),
      mapViewVersion: 0,
      themeVersion: 0,
      sections: {
        applications: { loading: false, loaded: false, error: '', data: {} },
        operations: { loading: false, loaded: false, error: '', data: {} },
        governance: { loading: false, loaded: false, error: '', data: {} }
      }
    }
  },
  computed: {
    applications() { return this.sections.applications.data || {} },
    summary() { return this.applications.summary || {} },
    operations() { return this.sections.operations.data || {} },
    governance() { return this.sections.governance.data || {} },
    hasOperations() {
      return ['ruleExecution', 'database', 'lists', 'datasource',
        'downstreamRules', 'billing'].some(key => this.operations[key] && this.operations[key].visible)
    },
    hasGovernance() {
      return Boolean(
        (this.governance.ruleSetHits && this.governance.ruleSetHits.visible) ||
        (this.governance.approvals && this.governance.approvals.visible)
      )
    },
    periodOption() {
      void this.themeVersion
      return distributionBarOption((this.applications.periods || {}).items || [])
    },
    amountOption() {
      void this.themeVersion
      return distributionBarOption((this.applications.amounts || {}).items || [])
    },
    geoOption() {
      void this.themeVersion
      return geoHeatmapOption((this.applications.geo || {}).points || [], {
        center: [this.mapView.longitude, this.mapView.latitude],
        zoom: this.mapView.zoom
      })
    },
    listCategoryOption() {
      void this.themeVersion
      return distributionPieOption((this.operations.lists && this.operations.lists.categories
        ? this.operations.lists.categories.items : []) || [])
    },
    ruleSetOption() {
      void this.themeVersion
      return ruleSetHitOption(
        ((this.governance.ruleSetHits || {}).items || [])
      )
    },
    approvalOption() {
      void this.themeVersion
      return distributionPieOption(((this.governance.approvals || {}).statuses || []))
    },
    geoNote() {
      const geo = this.applications.geo || {}
      return `合法记录 ${this.number(geo.validCount)}，排除 ${this.number(geo.excludedCount)}`
    }
  },
  mounted() {
    window.addEventListener('tianshu-theme-change', this.handleThemeChange)
    this.loadProjects()
    this.loadMap()
    this.refreshAll()
  },
  beforeUnmount() {
    window.removeEventListener('tianshu-theme-change', this.handleThemeChange)
  },
  methods: {
    resetMapView() {
      this.mapView = readDashboardMapView(window.sessionStorage)
      this.mapViewVersion += 1
    },
    async loadProjects() {
      try {
        const response = await listProjects({ pageNum: 1, pageSize: 1000, status: 1 })
        this.projects = (response.data && response.data.records) || []
      } catch (error) {
        this.projects = []
      }
    },
    async loadMap() {
      try {
        const response = await fetch('/maps/dashboard-world.geojson')
        if (!response.ok) throw new Error('地图资源加载失败')
        registerDashboardMap(await response.json())
        this.mapReady = true
      } catch (error) {
        this.mapReady = false
      }
    },
    queryParams() {
      return {
        projectId: this.filters.projectId,
        ruleCode: (this.filters.ruleCode || '').trim(),
        ruleName: (this.filters.ruleName || '').trim(),
        startTime: this.filters.startTime,
        endTime: this.filters.endTime
      }
    },
    syncDateRange() {
      if (Array.isArray(this.dateRange) && this.dateRange.length === 2) {
        this.filters.startTime = this.dateRange[0]
        this.filters.endTime = this.dateRange[1]
      }
      const result = validateDashboardRange(
        this.filters.startTime, this.filters.endTime
      )
      this.rangeError = result.message
      return result.valid
    },
    refreshAll() {
      if (!this.syncDateRange()) return Promise.resolve()
      this.filters.ruleCode = (this.filters.ruleCode || '').trim()
      this.filters.ruleName = (this.filters.ruleName || '').trim()
      writeDashboardFilters(window.sessionStorage, this.filters)
      return Promise.allSettled([
        this.loadApplications(),
        this.loadOperations(),
        this.loadGovernance()
      ])
    },
    loadApplications() {
      return this.loadSection('applications', getDashboardApplications)
    },
    loadOperations() {
      return this.loadSection('operations', getDashboardOperations)
    },
    loadGovernance() {
      return this.loadSection('governance', getDashboardGovernance)
    },
    async loadSection(key, loader) {
      const section = this.sections[key]
      section.loading = true
      section.error = ''
      try {
        const response = await loader(this.queryParams())
        section.data = response.data || {}
        section.loaded = true
      } catch (error) {
        section.error = error.message || '加载失败'
      } finally {
        section.loading = false
      }
    },
    resetFilters() {
      this.filters = defaultDashboardFilters()
      this.dateRange = [this.filters.startTime, this.filters.endTime]
      return this.refreshAll()
    },
    fetchRuleCodeOptions({ query, pageNum, pageSize }) {
      const params = {
        pageNum,
        pageSize,
        ruleCode: query || '',
        ruleName: this.filters.ruleName || ''
      }
      return this.filters.projectId
        ? listProjectDefinitions(this.filters.projectId, params)
        : listDefinitions(params)
    },
    fetchRuleNameOptions({ query, pageNum, pageSize }) {
      const params = {
        pageNum,
        pageSize,
        ruleCode: this.filters.ruleCode || '',
        ruleName: query || ''
      }
      return this.filters.projectId
        ? listProjectDefinitions(this.filters.projectId, params)
        : listDefinitions(params)
    },
    handleThemeChange() { this.themeVersion += 1 },
    projectLabel(project) {
      return `${project.projectName || project.projectCode}（${project.projectCode}）`
    },
    number(value) { return Number(value || 0).toLocaleString('zh-CN') },
    money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 6 }) },
    percent(value) { return `${(Number(value || 0) * 100).toFixed(2)}%` },
    sampleCount(timing) { return Number((timing || {}).sampleCount || 0) },
    timingNote(timing) {
      const value = timing || {}
      return `平均 ${Number(value.avgMs || 0).toFixed(2)} ms · P95 ${Number(value.p95Ms || 0).toFixed(2)} ms · P99 ${Number(value.p99Ms || 0).toFixed(2)} ms`
    },
    timingOption(timing) {
      void this.themeVersion
      return distributionBarOption(((timing || {}).distribution || {}).items || [])
    },
    emptyDistribution(distribution) {
      return !distribution || !Number(distribution.validCount || 0)
    },
    distributionNote(distribution) {
      const value = distribution || {}
      return `有效 ${this.number(value.validCount)}，排除 ${this.number(value.excludedCount)}`
    }
  }
}
</script>

<style scoped>
.dashboard-page {
  display: grid;
  align-content: start;
  gap: 16px;
}

.dashboard-heading {
  min-width: 0;
  padding: 12px 16px;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 8px;
}

.dashboard-heading-line {
  display: flex;
  align-items: center;
  min-width: 0;
  white-space: nowrap;
}

.dashboard-heading-line .hint-title {
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 600;
}

.dashboard-heading-divider {
  flex: 0 0 auto;
  width: 1px;
  height: 14px;
  margin: 0 12px;
  background: var(--tianshu-info-border);
}

.dashboard-heading-line .hint-text {
  min-width: 0;
  overflow: hidden;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
}

.dashboard-filters {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 0;
}

.dashboard-filter-form {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  min-width: 0;
  row-gap: 8px;
}

:deep(.dashboard-filter-form .el-form-item) {
  margin-bottom: 0;
}

.dashboard-settings-button {
  flex: 0 0 auto;
  margin-left: auto;
}

.dashboard-sections {
  display: grid;
  gap: 18px;
}

.dashboard-warning {
  margin-bottom: 14px;
}

.dashboard-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

:deep(.dashboard-metric) {
  display: grid;
  gap: 8px;
  min-height: 88px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--tianshu-bg-subtle);
}

:deep(.dashboard-metric span) {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

:deep(.dashboard-metric strong) {
  color: var(--el-color-primary);
  font-size: 25px;
  line-height: 1;
}

:deep(.dashboard-metric[data-tone="success"] strong) { color: var(--el-color-success); }
:deep(.dashboard-metric[data-tone="warning"] strong) { color: var(--el-color-warning); }
:deep(.dashboard-metric[data-tone="danger"] strong) { color: var(--el-color-danger); }
:deep(.dashboard-metric[data-tone="muted"] strong) { color: var(--el-text-color-secondary); }

.dashboard-chart-grid,
.dashboard-operation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.dashboard-operation-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

:deep(.dashboard-chart-card) {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--tianshu-bg-subtle);
}

:deep(.dashboard-chart-card header) {
  margin-bottom: 8px;
}

:deep(.dashboard-chart-card h3) {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

:deep(.dashboard-chart-card header p) {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard-chart-card--wide {
  grid-column: 1 / -1;
}

:deep(.dashboard-resource-count) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 18px 0;
  color: var(--el-text-color-secondary);
}

:deep(.dashboard-resource-count strong) {
  color: var(--el-color-primary);
  font-size: 28px;
}

:deep(.dashboard-resource-stats) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.dashboard-resource-stats span) {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

:deep(.dashboard-resource-stats b) { color: var(--el-text-color-primary); }

.dashboard-inline-metric {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.dashboard-inline-metric strong { color: var(--el-color-primary); font-size: 20px; }

.billing-amounts {
  display: grid;
  gap: 10px;
  padding-top: 16px;
}

.dashboard-billing-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.dashboard-billing-summary span {
  display: grid;
  gap: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard-billing-summary strong {
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.billing-amounts > div {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-bg-color);
}

.billing-amounts strong { color: var(--el-color-primary); }

@media (max-width: 1200px) {
  .dashboard-metrics { grid-template-columns: repeat(2, minmax(150px, 1fr)); }
  .dashboard-operation-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .dashboard-metrics,
  .dashboard-chart-grid,
  .dashboard-operation-grid { grid-template-columns: 1fr; }
  .dashboard-chart-card--wide { grid-column: auto; }
}
</style>
