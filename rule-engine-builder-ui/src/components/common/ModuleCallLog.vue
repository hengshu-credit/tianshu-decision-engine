<template>
  <div class="module-call-log">
    <div class="log-header">
      <div>
        <div class="log-title">{{ title || profile.title }}</div>
        <div class="log-subtitle">{{ profile.subtitle }}</div>
      </div>
      <el-button size="small" :icon="ElIconRefresh" @click="load"
        >刷新</el-button
      >
    </div>

    <div v-if="moduleType === 'DATASOURCE'" class="stats-panel">
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
        <el-table
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

    <div class="log-filter">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="动作">
          <el-select
            v-model="query.actionType"
            clearable
            placeholder="全部"
            style="width: 160px"
          >
            <el-option
              v-for="item in actionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="profile.targetLabel">
          <el-input
            v-model="query.targetCode"
            clearable
            placeholder="前缀筛选"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="Trace ID">
          <el-input
            v-model="query.traceId"
            clearable
            placeholder="模块或规则 trace_id"
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item label="结果">
          <el-select
            v-model="query.success"
            clearable
            placeholder="全部"
            style="width: 100px"
          >
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table
      :data="rows"
      border
      size="small"
      v-loading="loading"
      style="width: 100%"
    >
      <el-table-column prop="actionType" label="动作" width="140">
        <template v-slot="{ row }">{{ actionLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column
        prop="targetCode"
        :label="profile.targetLabel"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="targetName"
        label="名称"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="traceId"
        label="模块 trace"
        min-width="190"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="profile.showMethod"
        prop="requestMethod"
        :label="profile.methodLabel"
        width="90"
        align="center"
      />
      <el-table-column
        v-if="profile.showResource"
        prop="requestUrl"
        :label="profile.resourceLabel"
        min-width="220"
        show-overflow-tooltip
      >
        <template v-slot="{ row }">{{
          row.requestUrl || profile.emptyResource
        }}</template>
      </el-table-column>
      <el-table-column
        v-if="profile.showProject"
        prop="projectCode"
        label="项目编码"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        :label="profile.summaryLabel"
        min-width="180"
        show-overflow-tooltip
      >
        <template v-slot="{ row }">{{ rowSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="结果" width="70" align="center">
        <template v-slot="{ row }">
          <el-tag
            :type="row.success === 1 ? 'success' : 'danger'"
            size="small"
            >{{ row.success === 1 ? '成功' : '失败' }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column
        prop="costTimeMs"
        label="耗时(ms)"
        width="90"
        align="center"
      />
      <el-table-column prop="createTime" label="时间" width="160" fixed="right">
        <template v-slot="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template v-slot="{ row }">
          <el-button link size="small" @click="openDetail(row)"
            >详情</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="log-pager"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10, 30, 50, 100]"
      @current-change="
        (p) => {
          query.pageNum = p
          load()
        }
      "
      @size-change="
        (s) => {
          query.pageSize = s
          query.pageNum = 1
          load()
        }
      "
    />

    <el-drawer
      :title="profile.detailTitle"
      v-model="detailVisible"
      size="70%"
    >
      <div v-if="detail" class="log-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模块">{{
            profile.title
          }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{
            actionLabel(detail.actionType)
          }}</el-descriptions-item>
          <el-descriptions-item :label="profile.targetLabel">{{
            detail.targetCode || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{
            detail.targetName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="profile.showProject" label="项目编码">{{
            detail.projectCode || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{
            detail.success === 1 ? '成功' : '失败'
          }}</el-descriptions-item>
          <el-descriptions-item label="模块 trace_id" :span="2">{{
            detail.traceId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="规则 trace_id" :span="2">{{
            detail.ruleTraceId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item
            v-if="detail.requestUrl"
            :label="profile.resourceLabel"
            :span="2"
            >{{ detail.requestUrl }}</el-descriptions-item
          >
          <el-descriptions-item label="错误信息" :span="2">{{
            detail.errorMessage || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="moduleType === 'DATABASE'">
          <div class="detail-grid">
            <div class="detail-kv">
              <span>连接方式</span
              ><strong>{{ dbRequest.connectionMode || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>查询状态</span
              ><strong>{{ dbResponse.queryStatus || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>开始时间</span
              ><strong>{{
                dbResponse.startTime || dbRequest.startTime || '-'
              }}</strong>
            </div>
            <div class="detail-kv">
              <span>结束时间</span
              ><strong>{{ dbResponse.endTime || '-' }}</strong>
            </div>
          </div>
          <detail-block title="SQL" :content="dbRequest.sql || '-'" />
          <detail-block
            title="SQL 参数"
            :content="pretty(dbRequest.paramFields || dbRequest.params)"
          />
          <detail-block title="返回结果行" :content="pretty(dbResponse.rows)" />
          <detail-block
            title="结果提取"
            :content="
              pretty({
                resultPath: dbResponse.resultPath,
                extractedValue: dbResponse.extractedValue,
              })
            "
          />
        </template>

        <template v-else-if="moduleType === 'LIST'">
          <div class="detail-grid">
            <div class="detail-kv">
              <span>匹配值</span
              ><strong>{{ listRequest.queryValue || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>匹配模式</span
              ><strong>{{ listRequest.matchMode || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>内容类型</span
              ><strong>{{ prettyInline(listRequest.itemTypes) }}</strong>
            </div>
            <div class="detail-kv">
              <span>是否命中</span
              ><strong>{{
                listResponse.hit === true ? '命中' : '未命中'
              }}</strong>
            </div>
          </div>
          <detail-block title="名单匹配请求" :content="pretty(listRequest)" />
          <detail-block title="名单匹配结果" :content="pretty(listResponse)" />
        </template>

        <template v-else-if="moduleType === 'MODEL'">
          <detail-block title="模型输入参数" :content="pretty(modelRequest)" />
          <detail-block title="模型输出结果" :content="pretty(modelResponse)" />
        </template>

        <template v-else>
          <div class="detail-grid">
            <div class="detail-kv">
              <span>请求方法</span
              ><strong>{{ detail.requestMethod || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>响应状态</span
              ><strong>{{ detail.responseStatus || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>请求成功</span
              ><strong>{{ binaryLabel(detail.requestSuccess) }}</strong>
            </div>
            <div class="detail-kv">
              <span>是否查得</span
              ><strong>{{ binaryLabel(detail.found) }}</strong>
            </div>
            <div class="detail-kv">
              <span>供应商请求</span
              ><strong>{{ binaryLabel(detail.providerRequest) }}</strong>
            </div>
            <div class="detail-kv">
              <span>缓存状态</span
              ><strong>{{ detail.cacheStatus || '-' }}</strong>
            </div>
            <div class="detail-kv">
              <span>缓存键摘要</span
              ><strong>{{ detail.cacheKey || '-' }}</strong>
            </div>
          </div>
          <detail-block
            title="请求头"
            :content="pretty(detail.requestHeaders)"
          />
          <detail-block
            title="请求参数"
            :content="pretty(detail.requestParams)"
          />
          <detail-block title="请求体" :content="pretty(detail.requestBody)" />
          <detail-block
            title="响应内容"
            :content="pretty(detail.responseBody)"
          />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Refresh as ElIconRefresh } from '@element-plus/icons-vue'
import { plantRenderPara } from '../../utils/gogocodeTransfer'
import * as Vue from 'vue'
import { getExternalApiStats, listRuntimeLogs } from '@/api/runtimeLog'
import AsyncState from '@/components/common/AsyncState.vue'

const PROFILES = {
  DATASOURCE: {
    title: 'API外数调用日志',
    subtitle:
      '展示三方 API 鉴权、请求头、请求参数、请求体、响应状态和响应内容。',
    targetLabel: '接口编码',
    methodLabel: 'HTTP',
    resourceLabel: '请求地址',
    summaryLabel: '响应摘要',
    detailTitle: 'API外数调用详情',
    showMethod: true,
    showResource: true,
    showProject: false,
    emptyResource: '-',
  },
  DATABASE: {
    title: '数据源查询日志',
    subtitle:
      '展示数据库连接测试、只读 SQL、占位参数、返回结果行和变量提取结果。',
    targetLabel: '数据源/变量',
    methodLabel: '类型',
    resourceLabel: '数据库资源',
    summaryLabel: 'SQL摘要',
    detailTitle: '数据源查询详情',
    showMethod: true,
    showResource: false,
    showProject: false,
    emptyResource: '-',
  },
  LIST: {
    title: '名单匹配日志',
    subtitle: '展示名单变量匹配值、内容类型、匹配模式和命中结果。',
    targetLabel: '名单变量',
    methodLabel: '类型',
    resourceLabel: '名单库',
    summaryLabel: '匹配摘要',
    detailTitle: '名单匹配详情',
    showMethod: true,
    showResource: false,
    showProject: false,
    emptyResource: '-',
  },
  MODEL: {
    title: '模型执行日志',
    subtitle: '展示模型测试和执行时的输入参数、输出结果、错误信息和耗时。',
    targetLabel: '模型编码',
    methodLabel: '类型',
    resourceLabel: '模型资源',
    summaryLabel: '输出摘要',
    detailTitle: '模型执行详情',
    showMethod: false,
    showResource: false,
    showProject: true,
    emptyResource: '-',
  },
}

export default {
  data() {
    return {
      loading: false,
      statsLoading: false,
      statsError: '',
      externalStats: { overview: {}, providers: [] },
      rows: [],
      total: 0,
      query: {
        pageNum: 1,
        pageSize: 10,
        actionType: '',
        targetCode: '',
        traceId: '',
        success: '',
      },
      detailVisible: false,
      detail: null,
      actionMap: {
        API_INVOKE: 'API调用',
        AUTH_TEST: '鉴权测试',
        QUERY: '只读查询',
        TEST_CONNECTION: '连接测试',
        TEST_CONNECTION_DRAFT: '草稿连接测试',
        DB_VARIABLE_QUERY: 'DB变量查询',
        LIST_VARIABLE_MATCH: '名单变量匹配',
        EXECUTE: '执行测试',
        MODEL_EXECUTE: '规则内模型执行',
      },
      ElIconRefresh: markRaw(ElIconRefresh),
    }
  },
  name: 'ModuleCallLog',
  components: {
    AsyncState,
    DetailBlock: function render(_props, _context) {
      const ctx = {
        ..._context,
        props: _props,
        data: _context.attr,
        children: _context.slots,
      }
      return Vue.h('div', plantRenderPara({ class: 'detail-block' }), [
        Vue.h(
          'div',
          plantRenderPara({ class: 'detail-title' }),
          ctx.props.title
        ),
        Vue.h(
          'pre',
          plantRenderPara({ class: 'log-pre' }),
          ctx.props.content || '-'
        ),
      ])
    },
  },
  props: {
    moduleType: { type: String, required: true },
    title: { type: String, default: '' },
  },
  computed: {
    profile() {
      return PROFILES[this.moduleType] || PROFILES.DATASOURCE
    },
    statsOverview() {
      return this.externalStats && this.externalStats.overview
        ? this.externalStats.overview
        : {}
    },
    dbRequest() {
      return this.parseJsonValue(this.detail && this.detail.requestBody)
    },
    dbResponse() {
      return this.parseJsonValue(this.detail && this.detail.responseBody)
    },
    listRequest() {
      return this.parseJsonValue(this.detail && this.detail.requestBody)
    },
    listResponse() {
      return this.parseJsonValue(this.detail && this.detail.responseBody)
    },
    modelRequest() {
      return this.parseJsonValue(this.detail && this.detail.requestBody)
    },
    modelResponse() {
      return this.parseJsonValue(this.detail && this.detail.responseBody)
    },
    actionOptions() {
      const datasource = [
        { label: 'API调用', value: 'API_INVOKE' },
        { label: '鉴权测试', value: 'AUTH_TEST' },
      ]
      const database = [
        { label: '只读查询', value: 'QUERY' },
        { label: '连接测试', value: 'TEST_CONNECTION' },
        { label: '草稿连接测试', value: 'TEST_CONNECTION_DRAFT' },
        { label: 'DB变量查询', value: 'DB_VARIABLE_QUERY' },
      ]
      const list = [{ label: '名单变量匹配', value: 'LIST_VARIABLE_MATCH' }]
      const model = [
        { label: '执行测试', value: 'EXECUTE' },
        { label: '规则内模型执行', value: 'MODEL_EXECUTE' },
      ]
      if (this.moduleType === 'DATASOURCE') return datasource
      if (this.moduleType === 'DATABASE') return database
      if (this.moduleType === 'LIST') return list
      if (this.moduleType === 'MODEL') return model
      return datasource.concat(database, list, model)
    },
  },
  created() {
    this.load()
    if (this.moduleType === 'DATASOURCE') this.loadStats()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const params = this.cleanParams({
          ...this.query,
          moduleType: this.moduleType,
        })
        const res = await listRuntimeLogs(params)
        const data = res && res.data ? res.data : {}
        this.rows = data.records || []
        this.total = data.total || 0
      } finally {
        this.loading = false
      }
    },
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
    handleQuery() {
      this.query.pageNum = 1
      this.load()
    },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: this.query.pageSize,
        actionType: '',
        targetCode: '',
        traceId: '',
        success: '',
      }
      this.load()
    },
    openDetail(row) {
      this.detail = row
      this.detailVisible = true
    },
    actionLabel(value) {
      return this.actionMap[value] || value || '-'
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
    binaryLabel(value) {
      if (value === 1) return '是'
      if (value === 0) return '否'
      return '-'
    },
    rowSummary(row) {
      if (this.moduleType === 'DATABASE') {
        const request = this.parseJsonValue(row.requestBody)
        return (
          request.sql ||
          this.prettyInline(request.params || request.paramFields)
        )
      }
      if (this.moduleType === 'LIST') {
        const request = this.parseJsonValue(row.requestBody)
        const response = this.parseJsonValue(row.responseBody)
        return (
          '值=' +
          (request.queryValue || '-') +
          '，' +
          (response.hit ? '命中' : '未命中')
        )
      }
      if (this.moduleType === 'MODEL') {
        return this.prettyInline(this.parseJsonValue(row.responseBody))
      }
      return row.responseStatus
        ? 'HTTP ' + row.responseStatus
        : this.prettyInline(this.parseJsonValue(row.responseBody))
    },
    pretty(value) {
      if (value == null || value === '') return '-'
      if (typeof value === 'object') {
        return JSON.stringify(value, null, 2)
      }
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return String(value)
      }
    },
    prettyInline(value) {
      const text = this.pretty(value)
      return text.replace(/\s+/g, ' ').slice(0, 140)
    },
    parseJsonValue(value) {
      if (!value) return {}
      if (typeof value === 'object') return value
      try {
        return JSON.parse(value)
      } catch (e) {
        return {}
      }
    },
    formatTime(time) {
      if (!time) return '-'
      const d = new Date(time)
      if (Number.isNaN(d.getTime())) return time
      const pad = (n) => String(n).padStart(2, '0')
      return (
        d.getFullYear() +
        '-' +
        pad(d.getMonth() + 1) +
        '-' +
        pad(d.getDate()) +
        ' ' +
        pad(d.getHours()) +
        ':' +
        pad(d.getMinutes()) +
        ':' +
        pad(d.getSeconds())
      )
    },
    cleanParams(params) {
      Object.keys(params).forEach((key) => {
        if (
          params[key] === '' ||
          params[key] === null ||
          params[key] === undefined
        )
          delete params[key]
      })
      return params
    },
  },
}
</script>

<style scoped>
.module-call-log {
  margin-top: 16px;
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.log-title {
  color: #1f2937;
  font-weight: 700;
}
.log-subtitle {
  color: #64748b;
  font-size: 12px;
  margin-top: 3px;
}
.stats-panel {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
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
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}
.stat-card span {
  color: #64748b;
  font-size: 13px;
}
.stat-card strong {
  color: #303133;
  font-size: 24px;
  line-height: 1.2;
}
.provider-table {
  background: #fff;
}
.stats-empty {
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  color: #64748b;
  text-align: center;
  padding: 24px;
}
.log-filter {
  margin-bottom: 10px;
}
.log-pager {
  margin-top: 12px;
  text-align: right;
}
.log-detail {
  padding: 16px;
}
.detail-block {
  margin-top: 12px;
}
.detail-title {
  color: #334155;
  font-weight: 700;
  margin-bottom: 6px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.detail-kv {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.detail-kv span {
  color: #64748b;
}
.detail-kv strong {
  color: #1f2937;
  font-weight: 600;
  min-width: 0;
  overflow-wrap: anywhere;
}
.log-pre {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: Menlo, Monaco, Consolas, monospace;
}
</style>
