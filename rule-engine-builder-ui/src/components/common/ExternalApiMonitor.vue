<template>
  <div class="external-api-monitor stats-panel" aria-label="外数监控看板">
    <div class="stats-heading">
      <div>
        <div class="log-title">外数供应商质量看板</div>
        <div class="log-subtitle">
          全部指标从 API
          外数调用日志表统计；缓存命中只计算缓存期内命中的数据。
        </div>
      </div>
      <el-button size="small" :icon="ElIconRefresh" @click="loadStats"
        >刷新指标</el-button
      >
    </div>
    <async-state
      :loading="statsLoading"
      :error="statsError"
      :empty="externalStats.providers.length === 0"
      empty-text="暂无 API 外数调用数据"
      @retry="loadStats"
    >
      <!--
            不使用 Element Plus el-row/el-col，也不依赖 scoped 样式是否被正确注入。
            关键布局直接写入内联样式，避免项目中的全局样式覆盖列宽。
          -->
      <div
        class="datasource-stats-layout"
        data-layout="two-rows-four-columns"
        style="
          display: flex !important;
          flex-flow: row wrap !important;
          align-items: stretch !important;
          width: 100% !important;
          margin: 0 -6px 4px !important;
        "
      >
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>供应商查询次数</span
            ><strong>{{ statsOverview.queryCount || 0 }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>缓存命中率</span
            ><strong>{{ formatRate(statsOverview.cacheHitRate) }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>请求成功率</span
            ><strong>{{
              formatRate(statsOverview.requestSuccessRate)
            }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>失败率</span
            ><strong>{{ formatRate(statsOverview.failureRate) }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>查得率</span
            ><strong>{{ formatRate(statsOverview.foundRate) }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>平均耗时</span
            ><strong>{{ formatMs(statsOverview.avgCostTimeMs) }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>P95 耗时</span
            ><strong>{{ formatMs(statsOverview.p95CostTimeMs) }}</strong>
          </div>
        </div>
        <div
          class="datasource-stat-cell"
          style="
            box-sizing: border-box !important;
            flex: 0 0 25% !important;
            width: 25% !important;
            max-width: 25% !important;
            padding: 0 6px 12px !important;
          "
        >
          <div
            class="stat-card"
            style="width: 100% !important; height: 100% !important"
          >
            <span>P99 耗时</span
            ><strong>{{ formatMs(statsOverview.p99CostTimeMs) }}</strong>
          </div>
        </div>
      </div>
      <el-table show-overflow-tooltip
        :data="externalStats.providers"
        border
        size="small"
        class="provider-table"
      >
        <el-table-column
          prop="targetCode"
          label="接口编码"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="targetName"
          label="接口名称"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="queryCount"
          label="查询次数"
          width="90"
          align="right"
        />
        <el-table-column
          prop="requestSuccessRate"
          label="成功率"
          width="90"
          align="right"
        >
          <template v-slot="{ row }">{{
            formatRate(row.requestSuccessRate)
          }}</template>
        </el-table-column>
        <el-table-column
          prop="failureRate"
          label="失败率"
          width="90"
          align="right"
        >
          <template v-slot="{ row }">{{
            formatRate(row.failureRate)
          }}</template>
        </el-table-column>
        <el-table-column
          prop="foundRate"
          label="查得率"
          width="90"
          align="right"
        >
          <template v-slot="{ row }">{{
            formatRate(row.foundRate)
          }}</template>
        </el-table-column>
        <el-table-column
          prop="cacheHitRate"
          label="缓存命中率"
          width="110"
          align="right"
        >
          <template v-slot="{ row }">{{
            formatRate(row.cacheHitRate)
          }}</template>
        </el-table-column>
        <el-table-column
          prop="p95CostTimeMs"
          label="P95(ms)"
          width="90"
          align="right"
        />
        <el-table-column
          prop="p99CostTimeMs"
          label="P99(ms)"
          width="90"
          align="right"
        />
      </el-table>
    </async-state>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Refresh as ElIconRefresh } from '@element-plus/icons-vue'
import { getExternalApiStats } from '@/api/runtimeLog'
import AsyncState from '@/components/common/AsyncState.vue'

export default {
  name: 'ExternalApiMonitor',
  components: { AsyncState },
  data() {
    return {
      statsLoading: false,
      statsError: '',
      externalStats: { overview: {}, providers: [] },
      ElIconRefresh: markRaw(ElIconRefresh),
    }
  },
  computed: {
    statsOverview() {
      return this.externalStats && this.externalStats.overview
        ? this.externalStats.overview
        : {}
    },
  },
  created() {
    this.loadStats()
  },
  methods: {
    async loadStats() {
      this.statsLoading = true
      this.statsError = ''
      try {
        const res = await getExternalApiStats()
        const data = res && res.data ? res.data : {}
        this.externalStats = {
          overview: data.overview || {},
          providers: data.providers || [],
        }
      } catch (e) {
        this.statsError = (e && e.message) || '外数供应商质量指标加载失败'
      } finally {
        this.statsLoading = false
      }
    },
    formatRate(value) {
      const number = Number(value)
      return Number.isFinite(number) ? (number * 100).toFixed(2) + '%' : '0.00%'
    },
    formatMs(value) {
      const number = Number(value)
      return (
        (Number.isFinite(number)
          ? number.toFixed(number % 1 === 0 ? 0 : 2)
          : '0') + ' ms'
      )
    },
  },
}
</script>

<style scoped>
.external-api-monitor {
  margin-top: 16px;
}
.log-title {
  color: var(--tianshu-text-primary);
  font-weight: 700;
}
.log-subtitle {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  margin-top: 3px;
}
.stats-panel {
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-soft);
  padding: 14px;
  margin-bottom: 16px;
}
.stats-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.datasource-stats-layout {
  display: flex !important;
  flex-flow: row wrap !important;
  align-items: stretch !important;
  width: 100% !important;
  margin: 0 -6px 4px !important;
}
.datasource-stat-cell {
  box-sizing: border-box !important;
  flex: 0 0 25% !important;
  width: 25% !important;
  max-width: 25% !important;
  padding: 0 6px 12px !important;
}
.stat-card {
  min-width: 0;
  min-height: 86px;
  padding: 16px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}
.stat-card span {
  color: var(--tianshu-text-tertiary);
  font-size: 13px;
}
.stat-card strong {
  color: var(--tianshu-text-primary);
  font-size: 24px;
  line-height: 1.2;
}
.provider-table {
  background: var(--tianshu-bg-surface);
}
.stats-empty {
  border: 1px dashed var(--tianshu-border);
  border-radius: 4px;
  color: var(--tianshu-text-tertiary);
  text-align: center;
  padding: 24px;
}
</style>
