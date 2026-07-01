<template>
  <div class="module-call-log">
    <div class="log-header">
      <div>
        <div class="log-title">{{ title || '调用日志' }}</div>
        <div class="log-subtitle">记录模块测试、变量解析和执行调用的请求、响应、耗时与错误。</div>
      </div>
      <el-button size="small" icon="el-icon-refresh" @click="load">刷新</el-button>
    </div>
    <div class="log-filter">
      <el-form :inline="true" size="small">
        <el-form-item label="动作">
          <el-select v-model="query.actionType" clearable placeholder="全部" style="width:160px">
            <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标编码">
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
      <el-table-column prop="targetCode" label="目标编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="targetName" label="目标名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="requestUrl" label="请求地址/资源" min-width="220" show-overflow-tooltip />
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

    <el-drawer title="调用日志详情" :visible.sync="detailVisible" size="70%">
      <div v-if="detail" class="log-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模块">{{ moduleType }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ actionLabel(detail.actionType) }}</el-descriptions-item>
          <el-descriptions-item label="目标">{{ detail.targetName || detail.targetCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.success === 1 ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item label="请求地址" :span="2">{{ detail.requestUrl || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ detail.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-block">
          <div class="detail-title">请求头</div>
          <pre class="log-pre">{{ pretty(detail.requestHeaders) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-title">请求入参</div>
          <pre class="log-pre">{{ pretty(detail.requestParams) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-title">请求体</div>
          <pre class="log-pre">{{ pretty(detail.requestBody) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-title">响应内容</div>
          <pre class="log-pre">{{ pretty(detail.responseBody) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { listRuntimeLogs } from '@/api/runtimeLog'

export default {
  name: 'ModuleCallLog',
  props: {
    moduleType: { type: String, required: true },
    title: { type: String, default: '调用日志' }
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
    pretty(value) {
      if (value == null || value === '') return '-'
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return String(value)
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
