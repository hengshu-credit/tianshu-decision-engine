<template>
  <div class="uiue-list-page">
    <el-tabs
      v-model="activeView"
      class="log-view-tabs"
      @tab-click="handleViewChange"
    >
      <el-tab-pane label="执行明细" name="logs" />
      <el-tab-pane label="规则集命中统计" name="ruleSetStats" />
    </el-tabs>
    <div v-if="activeView === 'logs'">
      <div class="uiue-search-container">
        <el-form :inline="true" size="small" @keyup.enter="handleQuery">
          <el-form-item label="来源">
            <el-select
              v-model="qp.source"
              clearable
              placeholder="全部"
              @change="onSourceChange"
            >
              <el-option label="服务端" value="SERVER" />
              <el-option label="客户端" value="CLIENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目编码">
            <project-filter-select
              v-model:value="qp.projectCode"
              field="projectCode"
              placeholder="输入项目编码"
              style="width: 150px"
              @change="onProjectChange"
            />
          </el-form-item>
          <el-form-item label="项目名称">
            <project-filter-select
              v-model:value="qp.projectName"
              field="projectName"
              placeholder="输入项目名称"
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="规则">
            <el-select
              v-model="qp.ruleCode"
              clearable
              filterable
              remote
              reserve-keyword
              placeholder="全部规则"
              :remote-method="searchRules"
              :loading="ruleOptionsLoading"
              @visible-change="onRuleFilterVisible"
            >
              <el-option
                v-for="r in filteredRules"
                :key="r.ruleCode"
                :label="r.ruleName"
                :value="r.ruleCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型类型">
            <el-select v-model="qp.modelType" clearable placeholder="全部类型">
              <el-option label="决策表" value="TABLE" />
              <el-option label="决策树" value="TREE" />
              <el-option label="决策流" value="FLOW" />
              <el-option label="规则集" value="RULE_SET" />
              <el-option label="交叉表" value="CROSS" />
              <el-option label="评分卡" value="SCORE" />
              <el-option label="复杂交叉表" value="CROSS_ADV" />
              <el-option label="复杂评分卡" value="SCORE_ADV" />
              <el-option label="脚本" value="SCRIPT" />
            </el-select>
          </el-form-item>
          <el-form-item label="鉴权方式">
            <el-select
              v-model="qp.authType"
              clearable
              placeholder="全部方式"
              style="width: 130px"
            >
              <el-option label="兼容令牌" value="LEGACY_TOKEN" />
              <el-option label="账号密码" value="BASIC" />
              <el-option label="API Key" value="API_KEY" />
              <el-option label="HMAC-SHA256" value="HMAC_SHA256" />
            </el-select>
          </el-form-item>
          <el-form-item label="鉴权编码"
            ><el-input
              v-model="qp.authCode"
              clearable
              placeholder="如 BASIC_MAIN"
              style="width: 150px"
          /></el-form-item>
          <el-form-item label="Token 编码"
            ><el-input
              v-model="qp.tokenCode"
              clearable
              placeholder="如 TOKEN_..."
              style="width: 160px"
          /></el-form-item>
          <el-form-item label="Trace ID"
            ><el-input
              v-model="qp.traceId"
              clearable
              placeholder="完整 trace_id"
              style="width: 250px"
          /></el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              :default-time="
                ['00:00:00', '23:59:59'].map((d) =>
                  dayjs(d, 'hh:mm:ss').toDate()
                )
              "
              :shortcuts="pickerOptions && pickerOptions.shortcuts"
              :disabled-date="pickerOptions && pickerOptions.disabledDate"
              :cell-class-name="pickerOptions && pickerOptions.cellClassName"
              v-model="timeRange"
              type="datetimerange"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              size="small"
              style="width: 360px"
            ></el-date-picker>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="handleQuery"
              >查询</el-button
            >
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table
        :data="list"
        border
        size="small"
        v-loading="loading"
        style="width: 100%"
      >
        <el-table-column
          prop="projectCode"
          label="项目"
          min-width="120"
          show-overflow-tooltip
        >
          <template v-slot="{ row }">{{
            projectMap[row.projectCode] || row.projectCode || '-'
          }}</template>
        </el-table-column>
        <el-table-column
          prop="ruleCode"
          label="规则编码"
          min-width="140"
          show-overflow-tooltip
        >
          <template v-slot="{ row }">{{
            ruleMap[row.ruleCode] || row.ruleCode
          }}</template>
        </el-table-column>
        <el-table-column
          prop="traceId"
          label="会话 trace"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column
          prop="modelType"
          label="模型类型"
          min-width="80"
          align="center"
        >
          <template v-slot="{ row }">{{
            modelTypeMap[row.modelType] || row.modelType
          }}</template>
        </el-table-column>
        <el-table-column
          prop="source"
          label="来源"
          min-width="70"
          align="center"
        >
          <template v-slot="{ row }"
            ><el-tag
              :type="sourceTagType(row.source)"
              size="small"
              >{{ row.source === 'SERVER' ? '服务端' : '客户端' }}</el-tag
            ></template
          >
        </el-table-column>
        <el-table-column
          prop="authCode"
          label="鉴权编码"
          min-width="130"
          show-overflow-tooltip
        >
          <template v-slot="{ row }">
            <div>{{ row.authCode || '-' }}</div>
            <div class="auth-type-text">{{ authTypeLabel(row.authType) }}</div>
          </template>
        </el-table-column>
        <el-table-column
          prop="tokenCode"
          label="Token 编码"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          prop="success"
          label="结果"
          min-width="65"
          align="center"
        >
          <template v-slot="{ row }"
            ><el-tag
              :type="row.success === 1 ? 'success' : 'danger'"
              size="small"
              >{{ row.success === 1 ? '成功' : '失败' }}</el-tag
            ></template
          >
        </el-table-column>
        <el-table-column
          prop="executeTimeMs"
          label="耗时(ms)"
          min-width="80"
          align="center"
        />
        <el-table-column
          prop="clientAppName"
          label="客户端"
          min-width="110"
          show-overflow-tooltip
        />
        <el-table-column prop="createTime" label="执行时间" width="150" fixed="right">
          <template v-slot="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="追踪" width="70" align="center" fixed="right">
          <template v-slot="{ row }">
            <el-tag
              v-if="row.traceInfo"
              :type="row.success === 1 ? 'success' : 'danger'"
              size="small"
              ><el-icon><el-icon-view /></el-icon> 有</el-tag
            >
            <span v-else style="color: #64748b">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template v-slot="{ row }"
            ><el-button link size="small" @click="handleViewDetail(row)"
              >详情</el-button
            ></template
          >
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; text-align: right"
        :current-page="qp.pageNum"
        :page-size="qp.pageSize"
        :total="total"
        layout="total,sizes,prev,pager,next"
        :page-sizes="[10, 30, 50, 100, 200, 500]"
        @current-change="
          (p) => {
            qp.pageNum = p
            load()
          }
        "
        @size-change="
          (s) => {
            qp.pageSize = s
            qp.pageNum = 1
            load()
          }
        "
      />
    </div>
    <div v-else class="rule-set-stats">
      <div class="uiue-search-container">
        <el-form :inline="true" size="small">
          <el-form-item label="项目编码">
            <project-filter-select
              v-model:value="qp.projectCode"
              field="projectCode"
              placeholder="输入项目编码"
              style="width: 150px"
              @change="onProjectChange"
            />
          </el-form-item>
          <el-form-item label="项目名称">
            <project-filter-select
              v-model:value="qp.projectName"
              field="projectName"
              placeholder="输入项目名称"
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item label="规则集">
            <el-select
              v-model="qp.ruleCode"
              clearable
              filterable
              remote
              reserve-keyword
              placeholder="全部规则集"
              :remote-method="searchRules"
              :loading="ruleOptionsLoading"
              @visible-change="onRuleFilterVisible"
            >
              <el-option
                v-for="r in filteredRuleSets"
                :key="r.ruleCode"
                :label="r.ruleName"
                :value="r.ruleCode"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              :default-time="
                ['00:00:00', '23:59:59'].map((d) =>
                  dayjs(d, 'hh:mm:ss').toDate()
                )
              "
              :shortcuts="pickerOptions && pickerOptions.shortcuts"
              :disabled-date="pickerOptions && pickerOptions.disabledDate"
              :cell-class-name="pickerOptions && pickerOptions.cellClassName"
              v-model="timeRange"
              type="datetimerange"
              range-separator="-"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              size="small"
              style="width: 360px"
            ></el-date-picker>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadRuleSetStats">查询</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-alert
        title="统计范围仅包含规则集；串行规则集中未执行、禁用及首条命中后跳过的规则不计入执行次数。"
        type="info"
        show-icon
        :closable="false"
      />
      <async-state
        :loading="ruleSetStatsLoading"
        :error="ruleSetStatsError"
        :empty="!ruleSetStats.ruleSets.length"
        empty-text="当前筛选范围内暂无规则集追踪统计"
        @retry="loadRuleSetStats"
      >
        <div class="stats-cards">
          <div class="stats-card">
            <span>规则集执行次数</span
            ><strong>{{ ruleSetStats.overview.evaluationCount || 0 }}</strong>
          </div>
          <div class="stats-card">
            <span>整体命中率</span
            ><strong>{{ ratePercent(ruleSetStats.overview.hitRate) }}</strong>
          </div>
          <div class="stats-card">
            <span>失败率</span
            ><strong>{{
              ratePercent(ruleSetStats.overview.failureRate)
            }}</strong>
          </div>
          <div class="stats-card">
            <span>平均耗时</span
            ><strong>{{
              formatCost(ruleSetStats.overview.avgCostTimeMs)
            }}</strong>
          </div>
          <div class="stats-card">
            <span>P95 耗时</span
            ><strong>{{
              formatCost(ruleSetStats.overview.p95CostTimeMs)
            }}</strong>
          </div>
          <div class="stats-card">
            <span>P99 耗时</span
            ><strong>{{
              formatCost(ruleSetStats.overview.p99CostTimeMs)
            }}</strong>
          </div>
        </div>

        <el-table
          :data="ruleSetStats.ruleSets"
          border
          size="small"
          class="rule-set-table"
        >
          <el-table-column type="expand">
            <template v-slot="{ row }">
              <div class="item-table-title">规则集内部规则命中明细</div>
              <el-table
                v-if="row.items && row.items.length"
                :data="row.items"
                border
                size="small"
              >
                <el-table-column
                  prop="ruleCode"
                  label="规则编码"
                  min-width="150"
                />
                <el-table-column
                  prop="ruleName"
                  label="规则名称"
                  min-width="180"
                />
                <el-table-column
                  prop="evaluationCount"
                  label="实际执行次数"
                  min-width="110"
                  align="right"
                />
                <el-table-column
                  prop="hitCount"
                  label="命中次数"
                  min-width="90"
                  align="right"
                />
                <el-table-column label="命中率" min-width="90" align="right">
                  <template v-slot="itemScope">{{
                    ratePercent(itemScope.row.hitRate)
                  }}</template>
                </el-table-column>
              </el-table>
              <el-empty
                v-else
                description="暂无实际执行的内部规则"
                :image-size="56"
              />
            </template>
          </el-table-column>
          <el-table-column prop="ruleCode" label="规则集编码" min-width="150" />
          <el-table-column prop="ruleName" label="规则集名称" min-width="180" />
          <el-table-column
            prop="evaluationCount"
            label="执行次数"
            min-width="90"
            align="right"
          />
          <el-table-column
            prop="hitCount"
            label="命中次数"
            min-width="90"
            align="right"
          />
          <el-table-column label="命中率" min-width="90" align="right">
            <template v-slot="{ row }">{{ ratePercent(row.hitRate) }}</template>
          </el-table-column>
          <el-table-column label="失败率" min-width="90" align="right">
            <template v-slot="{ row }">{{
              ratePercent(row.failureRate)
            }}</template>
          </el-table-column>
          <el-table-column
            label="平均/P95/P99 耗时"
            min-width="190"
            align="right"
          >
            <template v-slot="{ row }"
              >{{ formatCost(row.avgCostTimeMs) }} /
              {{ formatCost(row.p95CostTimeMs) }} /
              {{ formatCost(row.p99CostTimeMs) }}</template
            >
          </el-table-column>
        </el-table>
      </async-state>
    </div>
    <el-drawer title="日志详情" v-model="detailVis" size="84%">
      <div style="padding: 16px" v-if="detail">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基本信息" name="basic">
            <div class="uiue-card trace-id-card">
              <div class="uiue-card-title">共享执行会话</div>
              <code>{{ detail.traceId || '-' }}</code>
            </div>
            <div class="uiue-card auth-attribution-card">
              <div class="uiue-card-title">鉴权归因</div>
              <div class="auth-attribution-grid">
                <div>
                  <span>鉴权编码</span><code>{{ detail.authCode || '-' }}</code>
                </div>
                <div>
                  <span>鉴权方式</span
                  ><strong>{{ authTypeLabel(detail.authType) }}</strong>
                </div>
                <div>
                  <span>Token 编码</span
                  ><code>{{ detail.tokenCode || '-' }}</code>
                </div>
                <div>
                  <span>鉴权阶段</span
                  ><strong>{{ authPhaseLabel(detail.authPhase) }}</strong>
                </div>
              </div>
            </div>
            <div class="uiue-card">
              <div class="uiue-card-title">输入参数</div>
              <pre class="log-pre">{{ fj(detail.inputParams) }}</pre>
            </div>
            <div class="uiue-card" style="margin-top: 12px">
              <div class="uiue-card-title">输出结果</div>
              <pre class="log-pre">{{ fj(detail.outputResult) }}</pre>
            </div>
            <div
              class="uiue-card"
              style="margin-top: 12px"
              v-if="detail.errorMessage"
            >
              <div class="uiue-card-title" style="color: #f76e6c">错误信息</div>
              <pre class="log-pre error">{{ detail.errorMessage }}</pre>
            </div>
          </el-tab-pane>
          <el-tab-pane name="trace" :disabled="!detail.traceInfo">
            <template v-slot:label>
              <span>
                <el-icon><el-icon-connection /></el-icon> 表达式追踪树
                <el-badge v-if="detail.traceInfo" is-dot class="trace-badge" />
              </span>
            </template>
            <trace-tree
              :trace-info="detail.traceInfo"
              :var-map="varMap"
              :function-name-map="functionNameMap"
              :model-type="detail.modelType"
              :input-params="detail.inputParams"
              :output-result="detail.outputResult"
              :rule-name="ruleMap[detail.ruleCode] || detail.ruleCode"
              :rule-version="detail.ruleVersion"
              :execute-time-ms="detail.executeTimeMs"
              :model-data="modelData"
              :definition-model="definitionModel"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { View as ElIconView, Connection as ElIconConnection } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import request from '@/api/request'
import { listVariables } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { listDefinitions as listRules, getContent } from '@/api/definition'
import { listProjects } from '@/api/project'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject } from '@/api/model'
import { getRuleSetStats } from '@/api/runtimeLog'
import TraceTree from '@/components/common/TraceTree.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import {
  clearPageState,
  restorePageState,
  savePageState,
} from '@/utils/pageStateCache'

export default {
  components: {
    TraceTree,
AsyncState,
ProjectFilterSelect,
    ElIconView,ElIconConnection,
  },
  data() {

return {
  activeView: 'logs',
loading: false,
list: [],
total: 0,
qp: {
pageNum: 1,
pageSize: 10,
ruleCode: '',
projectCode: '',
projectName: '',
source: '',
modelType: '',
authType: '',
authCode: '',
tokenCode: '',
traceId: '',
},
/** 时间范围，默认最近三个月 */
timeRange: null,
/** 日期快捷选项 */
pickerOptions: {
shortcuts: [
{
text: '最近一周',
value: function() {
var end = new Date()
var start = new Date()
start.setTime(start.getTime() - 7 * 24 * 3600 * 1000)
return [start, end];
},
},
{
text: '最近一个月',
value: function() {
var end = new Date()
var start = new Date()
start.setMonth(start.getMonth() - 1)
return [start, end];
},
},
{
text: '最近三个月',
value: function() {
var end = new Date()
var start = new Date()
start.setMonth(start.getMonth() - 3)
return [start, end];
},
},
{
text: '最近半年',
value: function() {
var end = new Date()
var start = new Date()
start.setMonth(start.getMonth() - 6)
return [start, end];
},
},
{
text: '最近一年',
value: function() {
var end = new Date()
var start = new Date()
start.setFullYear(start.getFullYear() - 1)
return [start, end];
},
},
],
},
detailVis: false,
detail: null,
detailLoading: false,
detailTab: 'basic',
varMap: {},
ruleMap: {},
projectMap: {},
projectList: [],
ruleList: [],
projectOptionsLoading: false,
ruleOptionsLoading: false,
projectOptionsLoaded: false,
ruleOptionsLoaded: false,
ruleSetStatsLoading: false,
ruleSetStatsError: '',
ruleSetStats: {
overview: {},
ruleSets: [],
},
modelTypeMap: {
TABLE: '决策表',
TREE: '决策树',
FLOW: '决策流',
RULE_SET: '规则集',
CROSS: '交叉表',
SCORE: '评分卡',
CROSS_ADV: '复杂交叉表',
SCORE_ADV: '复杂评分卡',
SCRIPT: '脚本',
},
/** 当前查看日志对应的规则模型数据（nodes + edges，决策树/决策流） */
modelData: null,
/** 规则设计器完整 modelJson 解析结果（交叉表/复杂交叉表矩阵展示依赖） */
definitionModel: null,
/** 当前日志项目下函数编码→中文名，供追踪树展示调用函数 */
functionNameMap: {},
  dayjs,
};
},
name: 'ExecutionLog',
computed: {
filteredRules: function () {
if (!this.qp.projectCode) return this.ruleList
var pc = this.qp.projectCode
var pid = null
for (var i = 0; i < this.projectList.length; i++) {
  if (this.projectList[i].projectCode === pc) {
    pid = this.projectList[i].id
    break
  }
}
if (!pid) return this.ruleList
return this.ruleList.filter(function (r) {
  return r.projectId === pid
})
},
filteredRuleSets: function () {
return this.filteredRules.filter(function (rule) {
  return rule.modelType === 'RULE_SET'
})
},
},
watch: {
detailVis: function (val) {
if (val) this.detailTab = 'basic'
},
},
created: function () {
this.initDefaultTimeRange()
this.restoreCachedState()
this.load()
},
methods: {
async handleViewChange(tab) {
var paneName = tab && (tab.paneName ?? tab.name ?? (tab.props && tab.props.name))
this.activeView = paneName && paneName.value !== undefined ? paneName.value : (paneName || this.activeView)
if (this.activeView === 'ruleSetStats') await this.loadRuleSetStats()
},
async loadRuleSetStats() {
this.ruleSetStatsLoading = true
this.ruleSetStatsError = ''
try {
  var params = {
    projectCode: this.qp.projectCode || '',
    projectName: this.qp.projectName || '',
    ruleCode: this.qp.ruleCode || '',
    startTime:
      this.timeRange && this.timeRange[0] ? this.timeRange[0] : '',
    endTime: this.timeRange && this.timeRange[1] ? this.timeRange[1] : '',
  }
  var result = await getRuleSetStats(params)
  var data = result && result.data ? result.data : {}
  this.ruleSetStats = {
    overview: data.overview || {},
    ruleSets: data.ruleSets || [],
  }
} catch (e) {
  this.ruleSetStatsError = '规则集命中统计加载失败'
} finally {
  this.ruleSetStatsLoading = false
}
},
ratePercent: function (value) {
var rate = Number(value)
return (isFinite(rate) ? rate * 100 : 0).toFixed(2) + '%'
},
formatCost: function (value) {
var cost = Number(value)
return (isFinite(cost) ? cost.toFixed(2) : '0.00') + ' ms'
},
restoreCachedState: function () {
var state = restorePageState('ExecutionLog')
if (state.qp) this.qp = Object.assign({}, this.qp, state.qp)
if (state.timeRange) this.timeRange = state.timeRange
this.normalizePagination()
},
normalizePagination: function () {
var allowedPageSizes = [10, 30, 50, 100, 200, 500]
var pageNum = Number(this.qp.pageNum)
var pageSize = Number(this.qp.pageSize)
this.qp.pageNum = Number.isInteger(pageNum) && pageNum > 0 ? pageNum : 1
this.qp.pageSize = allowedPageSizes.indexOf(pageSize) >= 0 ? pageSize : 10
},
saveCachedState: function () {
savePageState('ExecutionLog', {
  qp: this.qp,
  timeRange: this.timeRange,
})
},
async load() {
this.loading = true
try {
  this.normalizePagination()
  this.saveCachedState()
  var params = Object.assign({}, this.qp)
  if (this.timeRange && this.timeRange.length === 2) {
    params.startTime = this.timeRange[0]
    params.endTime = this.timeRange[1]
  }
  var r = await request({
    url: '/rule/log/list',
    method: 'get',
    params: params,
  })
  this.list = r.data.records
  this.total = r.data.total
} finally {
  this.loading = false
}
},
async loadProjects(keyword) {
this.projectOptionsLoading = true
try {
  var r = await listProjects({
    pageNum: 1,
    pageSize: 50,
    keyword: keyword || '',
  })
  var list = r.data && r.data.records ? r.data.records : []
  this.mergeProjects(list)
  this.projectOptionsLoaded = true
} catch (e) {
  console.warn('加载项目列表失败:', e)
} finally {
  this.projectOptionsLoading = false
}
},
async loadRules(keyword) {
this.ruleOptionsLoading = true
try {
  var r = await listRules({
    pageNum: 1,
    pageSize: 50,
    keyword: keyword || '',
    projectCode: this.qp.projectCode || '',
  })
  var list = r.data && r.data.records ? r.data.records : []
  this.mergeRules(list)
  this.ruleOptionsLoaded = true
} catch (e) {
  console.warn('加载规则列表失败:', e)
} finally {
  this.ruleOptionsLoading = false
}
},
mergeProjects: function (list) {
var byCode = {}
this.projectList.concat(list || []).forEach(function (project) {
  if (project && project.projectCode)
    byCode[project.projectCode] = project
})
this.projectList = Object.keys(byCode).map(function (code) {
  return byCode[code]
})
var map = Object.assign({}, this.projectMap)
this.projectList.forEach(function (project) {
  if (project.projectName) map[project.projectCode] = project.projectName
})
this.projectMap = map
},
mergeRules: function (list) {
var byCode = {}
this.ruleList.concat(list || []).forEach(function (rule) {
  if (rule && rule.ruleCode) byCode[rule.ruleCode] = rule
})
this.ruleList = Object.keys(byCode).map(function (code) {
  return byCode[code]
})
var map = Object.assign({}, this.ruleMap)
this.ruleList.forEach(function (rule) {
  if (rule.ruleName) map[rule.ruleCode] = rule.ruleName
})
this.ruleMap = map
},
onProjectFilterVisible: function (visible) {
if (visible && !this.projectOptionsLoaded) return this.loadProjects('')
return Promise.resolve()
},
onRuleFilterVisible: function (visible) {
if (visible && !this.ruleOptionsLoaded) return this.loadRules('')
return Promise.resolve()
},
searchProjects: function (keyword) {
return this.loadProjects(keyword || '')
},
searchRules: function (keyword) {
return this.loadRules(keyword || '')
},
async ensureDetailMetadata(row) {
var projectCode = row && row.projectCode
var ruleCode = row && row.ruleCode
var hasProject = this.projectList.some(function (project) {
  return project.projectCode === projectCode
})
if (projectCode && !hasProject) {
  var projectResult = await listProjects({
    pageNum: 1,
    pageSize: 1,
    projectCode: projectCode,
  })
  this.mergeProjects(
    projectResult.data && projectResult.data.records
      ? projectResult.data.records
      : []
  )
}
var hasRule = this.ruleList.some(function (rule) {
  return rule.ruleCode === ruleCode
})
if (ruleCode && !hasRule) {
  var ruleResult = await listRules({
    pageNum: 1,
    pageSize: 10,
    projectCode: projectCode || '',
    ruleCode: ruleCode,
  })
  this.mergeRules(
    ruleResult.data && ruleResult.data.records
      ? ruleResult.data.records
      : []
  )
}
},
async loadVarMap() {
try {
  var r = await listVariables({ pageNum: 1, pageSize: 1000 })
  var vars = r.data && r.data.records ? r.data.records : []
  var map = {}
  for (var i = 0; i < vars.length; i++) {
    var code = vars[i].scriptName || vars[i].varCode
    if (code && vars[i].varLabel) {
      map[code] = vars[i].varLabel
    }
  }
  var pid = this.currentDetailProjectId()
  await this.appendDataObjectVarMap(map, pid)
  await this.appendModelVarMap(map, pid)
  this.varMap = map
} catch (e) {
  console.warn('加载变量映射失败:', e)
}
},
currentDetailProjectId() {
if (!this.detail || !this.detail.projectCode) return 0
for (var i = 0; i < this.projectList.length; i++) {
  if (this.projectList[i].projectCode === this.detail.projectCode) {
    return this.projectList[i].id || 0
  }
}
return 0
},
async appendDataObjectVarMap(map, projectId) {
try {
  var r = await getVariableTree(projectId || 0)
  var data = r && r.data ? r.data : r
  var tree = Array.isArray(data)
    ? data
    : data && data.tree
    ? data.tree
    : []
  var visit = function (rows, objScriptName) {
    var rowsToVisit = rows || []
    rowsToVisit.forEach(function (row) {
      var scriptName = row.scriptName || row.varCode || ''
      var code = scriptName
      if (objScriptName && code.indexOf(objScriptName + '.') !== 0) {
        code = objScriptName + '.' + scriptName
      }
      if (code) map[code] = row.varLabel || row.varCode || code
      if (row.children && row.children.length)
        visit(row.children, objScriptName)
    })
  }
  var treeToVisit = tree || []
  treeToVisit.forEach(function (node) {
    var obj = node.object || node
    var objScriptName = obj.scriptName || obj.objectCode || ''
    var rows = node.flatVariables || node.variables || []
    visit(rows, objScriptName)
  })
} catch (e) {
  /* ignore */
}
},
async appendModelVarMap(map, projectId) {
try {
  var r = await listAllModelsByProject(projectId || 0)
  var data = r && r.data ? r.data : r
  var list = Array.isArray(data)
    ? data
    : data && data.records
    ? data.records
    : []
  for (var i = 0; i < list.length; i++) {
    if (list[i].modelCode)
      map[list[i].modelCode] = list[i].modelName || list[i].modelCode
  }
} catch (e) {
  /* ignore */
}
},
/**
* 按当前日志所属项目加载启用函数列表，构建编码→中文名映射
*/
async loadFunctionNameMap() {
this.functionNameMap = {}
if (!this.detail || !this.detail.projectCode) return
var pid = null
for (var i = 0; i < this.projectList.length; i++) {
  if (this.projectList[i].projectCode === this.detail.projectCode) {
    pid = this.projectList[i].id
    break
  }
}
if (!pid) return
try {
  var r = await listAllFunctionsByProject(pid)
  var funcData = r && r.data ? r.data : r
  var list = Array.isArray(funcData)
    ? funcData
    : funcData && Array.isArray(funcData.records)
    ? funcData.records
    : []
  var map = {}
  for (var j = 0; j < list.length; j++) {
    var f = list[j]
    if (f && f.funcCode && f.funcName) map[f.funcCode] = f.funcName
  }
  this.functionNameMap = map
} catch (e) {
  console.warn('加载函数中文名映射失败:', e)
}
},
/**
* 根据 ruleCode 加载规则 modelJson：决策树/流提取 nodes+edges；交叉表等保留完整模型供追踪矩阵高亮。
*/
async loadModelJson() {
this.modelData = null
this.definitionModel = null
if (!this.detail || !this.detail.ruleCode) return
var def = null
for (var i = 0; i < this.ruleList.length; i++) {
  if (this.ruleList[i].ruleCode === this.detail.ruleCode) {
    def = this.ruleList[i]
    break
  }
}
if (!def || !def.id) return
try {
  var r = await getContent(def.id)
  var content = r && r.data ? r.data : r
  if (content && content.modelJson) {
    var model = JSON.parse(content.modelJson)
    this.definitionModel = model
    if (model.nodes && model.edges) {
      this.modelData = { nodes: model.nodes, edges: model.edges }
    }
  }
} catch (e) {
  console.warn('加载规则模型失败:', e)
}
},
async handleViewDetail(row) {
this.detailLoading = true
this.detail = Object.assign({}, row)
this.detailVis = true
try {
  await this.ensureDetailMetadata(row)
  await this.loadVarMap()
  await this.loadFunctionNameMap()
  await this.loadModelJson()
} catch (e) {
  this.$message.error('加载日志详情失败')
} finally {
  this.detailLoading = false
}
},
/** formatParams: formatParams 的别名，内部实现委托给 fj */
formatParams: function (s) {
return this.fj(s)
},
sourceTagType: function (source) {
return source === 'SERVER' ? undefined : 'success'
},
onSourceChange: function () {
this.qp.projectCode = ''
this.qp.projectName = ''
this.qp.ruleCode = ''
},
onProjectChange: function () {
this.qp.ruleCode = ''
this.ruleList = []
this.ruleOptionsLoaded = false
},
/** handleQuery: 保留方法（别名），内部调用 load() */
handleQuery: function () {
this.qp.pageNum = 1
this.load()
},
resetQuery: function () {
this.qp.source = ''
this.qp.projectCode = ''
this.qp.projectName = ''
this.qp.ruleCode = ''
this.qp.modelType = ''
this.qp.authType = ''
this.qp.authCode = ''
this.qp.tokenCode = ''
this.qp.traceId = ''
this.qp.pageNum = 1
this.initDefaultTimeRange()
clearPageState('ExecutionLog')
this.load()
},
/** 初始化默认时间范围为最近三个月 */
initDefaultTimeRange: function () {
var end = new Date()
var start = new Date()
start.setMonth(start.getMonth() - 3)
start.setHours(0, 0, 0, 0)
end.setHours(23, 59, 59, 0)
var pad = function (n) {
  return String(n).padStart(2, '0')
}
var fmt = function (d) {
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
}
this.timeRange = [fmt(start), fmt(end)]
},
fj: function (s) {
if (s == null || s === '') return '-'
try {
  return JSON.stringify(JSON.parse(s), null, 2)
} catch (e) {
  return s
}
},
formatTime: function (time) {
if (!time) return '-'
var d = new Date(time)
if (isNaN(d.getTime())) return time
var pad = function (n) {
  return String(n).padStart(2, '0')
}
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
authTypeLabel: function (type) {
return (
  {
    LEGACY_TOKEN: '兼容令牌',
    BASIC: '账号密码',
    API_KEY: 'API Key',
    HMAC_SHA256: 'HMAC-SHA256',
  }[type] ||
  type ||
  '-'
)
},
authPhaseLabel: function (phase) {
return (
  { DIRECT: '直接鉴权', VALID: 'Token 有效期', GRACE: 'Token 冗余期' }[
    phase
  ] ||
  phase ||
  '-'
)
},
},
emits: ['pick']
}
</script>

<style scoped>
.log-view-tabs {
  margin-bottom: 12px;
}
.rule-set-stats {
  min-height: 260px;
}
.stats-cards {
  display: grid;
  grid-template-columns: repeat(6, minmax(130px, 1fr));
  gap: 12px;
  margin: 16px 0;
}
.stats-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}
.stats-card span {
  display: block;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}
.stats-card strong {
  color: #303133;
  font-size: 22px;
  line-height: 1;
}
.rule-set-table {
  width: 100%;
}
.item-table-title {
  margin: 0 0 10px 8px;
  color: #606266;
  font-weight: 600;
}
@media (max-width: 1280px) {
  .stats-cards {
    grid-template-columns: repeat(3, minmax(130px, 1fr));
  }
}
.log-pre {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow: auto;
  max-height: 200px;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.log-pre.error {
  background: #fff2f2;
  color: #f76e6c;
}
.trace-badge {
  margin-left: 4px;
}
.auth-type-text {
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}
.trace-id-card {
  margin-bottom: 12px;
}
.trace-id-card code {
  display: block;
  margin-top: 10px;
  color: #303133;
  font-family: Consolas, Monaco, monospace;
  word-break: break-all;
}
.auth-attribution-card {
  margin-bottom: 12px;
}
.auth-attribution-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin-top: 12px;
}
.auth-attribution-grid > div {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  align-items: baseline;
  gap: 12px;
}
.auth-attribution-grid span {
  color: #64748b;
  font-size: 12px;
}
.auth-attribution-grid code {
  word-break: break-all;
}
:deep(.trace-badge .el-badge__content) {
  background-color: var(--el-color-primary);
}
</style>
