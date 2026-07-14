<template>
  <div class="module-call-log">
    <div class="log-header">
      <div>
        <div class="log-title">{{ title || profile.title }}</div>
        <div class="log-subtitle">{{ profile.subtitle }}</div>
      </div>
      <el-button size="small" icon="el-icon-refresh" @click="load">刷新</el-button>
    </div>

    <div class="log-filter">
      <el-form :inline="true" size="small" @keyup.enter.native="handleQuery">
        <el-form-item label="动作">
          <el-select v-model="query.actionType" clearable placeholder="全部" style="width:160px">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="profile.targetLabel">
          <el-input v-model="query.targetCode" clearable placeholder="前缀筛选" style="width:150px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" clearable placeholder="全部" style="width:100px">
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

    <el-table :data="rows" border size="small" v-loading="loading" style="width:100%;">
      <el-table-column prop="actionType" label="动作" width="140">
        <template slot-scope="{ row }">{{ actionLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column prop="targetCode" :label="profile.targetLabel" min-width="150" show-overflow-tooltip />
      <el-table-column prop="targetName" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column v-if="profile.showMethod" prop="requestMethod" :label="profile.methodLabel" width="90" align="center" />
      <el-table-column v-if="profile.showResource" prop="requestUrl" :label="profile.resourceLabel" min-width="220" show-overflow-tooltip>
        <template slot-scope="{ row }">{{ row.requestUrl || profile.emptyResource }}</template>
      </el-table-column>
      <el-table-column v-if="profile.showProject" prop="projectCode" label="项目编码" min-width="120" show-overflow-tooltip />
      <el-table-column :label="profile.summaryLabel" min-width="180" show-overflow-tooltip>
        <template slot-scope="{ row }">{{ rowSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="结果" width="70" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="mini">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTimeMs" label="耗时(ms)" width="90" align="center" />
      <el-table-column prop="createTime" label="时间" width="160">
        <template slot-scope="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="log-pager"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10,30,50,100]"
      @current-change="p => { query.pageNum = p; load() }"
      @size-change="s => { query.pageSize = s; query.pageNum = 1; load() }"
    />

    <el-drawer :title="profile.detailTitle" :visible.sync="detailVisible" size="70%">
      <div v-if="detail" class="log-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模块">{{ profile.title }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ actionLabel(detail.actionType) }}</el-descriptions-item>
          <el-descriptions-item :label="profile.targetLabel">{{ detail.targetCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.targetName || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="profile.showProject" label="项目编码">{{ detail.projectCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.success === 1 ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.requestUrl" :label="profile.resourceLabel" :span="2">{{ detail.requestUrl }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ detail.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="moduleType === 'DATABASE'">
          <div class="detail-grid">
            <div class="detail-kv"><span>连接方式</span><strong>{{ dbRequest.connectionMode || '-' }}</strong></div>
            <div class="detail-kv"><span>查询状态</span><strong>{{ dbResponse.queryStatus || '-' }}</strong></div>
            <div class="detail-kv"><span>开始时间</span><strong>{{ dbResponse.startTime || dbRequest.startTime || '-' }}</strong></div>
            <div class="detail-kv"><span>结束时间</span><strong>{{ dbResponse.endTime || '-' }}</strong></div>
          </div>
          <detail-block title="SQL" :content="dbRequest.sql || '-'" />
          <detail-block title="SQL 参数" :content="pretty(dbRequest.paramFields || dbRequest.params)" />
          <detail-block title="返回结果行" :content="pretty(dbResponse.rows)" />
          <detail-block title="结果提取" :content="pretty({ resultPath: dbResponse.resultPath, extractedValue: dbResponse.extractedValue })" />
        </template>

        <template v-else-if="moduleType === 'LIST'">
          <div class="detail-grid">
            <div class="detail-kv"><span>匹配值</span><strong>{{ listRequest.queryValue || '-' }}</strong></div>
            <div class="detail-kv"><span>匹配模式</span><strong>{{ listRequest.matchMode || '-' }}</strong></div>
            <div class="detail-kv"><span>内容类型</span><strong>{{ prettyInline(listRequest.itemTypes) }}</strong></div>
            <div class="detail-kv"><span>是否命中</span><strong>{{ listResponse.hit === true ? '命中' : '未命中' }}</strong></div>
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
            <div class="detail-kv"><span>请求方法</span><strong>{{ detail.requestMethod || '-' }}</strong></div>
            <div class="detail-kv"><span>响应状态</span><strong>{{ detail.responseStatus || '-' }}</strong></div>
          </div>
          <detail-block title="请求头" :content="pretty(detail.requestHeaders)" />
          <detail-block title="请求参数" :content="pretty(detail.requestParams)" />
          <detail-block title="请求体" :content="pretty(detail.requestBody)" />
          <detail-block title="响应内容" :content="pretty(detail.responseBody)" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listRuntimeLogs } from '@/api/runtimeLog'

const PROFILES = {
  DATASOURCE: {
    title: 'API外数调用日志',
    subtitle: '展示三方 API 鉴权、请求头、请求参数、请求体、响应状态和响应内容。',
    targetLabel: '接口编码',
    methodLabel: 'HTTP',
    resourceLabel: '请求地址',
    summaryLabel: '响应摘要',
    detailTitle: 'API外数调用详情',
    showMethod: true,
    showResource: true,
    showProject: false,
    emptyResource: '-'
  },
  DATABASE: {
    title: '数据源查询日志',
    subtitle: '展示数据库连接测试、只读 SQL、占位参数、返回结果行和变量提取结果。',
    targetLabel: '数据源/变量',
    methodLabel: '类型',
    resourceLabel: '数据库资源',
    summaryLabel: 'SQL摘要',
    detailTitle: '数据源查询详情',
    showMethod: true,
    showResource: false,
    showProject: false,
    emptyResource: '-'
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
    emptyResource: '-'
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
    emptyResource: '-'
  }
}

export default {
  name: 'ModuleCallLog',
  components: {
    DetailBlock: {
      functional: true,
      props: {
        title: String,
        content: String
      },
      render(h, ctx) {
        return h('div', { class: 'detail-block' }, [
          h('div', { class: 'detail-title' }, ctx.props.title),
          h('pre', { class: 'log-pre' }, ctx.props.content || '-')
        ])
      }
    }
  },
  props: {
    moduleType: { type: String, required: true },
    title: { type: String, default: '' }
  },
  data() {
    return {
      loading: false,
      rows: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10, actionType: '', targetCode: '', success: '' },
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
        EXECUTE: '执行测试'
      }
    }
  },
  computed: {
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
        { label: '鉴权测试', value: 'AUTH_TEST' }
      ]
      const database = [
        { label: '只读查询', value: 'QUERY' },
        { label: '连接测试', value: 'TEST_CONNECTION' },
        { label: '草稿连接测试', value: 'TEST_CONNECTION_DRAFT' },
        { label: 'DB变量查询', value: 'DB_VARIABLE_QUERY' }
      ]
      const list = [{ label: '名单变量匹配', value: 'LIST_VARIABLE_MATCH' }]
      const model = [{ label: '执行测试', value: 'EXECUTE' }]
      if (this.moduleType === 'DATASOURCE') return datasource
      if (this.moduleType === 'DATABASE') return database
      if (this.moduleType === 'LIST') return list
      if (this.moduleType === 'MODEL') return model
      return datasource.concat(database, list, model)
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const params = this.cleanParams({ ...this.query, moduleType: this.moduleType })
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
      this.query = { pageNum: 1, pageSize: this.query.pageSize, actionType: '', targetCode: '', success: '' }
      this.load()
    },
    openDetail(row) {
      this.detail = row
      this.detailVisible = true
    },
    actionLabel(value) {
      return this.actionMap[value] || value || '-'
    },
    rowSummary(row) {
      if (this.moduleType === 'DATABASE') {
        const request = this.parseJsonValue(row.requestBody)
        return request.sql || this.prettyInline(request.params || request.paramFields)
      }
      if (this.moduleType === 'LIST') {
        const request = this.parseJsonValue(row.requestBody)
        const response = this.parseJsonValue(row.responseBody)
        return '值=' + (request.queryValue || '-') + '，' + (response.hit ? '命中' : '未命中')
      }
      if (this.moduleType === 'MODEL') {
        return this.prettyInline(this.parseJsonValue(row.responseBody))
      }
      return row.responseStatus ? 'HTTP ' + row.responseStatus : this.prettyInline(this.parseJsonValue(row.responseBody))
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
      const pad = n => String(n).padStart(2, '0')
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
    },
    cleanParams(params) {
      Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null || params[key] === undefined) delete params[key]
      })
      return params
    }
  }
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
