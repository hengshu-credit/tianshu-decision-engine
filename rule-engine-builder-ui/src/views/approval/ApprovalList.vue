<template>
  <div class="uiue-list-page approval-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">LIFECYCLE GOVERNANCE</span>
        <h1>审批任务中心</h1>
        <p>优先处理待审批事项，也可按申请人、资源和动作追踪完整变更记录。</p>
      </div>
      <div class="header-stat">
        <strong>{{ summary.pendingCount }}</strong>
        <span>待处理</span>
      </div>
    </header>

    <section class="summary-grid" aria-label="审批任务概览">
      <button
        v-for="item in summaryCards"
        :key="item.key"
        type="button"
        class="summary-card"
        :class="{ 'is-active': summarySelection === item.key }"
        @click="applySummary(item.key)"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.help }}</small>
      </button>
    </section>

    <section class="approval-card">
      <el-tabs
        v-model="activeScope"
        class="module-tabs"
        @tab-change="changeScope"
      >
        <el-tab-pane
          v-for="scope in taskScopes"
          :key="scope.name"
          :label="scope.label"
          :name="scope.name"
        />
      </el-tabs>

      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="审批单号或变更说明"
          class="keyword-filter"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.resourceType"
          clearable
          filterable
          placeholder="资源类型"
        >
          <el-option
            v-for="item in resourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-if="activeScope !== 'PENDING'"
          v-model="filters.status"
          clearable
          placeholder="申请状态"
        >
          <el-option
            v-for="item in visibleStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="filters.action" clearable placeholder="生命周期动作">
          <el-option
            v-for="item in actionOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table show-overflow-tooltip
        v-loading="loading"
        :data="records"
        row-key="id"
        class="approval-table"
        @row-click="openDetail"
      >
        <el-table-column label="审批申请" min-width="250">
          <template #default="{ row }">
            <div class="request-cell">
              <span class="request-icon">{{ moduleInitial(row.resourceType) }}</span>
              <span>
                <strong>{{ resourceTitle(row) }}</strong>
                <small>{{ row.requestNo }}</small>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ resourceTypeLabel(row.resourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <span class="action-label">{{ actionLabel(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="130" />
        <el-table-column label="提交时间" width="172">
          <template #default="{ row }">
            {{ formatTime(row.submitTime || row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <strong>{{ emptyTitle }}</strong>
            <span>{{ emptyHelp }}</span>
          </div>
        </template>
      </el-table>

      <footer class="pagination-row">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          layout="prev, pager, next"
          :total="total"
          @current-change="loadRequests"
        />
      </footer>
    </section>
  </div>
</template>

<script>
import router from '@/router'
import {
  getGovernanceSummary,
  listGovernanceRequests,
} from '@/api/governance'
import { routeProjectId } from '@/utils/projectContext'

const STATUS_OPTIONS = [
  { value: 'EDITING', label: '编辑中' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CONFLICT', label: '存在冲突' },
  { value: 'CANCELLED', label: '已取消' }
]

const TERMINAL_STATUS_VALUES = new Set([
  'APPROVED',
  'REJECTED',
  'CANCELLED',
  'CONFLICT',
])

const ACTION_OPTIONS = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
  { value: 'DELETE', label: '删除' },
  { value: 'RESTORE', label: '恢复版本' }
]

const RESOURCE_OPTIONS = [
  { value: 'VARIABLE', label: '字段' },
  { value: 'DATA_OBJECT', label: '数据对象' },
  { value: 'FIELD_VALIDATION', label: '字段校验' },
  { value: 'MODEL', label: '模型' },
  { value: 'EXTERNAL_DATASOURCE', label: '外数源' },
  { value: 'EXTERNAL_API', label: '外数 API' },
  { value: 'DATABASE', label: '数据库' },
  { value: 'FUNCTION', label: '函数' },
  { value: 'RULE', label: '规则' },
  { value: 'RULE_PROJECT_BINDING', label: '项目规则关联' },
  { value: 'LIST_LIBRARY', label: '名单库' },
  { value: 'LIST_RECORD_BATCH', label: '名单内容批次' },
  { value: 'BILLING_CONFIG', label: '计费配置' },
  { value: 'EXPERIMENT', label: '分流' },
  { value: 'PROJECT', label: '项目' },
]

export default {
  name: 'ApprovalList',
  data() {
    return {
      taskScopes: [
        { name: 'PENDING', label: '待处理' },
        { name: 'MINE', label: '我的申请' },
        { name: 'COMPLETED', label: '已结束' },
        { name: 'ALL', label: '全部记录' },
      ],
      resourceOptions: RESOURCE_OPTIONS,
      statusOptions: STATUS_OPTIONS,
      actionOptions: ACTION_OPTIONS,
      activeScope: 'PENDING',
      filters: {
        keyword: '',
        resourceType: '',
        status: '',
        action: '',
      },
      summary: {
        pendingCount: 0,
        myDraftCount: 0,
        myRequestCount: 0,
        completedCount: 0,
      },
      records: [],
      pageNum: 1,
      pageSize: 20,
      total: 0,
      loading: false,
      contextProjectId: null,
    }
  },
  computed: {
    summaryCards() {
      return [
        {
          key: 'PENDING',
          label: '待处理',
          value: this.summary.pendingCount,
          help: '等待审批并生效',
        },
        {
          key: 'MY_DRAFT',
          label: '我的草稿',
          value: this.summary.myDraftCount,
          help: '尚未提交的申请',
        },
        {
          key: 'MINE',
          label: '我的申请',
          value: this.summary.myRequestCount,
          help: '我发起的全部记录',
        },
        {
          key: 'COMPLETED',
          label: '已结束',
          value: this.summary.completedCount,
          help: '已通过、驳回或取消',
        },
      ]
    },
    visibleStatusOptions() {
      if (this.activeScope !== 'COMPLETED') return this.statusOptions
      return this.statusOptions.filter((item) =>
        TERMINAL_STATUS_VALUES.has(item.value)
      )
    },
    summarySelection() {
      if (this.activeScope === 'PENDING') return 'PENDING'
      if (this.activeScope === 'MINE' && this.filters.status === 'EDITING') {
        return 'MY_DRAFT'
      }
      if (this.activeScope === 'MINE' && !this.filters.status) return 'MINE'
      if (this.activeScope === 'COMPLETED') return 'COMPLETED'
      return ''
    },
    emptyTitle() {
      return this.activeScope === 'PENDING'
        ? '当前没有等待处理的审批'
        : '没有符合条件的审批记录'
    },
    emptyHelp() {
      return this.activeScope === 'PENDING'
        ? '新的业务变更提交后会自动出现在这里。'
        : '可以调整资源类型、状态或关键词后重新查询。'
    },
  },
  created() {
    this.contextProjectId = routeProjectId(
      this.$route || router.currentRoute.value,
      this.$store && this.$store.state.currentProject
    )
    this.loadSummary()
    this.loadRequests()
  },
  methods: {
    async loadSummary() {
      const response = await getGovernanceSummary({
        ...(this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {}),
      })
      this.summary = { ...this.summary, ...(response.data || {}) }
    },
    async loadRequests() {
      this.loading = true
      try {
        const response = await listGovernanceRequests({
          taskScope: this.activeScope,
          tab: undefined,
          resourceType: this.filters.resourceType || undefined,
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          action: this.filters.action || undefined,
          projectId: this.contextProjectId || undefined,
          pageNum: this.pageNum,
          pageSize: this.pageSize
        })
        const page = response.data || {}
        this.records = page.records || []
        this.total = Number(page.total || 0)
      } finally {
        this.loading = false
      }
    },
    changeScope() {
      this.pageNum = 1
      if (this.activeScope === 'PENDING') this.filters.status = ''
      if (
        this.activeScope === 'COMPLETED' &&
        !TERMINAL_STATUS_VALUES.has(this.filters.status)
      ) {
        this.filters.status = ''
      }
      this.loadRequests()
    },
    search() {
      this.pageNum = 1
      this.loadRequests()
    },
    resetFilters() {
      this.filters = {
        keyword: '',
        resourceType: '',
        status: '',
        action: '',
      }
      this.search()
    },
    applySummary(key) {
      this.pageNum = 1
      if (key === 'PENDING') {
        this.activeScope = 'PENDING'
        this.filters.status = ''
      } else if (key === 'MY_DRAFT') {
        this.activeScope = 'MINE'
        this.filters.status = 'EDITING'
      } else if (key === 'MINE') {
        this.activeScope = 'MINE'
        this.filters.status = ''
      } else {
        this.activeScope = 'COMPLETED'
        this.filters.status = ''
      }
      this.loadRequests()
    },
    openDetail(row) {
      if (!this.contextProjectId) {
        router.push(`/approval/${row.id}`)
        return
      }
      router.push({
        path: `/approval/${row.id}`,
        query: { projectId: this.contextProjectId },
      })
    },
    snapshot(row) {
      const text = row.submittedSnapshotJson || row.draftSnapshotJson
      if (!text) return {}
      try {
        return JSON.parse(text)
      } catch (error) {
        return {}
      }
    },
    resourceTitle(row) {
      const value = this.snapshot(row)
      if (
        row.resourceType === 'RULE_PROJECT_BINDING' &&
        value.definitionId &&
        value.projectId
      ) {
        const action = row.action === 'DELETE' ? '移出' : '加入'
        const rule = value.ruleName
          ? `“${value.ruleName}”`
          : `#${value.definitionId}`
        const project = value.projectName
          ? `“${value.projectName}”`
          : `#${value.projectId}`
        return `将规则${rule}${action}项目${project}`
      }
      if (row.resourceType === 'LIST_RECORD_BATCH' && value.batchId) {
        const count = (value.addCount || 0) +
          (value.updateCount || 0) + (value.deleteCount || 0)
        return `${value.listName || '名单'} · ${count} 条内容变更`
      }
      return value.ruleName || value.modelName || value.funcName ||
        value.datasourceName || value.projectName || value.varLabel ||
        value.objectLabel || value.experimentName || value.listName ||
        value.validationName || value.billingName ||
        `${this.resourceTypeLabel(row.resourceType)} #${row.resourceId}`
    },
    moduleInitial(type) {
      return this.resourceTypeLabel(type).slice(0, 1)
    },
    resourceTypeLabel(type) {
      return {
        VARIABLE: '字段',
        DATA_OBJECT: '数据对象',
        MODEL: '模型',
        EXTERNAL_DATASOURCE: '外数源',
        EXTERNAL_API: '外数 API',
        DATABASE: '数据库',
        FUNCTION: '函数',
        RULE: '规则',
        RULE_PROJECT_BINDING: '项目规则关联',
        LIST_LIBRARY: '名单库',
        LIST_RECORD_BATCH: '名单内容批次',
        FIELD_VALIDATION: '字段校验',
        BILLING_CONFIG: '计费配置',
        EXPERIMENT: '分流',
        PROJECT: '项目'
      }[type] || type || '资源'
    },
    actionLabel(action) {
      return (ACTION_OPTIONS.find(item => item.value === action) || {}).label || action
    },
    statusLabel(status) {
      return (STATUS_OPTIONS.find(item => item.value === status) || {}).label || status
    },
    statusType(status) {
      return {
        EDITING: 'info',
        PENDING: 'warning',
        APPROVED: 'success',
        REJECTED: 'danger',
        CONFLICT: 'danger',
        CANCELLED: 'info'
      }[status] || 'info'
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
    }
  }
}
</script>

<style scoped>
.approval-page {
  color: var(--tianshu-text-primary);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.page-header h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.04em;
}

.page-header p {
  margin: 9px 0 0;
  color: var(--tianshu-text-secondary);
}

.header-stat {
  display: flex;
  align-items: baseline;
  gap: 9px;
  color: var(--tianshu-text-secondary);
}

.header-stat strong {
  color: var(--tianshu-text-primary);
  font-size: 30px;
}

.approval-card {
  overflow: hidden;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
  box-shadow: var(--tianshu-shadow-small);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  min-height: 108px;
  flex-direction: column;
  align-items: flex-start;
  padding: 16px 18px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
  color: var(--tianshu-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.summary-card:hover,
.summary-card.is-active {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 8px 22px var(--tianshu-primary-shadow);
}

.summary-card.is-active {
  background: var(--tianshu-bg-active);
}

.summary-card strong {
  margin: 7px 0 3px;
  color: var(--tianshu-text-primary);
  font-size: 28px;
}

.summary-card small {
  color: var(--tianshu-text-tertiary);
}

.module-tabs {
  padding: 0 22px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
}

.filter-bar {
  display: flex;
  gap: 10px;
  padding: 18px 22px;
  background: var(--tianshu-bg-soft);
}

.filter-bar .keyword-filter {
  width: min(360px, 34vw);
}

.filter-bar :deep(.el-select) {
  width: 160px;
}

.approval-table {
  cursor: pointer;
}

.request-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.request-cell > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.request-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-cell small {
  margin-top: 3px;
  color: var(--tianshu-text-tertiary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.request-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 9px;
  background: var(--tianshu-info-bg);
  color: var(--tianshu-info-text);
  font-weight: 700;
}

.action-label {
  color: var(--tianshu-text-secondary);
  font-weight: 600;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 22px;
  border-top: 1px solid var(--tianshu-border-subtle);
  color: var(--tianshu-text-tertiary);
  font-size: 13px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 46px 0;
  color: var(--tianshu-text-tertiary);
}

.empty-state strong {
  color: var(--tianshu-text-secondary);
}

@media (max-width: 900px) {
  .header-stat {
    display: none;
  }

  .filter-bar {
    flex-wrap: wrap;
  }

  .filter-bar .keyword-filter {
    width: 100%;
  }

  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
