<template>
  <div class="module-call-log management-table-region">
    <div class="log-header">
      <div>
        <div class="log-title">{{ title || profile.title }}</div>
        <div class="log-subtitle">{{ profile.subtitle }}</div>
      </div>
      <table-column-settings :columns="moduleColumns" :storage-key="`tianshu:table-columns:module-log-${moduleType.toLowerCase()}`" />
      <el-button size="small" :icon="ElIconRefresh" @click="load"
        >刷新</el-button
      >
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
        <el-form-item label="项目">
          <project-filter-select
            v-model:value="query.projectCode"
            field="projectCode"
            placeholder="全部项目"
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item :label="profile.targetLabel">
          <remote-filter-select
            v-model:value="query.targetCode"
            :fetch-options="fetchTargetCodeOptions"
            option-label-key="targetCode"
            option-value-key="targetCode"
            allow-free-input
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

    <el-table class="management-table" show-overflow-tooltip
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
      <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="80" align="center" fixed="right">
        <template v-slot="{ row }">
          <el-button
            link
            size="small"
            type="primary"
            @click="openDetail(row)"
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

        <template v-if="moduleType === 'DATASOURCE'">
          <el-alert
            v-if="detailLoadError"
            class="payload-alert"
            type="warning"
            :closable="false"
            show-icon
            :title="detailLoadError"
          />
          <div class="detail-grid payload-meta">
            <div class="detail-kv">
              <span>调用 ID</span>
              <strong class="detail-kv-value">
                <span>{{ apiPayload.callId || detail.callId || '-' }}</span>
                <el-button
                  v-if="apiPayload.callId || detail.callId"
                  link
                  size="small"
                  type="primary"
                  @click="copyText(apiPayload.callId || detail.callId)"
                  >复制</el-button
                >
              </strong>
            </div>
            <div class="detail-kv">
              <span>根 Trace ID</span>
              <strong class="detail-kv-value">
                <span>{{ apiPayload.rootTraceId || detail.rootTraceId || '-' }}</span>
                <el-button
                  v-if="apiPayload.rootTraceId || detail.rootTraceId"
                  link
                  size="small"
                  type="primary"
                  @click="copyText(apiPayload.rootTraceId || detail.rootTraceId)"
                  >复制</el-button
                >
              </strong>
            </div>
            <div class="detail-kv">
              <span>分析请求副本</span>
              <strong>{{ apiPayloadLoaded ? (apiPayload.rawRequestAvailable ? '可用' : '不可用（GET 或历史记录）') : '加载中…' }}</strong>
            </div>
            <div class="detail-kv">
              <span>分析响应副本</span>
              <strong>{{ apiPayloadLoaded ? (apiPayload.rawResponseAvailable ? '可用' : '不可用（历史记录或脱敏回退）') : '加载中…' }}</strong>
            </div>
          </div>
          <external-call-trace
            :steps="apiPayload.traceSteps"
            title="外数调用链"
            subtitle="从规则入参、鉴权到外部响应和引擎赋值的可关联过程"
            empty-text="历史记录未保存外数阶段链，仅保留调用摘要和受控报文。"
          />
        </template>

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
          <detail-block
            :title="apiPayload.rawRequestAvailable ? '分析请求报文（按配置留存）' : '请求报文（未生成分析副本）'"
            :content="pretty(apiPayload.requestBody || detail.requestBody)"
          />
          <detail-block
            v-if="apiPayload.originalRequestAvailable"
            title="供应商原始请求（永久留存）"
            :content="pretty(apiPayload.originalRequestBody)"
          />
          <detail-block
            :title="apiPayload.rawResponseAvailable ? '分析响应报文（按配置留存）' : '响应内容（未生成分析副本）'"
            :content="pretty(apiPayload.responseBody || detail.responseBody)"
          />
          <detail-block
            v-if="apiPayload.originalResponseAvailable"
            title="供应商原始响应（永久留存）"
            :content="pretty(apiPayload.originalResponseBody)"
          />
          <detail-block
            v-if="apiPayload.rawRequestMetadata && Object.keys(apiPayload.rawRequestMetadata).length"
            title="请求留存处理"
            :content="pretty(apiPayload.rawRequestMetadata)"
          />
          <detail-block
            v-if="apiPayload.rawResponseMetadata && Object.keys(apiPayload.rawResponseMetadata).length"
            title="响应留存处理"
            :content="pretty(apiPayload.rawResponseMetadata)"
          />
          <detail-block v-if="detail.historyFields" title="历史统计字段结果（字段 ID）" :content="pretty(detail.historyFields)" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Refresh as ElIconRefresh } from '@element-plus/icons-vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { ElMessage } from 'element-plus'
import { plantRenderPara } from '../../utils/gogocodeTransfer'
import * as Vue from 'vue'
import { getRuntimeCallPayload, listRuntimeLogs } from '@/api/runtimeLog'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ExternalCallTrace from './ExternalCallTrace.vue'

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
      rows: [],
      total: 0,
      query: {
        pageNum: 1,
        pageSize: 10,
        actionType: '',
        projectCode: '',
        targetCode: '',
        traceId: '',
        success: '',
      },
      detailVisible: false,
      detail: null,
      apiPayload: {},
      apiPayloadLoaded: false,
      detailLoadError: '',
      detailRequestSeq: 0,
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
        API_ASSIGNMENT: '引擎变量赋值',
      },
      ElIconRefresh: markRaw(ElIconRefresh),
    }
  },
  name: 'ModuleCallLog',
  components: {
    TableColumnSettings,
    RemoteFilterSelect,
    ProjectFilterSelect,
    ExternalCallTrace,
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
    moduleColumns() {
      const columns = [
        { key: 'actionType', label: '动作' },
        { key: 'targetCode', label: this.profile.targetLabel },
        { key: 'targetName', label: '名称' },
        { key: 'traceId', label: '模块 trace' },
      ]
      if (this.profile.showMethod) columns.push({ key: 'requestMethod', label: this.profile.methodLabel })
      if (this.profile.showResource) columns.push({ key: 'requestUrl', label: this.profile.resourceLabel })
      if (this.profile.showProject) columns.push({ key: 'projectCode', label: '项目编码' })
      columns.push(
        { key: 'summary', label: this.profile.summaryLabel },
        { key: 'success', label: '结果' },
        { key: 'costTimeMs', label: '耗时(ms)' },
        { key: 'createTime', label: '时间' },
      )
      return columns
    },
    profile() {
      return PROFILES[this.moduleType] || PROFILES.DATASOURCE
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
  },
  methods: {
    fetchTargetCodeOptions({ query, pageNum, pageSize }) {
      return listRuntimeLogs(this.cleanParams({
        ...this.query,
        moduleType: this.moduleType,
        targetCode: query,
        pageNum,
        pageSize,
      }))
    },
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
    handleQuery() {
      this.query.pageNum = 1
      this.load()
    },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: this.query.pageSize,
        actionType: '',
        projectCode: '',
        targetCode: '',
        traceId: '',
        success: '',
      }
      this.load()
    },
    async openDetail(row) {
      this.detail = row
      this.apiPayload = {}
      this.apiPayloadLoaded = false
      this.detailLoadError = ''
      this.detailVisible = true
      const requestSeq = ++this.detailRequestSeq
      if (this.moduleType !== 'DATASOURCE' || !row || !row.id) {
        this.apiPayloadLoaded = true
        return
      }
      try {
        const res = row.projectId == null
          ? await getRuntimeCallPayload(row.id)
          : await getRuntimeCallPayload(row.id, row.projectId)
        if (requestSeq !== this.detailRequestSeq) return
        this.apiPayload = (res && res.data) || {}
      } catch (error) {
        if (requestSeq !== this.detailRequestSeq) return
        this.detailLoadError = '受控报文加载失败，可稍后重试。'
      } finally {
        if (requestSeq === this.detailRequestSeq) {
          this.apiPayloadLoaded = true
        }
      }
    },
    async copyText(value) {
      if (!value) return
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(String(value))
        } else {
          const input = document.createElement('textarea')
          input.value = String(value)
          input.setAttribute('readonly', '')
          input.style.position = 'fixed'
          input.style.opacity = '0'
          document.body.appendChild(input)
          input.select()
          document.execCommand('copy')
          document.body.removeChild(input)
        }
        ElMessage.success('已复制关联 ID')
      } catch (error) {
        ElMessage.warning('复制失败，请手动选择文本')
      }
    },
    actionLabel(value) {
      return this.actionMap[value] || value || '-'
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
    traceStepKey(step) {
      return `${step && step.sequence ? step.sequence : ''}-${step && step.type ? step.type : 'step'}`
    },
    traceStepStatusLabel(status) {
      return { SUCCESS: '成功', FAILED: '失败', READY: '已准备', SENT: '已发送', SKIPPED: '已跳过' }[status] || status || '处理中'
    },
    traceStepTagType(status) {
      if (status === 'FAILED') return 'danger'
      if (status === 'SKIPPED') return 'info'
      return 'success'
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
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  margin-top: 3px;
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
.payload-alert {
  margin-top: 12px;
}
.payload-meta {
  margin-top: 12px;
}
.detail-kv {
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-soft);
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.detail-kv span {
  color: var(--tianshu-text-tertiary);
}
.detail-kv strong {
  color: #1f2937;
  font-weight: 600;
  min-width: 0;
  overflow-wrap: anywhere;
}
.detail-kv-value {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  text-align: right;
}
.log-pre {
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: Menlo, Monaco, Consolas, monospace;
}
.external-trace-chain {
  display: grid;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-muted);
}
.external-trace-step {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.external-trace-step-index {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
}
.external-trace-step-main {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 5px;
  background: var(--tianshu-bg-surface);
}
.external-trace-step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--tianshu-text-primary);
  font-size: 12px;
}
.external-trace-step-values {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.external-trace-step-values .detail-block {
  min-width: 0;
  margin-top: 8px;
}
@media (max-width: 760px) {
  .external-trace-step-values { grid-template-columns: 1fr; }
}
</style>
