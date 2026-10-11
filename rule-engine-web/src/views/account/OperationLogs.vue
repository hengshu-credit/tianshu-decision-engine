<template>
  <div class="operation-logs management-table-region">
    <div class="uiue-search-container uiue-filter-toolbar">
      <div class="log-filters">
        <el-input v-model="query.username" clearable placeholder="操作账号" aria-label="操作账号" @keyup.enter="search" />
        <el-select v-model="query.module" clearable placeholder="全部模块" aria-label="操作模块">
          <el-option v-for="(label, value) in modules" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.action" clearable placeholder="全部操作" aria-label="操作类型">
          <el-option v-for="(label, value) in actions" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="query.outcome" clearable placeholder="全部结果" aria-label="操作结果">
          <el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILED" /><el-option label="无权访问" value="DENIED" />
        </el-select>
        <el-input v-model="query.keyword" clearable placeholder="对象名称、ID、路径或摘要" aria-label="操作对象" @keyup.enter="search" />
        <el-date-picker v-model="timeRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" />
      </div>
      <div class="log-actions"><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="reset">重置</el-button></div>
    </div>
    <p class="log-note">记录具体对象、页面路径、操作类型及结果。页面和字段访问由前端呈报；不记录正文或前后快照，未提交的输入不记为修改。</p>
    <el-table v-loading="loading" :data="rows" row-key="id" show-overflow-tooltip class="management-table">
      <el-table-column label="操作时间" min-width="175"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
      <el-table-column prop="username" label="操作账号" min-width="125" />
      <el-table-column label="模块" width="115"><template #default="{ row }">{{ modules[row.module] || row.module }}</template></el-table-column>
      <el-table-column label="操作" width="115"><template #default="{ row }">{{ actions[row.action] || row.action }}</template></el-table-column>
      <el-table-column label="操作对象" min-width="220"><template #default="{ row }">{{ row.targetName || row.pagePath || row.requestPath }}<small v-if="row.targetId"> · ID {{ row.targetId }}</small></template></el-table-column>
      <el-table-column prop="pagePath" label="页面路径" min-width="260"><template #default="{ row }">{{ row.pagePath || '—（接口调用）' }}</template></el-table-column>
      <el-table-column label="版本" width="110"><template #default="{ row }">{{ row.targetVersion || '—' }}</template></el-table-column>
      <el-table-column label="结果" width="105"><template #default="{ row }"><el-tag :type="row.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ outcomes[row.outcome] || row.outcome }}</el-tag></template></el-table-column>
      <el-table-column prop="changeSummary" label="操作摘要" min-width="240" />
      <el-table-column prop="clientIp" label="来源 IP" min-width="120" />
      <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    <el-dialog v-model="detailVisible" title="操作日志详情" width="min(760px, 94vw)">
      <div v-if="detail" class="log-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="操作账号">{{ detail.username }}（ID {{ detail.userId || '—' }}）</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="操作">{{ modules[detail.module] || detail.module }} · {{ actions[detail.action] || detail.action }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ outcomes[detail.outcome] }}（{{ detail.resultCode }}）</el-descriptions-item>
          <el-descriptions-item label="页面路径" :span="2"><span class="location-path">{{ detail.pagePath || '—（接口调用）' }}</span></el-descriptions-item>
          <el-descriptions-item label="来源 IP">{{ detail.clientIp }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ detail.targetName || detail.targetType || '—' }} · {{ detail.targetId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.targetVersion || '—' }}</el-descriptions-item>
          <el-descriptions-item label="接口">{{ detail.requestMethod }} {{ detail.requestPath }}</el-descriptions-item>
          <el-descriptions-item label="操作摘要" :span="2">{{ detail.changeSummary || '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listConsoleOperationLogs, getConsoleOperationLog } from '@/api/consoleAccount'

const emptyQuery = () => ({ username: '', module: '', action: '', outcome: '', keyword: '', pageNum: 1, pageSize: 20 })
export default {
  name: 'OperationLogs',
  data() {
    return {
      query: emptyQuery(), timeRange: [], loading: false, rows: [], total: 0, detail: null, detailVisible: false, requestId: 0,
      modules: { project: '项目管理', rule: '规则管理', field: '变量与数据对象', list: '名单管理', datasource: '外数管理', database: '数据库管理', model: '模型管理', function: '函数管理', experiment: '分流实验', log: '执行日志', billing: '账单管理', approval: '审批管理', transfer: '离线迁移', lineage: '血缘分析', account: '账户管理', dashboard: '数据看板' },
      actions: { READ: '查看', CREATE: '新增', UPDATE: '修改', DELETE: '删除', PUBLISH: '发布', DEBUG: '调试', COMPILE: '编译', OFFLINE: '下线', ROLLBACK: '回滚', RESTORE: '恢复版本', ENABLE: '启用', DISABLE: '停用', EXECUTE: '执行', IMPORT: '导入', EXPORT: '导出', SUBMIT: '提交审批', APPROVE: '审批通过', REJECT: '审批拒绝', CANCEL: '取消', PAGE_VIEW: '查看页面', FIELD_VIEW: '查看字段', FIELD_SELECT: '选择字段', LOGIN: '登录', LOGOUT: '退出登录' },
      outcomes: { SUCCESS: '成功', FAILED: '失败', DENIED: '无权访问' },
    }
  },
  mounted() { this.load() },
  beforeUnmount() { this.requestId++ },
  methods: {
    async load() {
      const id = ++this.requestId
      this.loading = true
      try {
        const result = await listConsoleOperationLogs({ ...this.query, startTime: this.timeRange?.[0], endTime: this.timeRange?.[1] })
        if (id !== this.requestId) return
        this.rows = result.data?.records || []
        this.total = result.data?.total || 0
      } finally { if (id === this.requestId) this.loading = false }
    },
    search() { this.query.pageNum = 1; return this.load() },
    reset() { this.query = emptyQuery(); this.timeRange = []; return this.load() },
    async openDetail(row) { const result = await getConsoleOperationLog(row.id); this.detail = result.data; this.detailVisible = true },
    formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' },
  },
}
</script>

<style scoped>
.log-filters { display: flex; flex: 1; gap: 8px; flex-wrap: wrap; min-width: 0; }
.log-filters > .el-input { width: 180px; }
.log-filters > .el-select { width: 140px; }
.log-filters > .el-date-editor { flex: 0 1 340px; min-width: 280px; }
.log-actions { display: flex; margin-left: auto; }
.log-note { color: var(--tianshu-text-tertiary); font-size: 12px; line-height: 1.6; }
.location-path { overflow-wrap: anywhere; }
.el-pagination { margin-top: 16px; justify-content: flex-end; flex-wrap: wrap; }
</style>
