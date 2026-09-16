<template>
  <section class="workbench-overview" aria-label="项目工作台">
    <div class="overview-heading">
      <div>
        <p>集中查看配置进度、待处理事项和最近运行情况。</p>
      </div>
      <div class="overview-status" aria-live="polite">
        <el-tag :type="readinessSummary.type">{{ readinessSummary.label }}</el-tag>
        <el-button
          data-testid="refresh-workbench"
          :loading="loading"
          :disabled="loading"
          @click="$emit('action', { actionCode: 'REFRESH_WORKBENCH' })"
        >{{ error ? '重新加载' : '刷新状态' }}</el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      class="overview-alert"
      title="工作台数据暂时无法读取"
      :description="error"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-alert
      v-else-if="warnings.length"
      class="overview-alert"
      title="部分状态暂时无法读取"
      :description="warnings.join('；')"
      type="warning"
      show-icon
      :closable="false"
    />

    <div v-if="nextCheck" class="next-action-card">
      <div>
        <span class="section-kicker">下一步建议</span>
        <strong>{{ nextCheck.title }}</strong>
        <p>{{ nextCheck.reason }}</p>
      </div>
      <el-button
        v-if="nextCheck.actionCode"
        type="primary"
        :disabled="!canUseAction(nextCheck.actionCode)"
        :title="canUseAction(nextCheck.actionCode) ? '' : '当前账号没有此模块的访问权限'"
        @click="$emit('action', nextCheck)"
      >
        {{ nextCheck.actionLabel || '立即处理' }}
      </el-button>
    </div>

    <div class="metrics-grid" v-loading="loading">
      <div v-for="metric in metricItems" :key="metric.label" class="metric-card">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.hint }}</small>
      </div>
    </div>

    <div class="overview-grid">
      <div class="panel readiness-panel">
        <div class="panel-heading">
          <div>
            <h4>配置与运行检查</h4>
            <p>根据当前配置和执行记录检查；发布仍需完成规则校验与审批。</p>
          </div>
          <span>{{ checks.length ? `${readyCount}/${checks.length} 已就绪` : '待检查' }}</span>
        </div>
        <div v-if="checks.length" class="check-list">
          <div v-for="item in checks" :key="item.code" class="check-row">
            <span
              class="status-mark"
              :class="`status-${item.status.toLowerCase()}`"
              aria-hidden="true"
            />
            <div class="check-content">
              <div class="check-title">
                <strong>{{ item.title }}</strong>
                <span>{{ statusText(item.status) }}</span>
              </div>
              <p>{{ item.reason }}</p>
            </div>
            <el-button
              v-if="item.actionCode"
              link
              type="primary"
              :disabled="!canUseAction(item.actionCode)"
              :title="canUseAction(item.actionCode) ? '' : '当前账号没有此模块的访问权限'"
              @click="$emit('action', item)"
            >
              {{ item.actionLabel || '查看' }}
            </el-button>
          </div>
        </div>
        <el-empty v-else :image-size="64" :description="loading ? '正在读取检查结果' : '暂无检查结果，请重新加载'" />
      </div>

      <div class="side-column">
        <div class="panel">
          <div class="panel-heading">
            <div>
              <h4>常用操作</h4>
              <p>始终在当前项目范围内创建和查看资源。</p>
            </div>
          </div>
          <div class="quick-actions">
            <el-button
              v-for="action in quickActions"
              :key="action.actionCode"
              plain
              :disabled="!canUseAction(action.actionCode)"
              @click="$emit('action', action)"
            >
              {{ action.actionLabel }}
            </el-button>
          </div>
          <p v-if="quickActions.some(action => !canUseAction(action.actionCode))" class="permission-hint">
            灰色入口表示当前账号没有对应模块的访问权限，请联系项目管理员。
          </p>
        </div>

        <div class="panel recent-panel">
          <div class="panel-heading">
            <div>
              <h4>最近一次执行</h4>
              <p>查看最近执行结果；调用来源和修订以日志详情为准。</p>
            </div>
          </div>
          <template v-if="recentExecution">
            <div class="recent-summary">
              <el-tag :type="recentExecution.success === 1 ? 'success' : 'danger'">
                {{ recentExecution.success === 1 ? '执行成功' : '执行失败' }}
              </el-tag>
              <strong>{{ recentExecution.ruleCode || '未知规则' }}</strong>
            </div>
            <dl>
              <div>
                <dt>耗时</dt>
                <dd>{{ formatNumber(recentExecution.executeTimeMs) }} ms</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>{{ recentExecution.source || '-' }}</dd>
              </div>
              <div>
                <dt>时间</dt>
                <dd>{{ recentExecution.createTime || '-' }}</dd>
              </div>
            </dl>
            <el-button link type="primary" :disabled="!canUseAction(logAction.actionCode)" @click="$emit('action', logAction)">
              查看执行日志
            </el-button>
          </template>
          <el-empty v-else :image-size="56" description="暂无执行记录" />
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { canUseWorkbenchAction } from '@/utils/workbenchActions'

const STATUS_TEXT = {
  READY: '已就绪',
  OPTIONAL: '可选',
  ACTION_REQUIRED: '待配置',
  ATTENTION: '需关注',
  BLOCKED: '被阻塞',
  UNAVAILABLE: '暂不可用',
}

export default {
  name: 'ProjectWorkbenchOverview',
  props: {
    workbench: {
      type: Object,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    error: {
      type: String,
      default: '',
    },
  },
  emits: ['action'],
  data() {
    return {
      quickActions: [
        { actionCode: 'CONFIGURE_FIELDS', actionLabel: '业务字段' },
        { actionCode: 'CONFIGURE_EXTERNAL_SOURCES', actionLabel: '外数管理' },
        { actionCode: 'CONFIGURE_DATABASE_SOURCES', actionLabel: '数据库' },
        { actionCode: 'CONFIGURE_RULES', actionLabel: '规则设计' },
        { actionCode: 'TEST_RULES', actionLabel: '规则测试' },
        { actionCode: 'REVIEW_APPROVALS', actionLabel: '审批事项' },
        { actionCode: 'VIEW_LOGS', actionLabel: '执行日志' },
      ],
      logAction: { actionCode: 'VIEW_LOGS', actionLabel: '查看执行日志' },
    }
  },
  computed: {
    metrics() {
      return (this.workbench && this.workbench.metrics) || {}
    },
    checks() {
      if (this.loading || this.error) return []
      return (this.workbench && this.workbench.checks) || []
    },
    readinessSummary() {
      if (this.loading) return { type: 'info', label: '正在检查' }
      if (this.error) return { type: 'danger', label: '状态读取失败' }
      if (!this.checks.length) return { type: 'info', label: '尚未获取检查结果' }
      if (this.warnings.length || this.checks.some(item => item.status === 'UNAVAILABLE')) {
        return { type: 'warning', label: '部分状态待确认' }
      }
      return this.remainingCount
        ? { type: 'warning', label: `还有 ${this.remainingCount} 项待处理` }
        : { type: 'success', label: '当前检查项已就绪' }
    },
    warnings() {
      return (this.workbench && this.workbench.warnings) || []
    },
    recentExecution() {
      return this.workbench && this.workbench.recentExecution
    },
    readyCount() {
      return this.checks.filter((item) =>
        ['READY', 'OPTIONAL'].includes(item.status)
      ).length
    },
    remainingCount() {
      return this.checks.filter(
        (item) => !['READY', 'OPTIONAL'].includes(item.status)
      ).length
    },
    nextCheck() {
      const priority = [
        'BLOCKED',
        'ACTION_REQUIRED',
        'ATTENTION',
        'UNAVAILABLE',
      ]
      return priority
        .map((status) => this.checks.find((item) => item.status === status))
        .find(Boolean)
    },
    metricItems() {
      const totalSources = this.formatNumber(this.metrics.dataSourceCount)
      const enabledSources = this.formatNumber(
        this.metrics.enabledDataSourceCount
      )
      const executionCount = this.formatNumber(
        this.metrics.recentExecutionCount
      )
      const successRate =
        this.metrics.recentSuccessRate == null
          ? '-'
          : `${this.metrics.recentSuccessRate}%`
      return [
        {
          label: '业务字段',
          value: this.formatNumber(this.metrics.fieldCount),
          hint: '项目级变量',
        },
        {
          label: '启用数据源',
          value: `${enabledSources}/${totalSources}`,
          hint: '外数与数据库数据源',
        },
        {
          label: '预测模型',
          value: this.formatNumber(this.metrics.modelCount),
          hint: '项目级模型',
        },
        {
          label: '规则状态',
          value: `${this.formatNumber(this.metrics.publishedRuleCount)} 已发布`,
          hint: `${this.formatNumber(this.metrics.draftRuleCount)} 条草稿`,
        },
        {
          label: '待审批',
          value: this.formatNumber(this.metrics.pendingApprovalCount),
          hint: '等待处理的变更申请',
        },
        {
          label: '24 小时执行',
          value: executionCount,
          hint: `成功率 ${successRate}`,
        },
      ]
    },
  },
  methods: {
    canUseAction: canUseWorkbenchAction,
    statusText(status) {
      return STATUS_TEXT[status] || status
    },
    formatNumber(value) {
      return value == null ? '-' : value
    },
  },
}
</script>

<style lang="scss" scoped>
.workbench-overview {
  margin-bottom: 24px;
}

.overview-heading,
.panel-heading,
.next-action-card,
.check-row,
.recent-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.overview-heading {
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.overview-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-heading h4 {
  margin: 0;
  color: var(--tianshu-text-primary);
}

.overview-heading p,
.panel-heading p,
.next-action-card p,
.check-content p {
  margin: 4px 0 0;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.overview-alert {
  margin-bottom: 12px;
}

.next-action-card {
  gap: 16px;
  margin-bottom: 12px;
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
}

.next-action-card strong {
  display: block;
  margin-top: 4px;
  color: var(--tianshu-text-primary);
  font-size: 16px;
}

.section-kicker {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  min-height: 92px;
  margin-bottom: 12px;
}

.metric-card,
.panel {
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-surface);
}

.metric-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
}

.metric-card span,
.metric-card small {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.metric-card strong {
  margin: 6px 0 4px;
  overflow: hidden;
  color: var(--tianshu-text-primary);
  font-size: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 12px;
}

.panel {
  padding: 16px;
}

.panel-heading {
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-heading > span {
  color: var(--tianshu-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.check-list {
  border-top: 1px solid #eef2f7;
}

.check-row {
  justify-content: flex-start;
  gap: 10px;
  min-height: 58px;
  border-bottom: 1px solid #eef2f7;
}

.check-row:last-child {
  border-bottom: 0;
}

.status-mark {
  width: 9px;
  height: 9px;
  border: 2px solid #94a3b8;
  border-radius: 50%;
  box-sizing: border-box;
  flex: 0 0 auto;
}

.status-ready {
  border-color: #16a34a;
  background: #16a34a;
}

.status-optional {
  border-color: #64748b;
  background: var(--tianshu-bg-surface);
}

.status-action_required,
.status-attention {
  border-color: #d97706;
  background: #fef3c7;
}

.status-blocked,
.status-unavailable {
  border-color: #dc2626;
  background: #fee2e2;
}

.check-content {
  min-width: 0;
  flex: 1;
}

.check-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-title strong {
  color: var(--tianshu-text-primary);
  font-size: 13px;
}

.check-title span {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quick-actions :deep(.el-button) {
  margin-left: 0;
}

.permission-hint { color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.5; }

.recent-summary {
  justify-content: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.recent-panel dl {
  margin: 0 0 8px;
}

.recent-panel dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid #eef2f7;
  font-size: 12px;
}

.recent-panel dt {
  color: var(--tianshu-text-tertiary);
}

.recent-panel dd {
  margin: 0;
  color: var(--tianshu-text-primary);
  text-align: right;
}

@media (max-width: 1280px) {
  .metrics-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
