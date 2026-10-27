<template>
  <article class="execution-metrics-panel" aria-label="执行容量与持久化状态" :aria-busy="loading">
    <header class="execution-metrics-panel__header">
      <div>
        <h2>执行容量与持久化状态</h2>
        <p>
          查看日志写入、变量来源解析、开放执行线程池、外数熔断和规则预热的实时快照。
          <span v-if="instanceLabel" data-testid="execution-metrics-instance">当前节点：{{ instanceLabel }}</span>
        </p>
      </div>
      <div class="execution-metrics-panel__actions">
        <span
          data-testid="execution-metrics-snapshot-status"
          class="execution-metrics-panel__updated"
        >
          {{ snapshotStatusLabel }}<template v-if="updatedAt"> · 更新于 {{ updatedAt }}</template>
        </span>
        <el-button
          data-testid="execution-metrics-refresh"
          size="small"
          :loading="loading"
          @click="loadMetrics"
        >刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="error"
      class="execution-metrics-panel__error"
      :title="error"
      type="warning"
      :closable="false"
      show-icon
    />

    <div v-if="!loading && snapshotStatus === 'UNKNOWN'" class="execution-metrics-panel__state execution-metrics-panel__state--unknown">
      暂无可用指标快照，请刷新重试
    </div>
    <div v-else class="execution-metrics-panel__content">
      <section class="execution-metrics-group" data-testid="execution-metrics-persistence">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>持久化队列</h3>
            <p>规则执行日志与计费写入队列</p>
          </div>
          <el-tag size="small" :type="sectionStatusType(persistence, ['failed', 'fallback'])">
            {{ sectionStatusLabel(persistence, ['failed', 'fallback']) }}
          </el-tag>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="队列深度" :value="queueValue(persistence)" :detail="persistenceDetail" />
          <metric-item label="同步回退" :value="formatNumber(persistence.fallback)" :tone="warningTone(persistence.fallback)" />
          <metric-item label="写入失败" :value="formatNumber(persistence.failed)" :tone="warningTone(persistence.failed)" />
          <metric-item
            label="日志失败 / 计费失败"
            :value="`${formatNumber(persistence.logFailed)} / ${formatNumber(persistence.billingFailed)}`"
            :tone="warningToneForFields(persistence, ['logFailed', 'billingFailed'])"
          />
          <metric-item
            label="恢复待处理 / 死信"
            :value="`${formatNumber(executionPersistenceOutbox.pending)} / ${formatNumber(executionPersistenceOutbox.deadLetter)}`"
            :tone="warningToneForFields(executionPersistenceOutbox, ['pending', 'deadLetter'])"
          />
          <metric-item label="平均写入耗时" :value="formatMs(persistence.avgWriteMs)" />
        </div>
      </section>

      <section class="execution-metrics-group" data-testid="execution-metrics-journal">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>本地恢复日志</h3>
            <p>数据库不可写时保存恢复事件，数据库恢复后继续补写。</p>
          </div>
          <el-tag size="small" :type="journalStatusType">{{ journalStatusLabel }}</el-tag>
        </div>
        <el-alert
          v-if="journalStatusType === 'warning'"
          :title="`本地恢复日志${journalStatusLabel}`"
          :description="journalStatusHint"
          type="warning"
          :closable="false"
          show-icon
        />
        <div class="execution-metrics-grid">
          <metric-item
            data-testid="journal-pending"
            label="待恢复事件"
            :value="journalPendingLabel"
            :detail="journal.pendingTruncated ? '统计达到扫描上限，总数尚未确定；有已识别记录时显示数量下界。' : ''"
          />
          <metric-item label="文件大小" :value="formatBytes(journal.bytes)" />
        </div>
        <details v-if="journalStatusType === 'warning' && (journal.path || journal.failureReason)" class="execution-metrics-journal-details">
          <summary>查看故障定位信息</summary>
          <p v-if="journal.path">目录或文件：{{ journal.path }}</p>
          <p v-if="journal.failureReason">原因：{{ journal.failureReason }}</p>
          <p v-if="hasMetricValue(journal.failureOffset)">故障位置（字节）：{{ formatNumber(journal.failureOffset) }}</p>
          <p v-if="hasMetricValue(journal.replayedBytes)">已确认前缀（字节）：{{ formatNumber(journal.replayedBytes) }}</p>
        </details>
      </section>

      <section class="execution-metrics-group" data-testid="execution-metrics-executors">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>执行线程池</h3>
            <p>来源解析与开放规则执行的并行度和排队情况</p>
          </div>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="来源解析队列" :value="formatNumber(sourceResolution.queueDepth)" :detail="executorDetail(sourceResolution)" />
          <metric-item label="来源解析失败" :value="formatNumber(sourceResolution.failed)" :tone="warningTone(sourceResolution.failed)" />
          <metric-item label="开放执行队列" :value="queueValue(openExecution)" />
          <metric-item label="拒绝 / 超时" :value="`${formatNumber(openExecution.rejected)} / ${formatNumber(openExecution.timedOut)}`" :tone="warningToneForFields(openExecution, ['rejected', 'timedOut'])" />
        </div>
      </section>

      <section class="execution-metrics-group" data-testid="execution-metrics-outbox">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>发布 Outbox</h3>
            <p>Redis 发布消息的待投递、重试和死信状态</p>
          </div>
          <el-tag size="small" :type="outboxStatusType">{{ outboxStatusLabel }}</el-tag>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="待投递" :value="formatNumber(publishOutbox.pending)" />
          <metric-item label="投递中" :value="formatNumber(publishOutbox.delivering)" />
          <metric-item label="重试中" :value="formatNumber(publishOutbox.retrying)" :tone="warningTone(publishOutbox.retrying)" />
          <metric-item label="死信" :value="formatNumber(publishOutbox.deadLetter)" :tone="warningTone(publishOutbox.deadLetter)" />
        </div>
      </section>

      <section class="execution-metrics-group" data-testid="execution-metrics-circuit-breakers">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>外数熔断器</h3>
            <p>当前节点已登记的 API 熔断状态</p>
          </div>
          <el-tag size="small" :type="sectionStatusType(externalCircuitBreakers, ['open', 'halfOpen'])">
            {{ sectionStatusLabel(externalCircuitBreakers, ['open', 'halfOpen']) }}
          </el-tag>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="已登记 API" :value="formatNumber(externalCircuitBreakers.registeredApis)" />
          <metric-item label="熔断打开" :value="formatNumber(externalCircuitBreakers.open)" :tone="warningTone(externalCircuitBreakers.open)" />
          <metric-item label="半开探测" :value="formatNumber(externalCircuitBreakers.halfOpen)" :tone="warningTone(externalCircuitBreakers.halfOpen)" />
          <metric-item label="正常关闭" :value="formatNumber(externalCircuitBreakers.closed)" :tone="warningTone(externalCircuitBreakers.closed)" />
        </div>
      </section>

      <section class="execution-metrics-group" data-testid="execution-metrics-warmup">
        <div class="execution-metrics-group__heading">
          <div>
            <h3>规则预热</h3>
            <p>发布规则加载状态和失败明细数量</p>
          </div>
          <div class="execution-metrics-group__heading-actions">
            <el-tag size="small" :type="warmupType(ruleWarmup.state)">
              {{ warmupLabel(ruleWarmup.state) }}
            </el-tag>
            <el-button
              v-if="canRetryRuleWarmup && (ruleWarmup.state === 'FAILED' || Number(ruleWarmup.failureCount) > 0)"
              v-permission="'rule:submit'"
              data-testid="execution-metrics-warmup-retry"
              size="small"
              :loading="warmupRetrying"
              @click="retryWarmup"
            >重新验证预热</el-button>
          </div>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="预热目标" :value="formatNumber(ruleWarmup.targetCount)" />
          <metric-item label="已完成" :value="formatNumber(ruleWarmup.preparedCount)" />
          <metric-item label="失败数" :value="formatNumber(ruleWarmup.failureCount)" :tone="warningTone(ruleWarmup.failureCount)" />
          <metric-item label="完成进度" :value="warmupProgress" :tone="ruleWarmup.state === 'FAILED' ? 'warning' : ''" />
        </div>
        <div v-if="warmupFailures.length" class="execution-metrics-failures" data-testid="execution-metrics-warmup-failures">
          <div v-for="failure in warmupFailures" :key="`${failure.definitionId}-${failure.version}-${failure.revisionId || ''}`" class="execution-metrics-failure">
            <strong>{{ failureTitle(failure) }}</strong>
            <span>规则 {{ failure.definitionId }} / 版本 {{ failure.version }}</span>
            <span>{{ failure.nextAction || '修复后重新发布并点击预热重试' }}</span>
            <small>{{ failure.message || '未知预热错误' }}</small>
          </div>
        </div>
        <p v-if="ruleWarmup.detailsRestricted" class="execution-metrics-panel__updated">当前账号仅可查看节点汇总，具体失败资源需要全部项目的规则查看权限。</p>
      </section>
      <section v-if="metrics.onnxWarmup" class="execution-metrics-group" data-testid="execution-metrics-model-warmup">
        <div class="execution-metrics-group__heading">
          <div><h3>模型预热</h3><p>只验证已发布且开启预加载的 ONNX 模型；失败时就绪检查保持关闭。</p></div>
          <div class="execution-metrics-group__heading-actions">
            <el-tag size="small" :type="warmupType(modelWarmup.state)">{{ warmupLabel(modelWarmup.state) }}</el-tag>
            <el-button v-if="canRetryModelWarmup && (modelWarmup.state === 'FAILED' || Number(modelWarmup.failureCount) > 0)" data-testid="execution-metrics-model-warmup-retry" size="small" :loading="modelWarmupRetrying" :disabled="warmupRetrying" @click="retryModels">重新验证模型预热</el-button>
          </div>
        </div>
        <div class="execution-metrics-grid">
          <metric-item label="模型目标" :value="formatNumber(modelWarmup.targetCount)" />
          <metric-item label="预热成功" :value="formatNumber(modelWarmup.successCount)" />
          <metric-item label="预热失败" :value="formatNumber(modelWarmup.failureCount)" :tone="warningTone(modelWarmup.failureCount)" />
          <metric-item label="CPU 回退" :value="formatNumber(modelWarmup.cpuFallbackCount)" :tone="warningTone(modelWarmup.cpuFallbackCount)" />
        </div>
        <p v-if="modelWarmup.detailsRestricted" class="execution-metrics-panel__updated">当前账号仅可查看节点汇总，具体失败资源需要全部项目的模型查看权限。</p>
        <p v-else-if="modelWarmup.state === 'FAILED'">检查发布快照、数据库连接和模型运行库后，可重新验证当前节点，无需修改已发布模型。</p>
        <div v-for="failure in modelWarmup.failures || []" :key="`${failure.modelId}-${failure.version}`" class="execution-metrics-failure">
          <strong>{{ failure.modelName }}</strong><span>模型 {{ failure.modelId }} / 版本 {{ failure.version }}</span><small>{{ failure.errorType }}</small>
        </div>
      </section>
    </div>
  </article>
</template>

<script>
import { getExecutionMetrics, retryRuleWarmup, retryModelWarmup } from '@/api/runtimeLog'
import { hasUnrestrictedPermissions } from '@/security/permissionState'
import ExecutionMetricsItem from './ExecutionMetricsItem.vue'

export default {
  name: 'ExecutionMetricsPanel',
  components: { MetricItem: ExecutionMetricsItem },
  data() {
    return {
      metrics: {},
      loading: false,
      loaded: false,
      error: '',
      updatedAt: '',
      snapshotStatus: 'UNKNOWN',
      warmupRetrying: false,
      modelWarmupRetrying: false,
      metricsRequestId: 0,
    }
  },
  computed: {
    canRetryRuleWarmup() { return hasUnrestrictedPermissions('rule:view', 'rule:submit') },
    canRetryModelWarmup() { return hasUnrestrictedPermissions('model:view', 'model:edit') },
    modelWarmup() { return this.metrics.onnxWarmup || {} },
    persistence() { return this.metrics.persistence || {} },
    journal() { return this.persistence.journal || {} },
    journalStatusLabel() {
      if (this.journal.status === 'READY') return this.journal.available === true ? '可写入' : '不可用'
      return {
        NOT_INITIALIZED: '未初始化',
        DISABLED: '未启用',
        OWNERSHIP_CONFLICT: '目录被占用',
        INITIALIZATION_FAILED: '初始化失败',
        RECOVERY_REQUIRED: '需要恢复',
        WRITE_FAILED: '写入失败',
        REPLACEMENT_FAILED: '恢复进度保存失败',
        CLOSED: '已关闭',
      }[this.journal.status] || '未知'
    },
    journalStatusType() {
      if (this.journal.status === 'READY' && this.journal.available === true) return 'success'
      if (['OWNERSHIP_CONFLICT', 'INITIALIZATION_FAILED', 'RECOVERY_REQUIRED', 'WRITE_FAILED', 'REPLACEMENT_FAILED', 'READY'].includes(this.journal.status)) return 'warning'
      return 'info'
    },
    journalStatusHint() {
      return {
        OWNERSHIP_CONFLICT: '此目录已被其他实例占用。请为每个节点配置独立的本地目录，不要删除正在使用的锁文件。',
        INITIALIZATION_FAILED: '本地恢复存储暂不可用。请检查目录权限、磁盘空间和挂载状态，并保留已有文件。',
        RECOVERY_REQUIRED: '无法确认本地日志或恢复进度的完整性。不要删除已有文件，请保留现场并检查恢复状态；待恢复总数暂无法确认。',
        WRITE_FAILED: '本地写入未能确认完成，已停止继续追加。已有文件已保留，请检查磁盘和目录状态，由运维排障后重启当前节点；待恢复总数暂无法确认。',
        REPLACEMENT_FAILED: '恢复进度未能安全保存，已有文件已保留。请检查磁盘和文件系统状态，由运维排障后重启当前节点，不要手工清空日志。',
      }[this.journal.status] || '本地恢复日志暂不可写，请检查当前节点的运行状态。'
    },
    journalPendingLabel() {
      if (this.journal.status !== 'READY' || this.journal.available !== true || !this.hasMetricValue(this.journal.pending)) return '未知'
      if (this.journal.pendingTruncated && Number(this.journal.pending) === 0) return '未知'
      return `${this.journal.pendingTruncated ? '至少 ' : ''}${this.formatNumber(this.journal.pending)}`
    },
    instanceLabel() { return this.metrics.instanceId || '' },
    sourceResolution() { return this.metrics.sourceResolution || {} },
    openExecution() { return this.metrics.openExecution || {} },
    publishOutbox() { return this.metrics.publishOutbox || {} },
    executionPersistenceOutbox() { return this.metrics.executionPersistenceOutbox || {} },
    externalCircuitBreakers() { return this.metrics.externalCircuitBreakers || {} },
    ruleWarmup() { return this.metrics.ruleWarmup || {} },
    outboxStatusLabel() {
      if (!this.hasMetricValue(this.publishOutbox.deadLetter)) return '未知'
      return Number(this.publishOutbox.deadLetter) > 0 ? '有死信' : '正常'
    },
    outboxStatusType() {
      if (!this.hasMetricValue(this.publishOutbox.deadLetter)) return 'info'
      return Number(this.publishOutbox.deadLetter) > 0 ? 'warning' : 'success'
    },
    snapshotStatusLabel() {
      return {
        CURRENT: '当前快照',
        STALE: '最近快照',
        UNKNOWN: '暂无快照'
      }[this.snapshotStatus] || '暂无快照'
    },
    warmupProgress() {
      if (!this.hasValue(this.ruleWarmup.state)
        || !this.hasMetricValue(this.ruleWarmup.targetCount)
        || !this.hasMetricValue(this.ruleWarmup.preparedCount)) return '未知'
      const target = Number(this.ruleWarmup.targetCount)
      if (!target) return this.ruleWarmup.state === 'READY' ? '100%' : '0%'
      return `${Math.min(100, (Number(this.ruleWarmup.preparedCount || 0) / target) * 100).toFixed(1)}%`
    },
    persistenceDetail() {
      const utilizationValue = Number(this.persistence.queueUtilization)
      const utilization = this.hasValue(this.persistence.queueUtilization) && Number.isFinite(utilizationValue)
        ? `${(utilizationValue * 100).toFixed(1)}%`
        : '利用率未知'
      return `${utilization} · 溢出策略 ${this.overflowStrategyLabel(this.persistence.overflowStrategy)}`
    },
    warmupFailures() {
      return Array.isArray(this.ruleWarmup.failures) ? this.ruleWarmup.failures.slice(0, 3) : []
    },
  },
  mounted() {
    this.loadMetrics()
  },
  beforeUnmount() { this.metricsRequestId++ },
  methods: {
    async loadMetrics() {
      if (this.warmupRetrying || this.modelWarmupRetrying) return
      const requestId = ++this.metricsRequestId
      this.loading = true
      this.error = ''
      try {
        const response = await getExecutionMetrics()
        if (requestId !== this.metricsRequestId) return
        if (!this.isValidSnapshot(response && response.data)) {
          this.error = '执行指标快照不完整，请重试'
          this.snapshotStatus = this.loaded ? 'STALE' : 'UNKNOWN'
          return
        }
        this.metrics = response.data
        this.loaded = true
        this.snapshotStatus = 'CURRENT'
        this.updatedAt = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      } catch (error) {
        if (requestId !== this.metricsRequestId) return
        this.error = error.message || '执行指标加载失败'
        this.snapshotStatus = this.loaded ? 'STALE' : 'UNKNOWN'
      } finally {
        if (requestId === this.metricsRequestId) this.loading = false
      }
    },
    async retryWarmup() {
      if (this.warmupRetrying || this.modelWarmupRetrying || !this.canRetryRuleWarmup) return
      const requestId = ++this.metricsRequestId
      this.loading = false
      this.warmupRetrying = true
      this.error = ''
      try {
        const response = await retryRuleWarmup()
        if (requestId !== this.metricsRequestId) return
        const warmup = response && response.data
        if (warmup && typeof warmup === 'object' && !Array.isArray(warmup) && warmup.state) {
          this.metrics = { ...this.metrics, ruleWarmup: warmup }
          this.loaded = true
          this.snapshotStatus = 'CURRENT'
          this.updatedAt = new Date().toLocaleTimeString('zh-CN', { hour12: false })
        } else {
          this.warmupRetrying = false
          await this.loadMetrics()
        }
      } catch (error) {
        if (requestId !== this.metricsRequestId) return
        this.error = error.message || '规则预热重试失败'
        this.snapshotStatus = this.loaded ? 'STALE' : 'UNKNOWN'
      } finally {
        this.warmupRetrying = false
      }
    },
    async retryModels() {
      if (this.warmupRetrying || this.modelWarmupRetrying || !this.canRetryModelWarmup) return
      const requestId = ++this.metricsRequestId
      this.loading = false
      this.modelWarmupRetrying = true
      this.error = ''
      try {
        const response = await retryModelWarmup()
        if (requestId !== this.metricsRequestId) return
        const warmup = response?.data
        if (!warmup || typeof warmup !== 'object' || !warmup.state) throw new Error('模型预热响应不完整，请刷新状态')
        this.metrics = { ...this.metrics, onnxWarmup: warmup }
        this.loaded = true
        this.snapshotStatus = 'CURRENT'
        this.updatedAt = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      } catch (error) {
        if (requestId !== this.metricsRequestId) return
        this.error = error.message || '模型预热重试失败，请刷新状态'
        this.snapshotStatus = this.loaded ? 'STALE' : 'UNKNOWN'
      } finally { this.modelWarmupRetrying = false }
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ''
    },
    isValidSnapshot(snapshot) {
      if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) return false
      return ['persistence', 'sourceResolution', 'openExecution', 'externalCircuitBreakers', 'ruleWarmup']
        .every(section => snapshot[section] && typeof snapshot[section] === 'object' && !Array.isArray(snapshot[section]))
    },
    formatNumber(value) {
      if (!this.hasValue(value)) return '未知'
      const number = Number(value)
      return Number.isFinite(number) ? number.toLocaleString('zh-CN') : '未知'
    },
    formatMs(value) {
      if (!this.hasValue(value)) return '未知'
      const number = Number(value)
      if (!Number.isFinite(number)) return '未知'
      return `${number.toFixed(number < 10 ? 2 : 0)} ms`
    },
    formatBytes(value) {
      if (!this.hasMetricValue(value) || Number(value) < 0) return '未知'
      let size = Number(value)
      const units = ['B', 'KB', 'MB', 'GB', 'TB']
      let unit = 0
      while (size >= 1024 && unit < units.length - 1) {
        size /= 1024
        unit++
      }
      return `${unit ? size.toFixed(1) : size} ${units[unit]}`
    },
    queueValue(pool) {
      if (!this.hasMetricValue(pool.queueDepth) || !this.hasMetricValue(pool.queueCapacity)) return '未知'
      return `${this.formatNumber(pool.queueDepth)} / ${this.formatNumber(pool.queueCapacity)}`
    },
    executorDetail(pool) {
      if (!this.hasMetricValue(pool.active)
        || (!this.hasMetricValue(pool.parallelism) && !this.hasMetricValue(pool.poolSize))) {
        return '并发信息未知'
      }
      const active = Number(pool.active || 0)
      const parallelism = Number(pool.parallelism || pool.poolSize || 0)
      return parallelism ? `活跃 ${this.formatNumber(active)} / 并行 ${this.formatNumber(parallelism)}` : ''
    },
    warningTone(value) {
      if (!this.hasMetricValue(value)) return 'info'
      return Number(value) > 0 ? 'warning' : 'success'
    },
    warningType(value) {
      if (!this.hasMetricValue(value)) return 'info'
      return Number(value) > 0 ? 'warning' : 'success'
    },
    hasMetricValue(value) {
      return this.hasValue(value) && Number.isFinite(Number(value))
    },
    warningToneForFields(section, keys) {
      if (!keys.every(key => this.hasMetricValue(section[key]))) return 'info'
      return keys.some(key => Number(section[key]) > 0) ? 'warning' : 'success'
    },
    sectionStatusLabel(section, keys) {
      if (!keys.every(key => this.hasMetricValue(section[key]))) return '未知'
      return keys.some(key => Number(section[key]) > 0)
        ? (keys.includes('open') ? '存在保护状态' : '需要关注')
        : '正常'
    },
    sectionStatusType(section, keys) {
      if (!keys.every(key => this.hasMetricValue(section[key]))) return 'info'
      return keys.some(key => Number(section[key]) > 0) ? 'warning' : 'success'
    },
    overflowStrategyLabel(strategy) {
      if (!this.hasValue(strategy)) return '未知'
      return {
        SYNC_FALLBACK: '同步回退',
        JOURNAL_THEN_SYNC_FALLBACK: '本地写前日志后同步回退',
        DURABLE_OUTBOX_REPLAY: '持久化队列重放'
      }[strategy] || '未知'
    },
    warmupType(state) {
      if (state === 'FAILED' || state === 'WARMING') return 'warning'
      if (state === 'READY') return 'success'
      return 'info'
    },
    warmupLabel(state) {
      return { READY: '已就绪', WARMING: '预热中', FAILED: '预热失败', NOT_STARTED: '未开始' }[state] || '未知'
    },
    failureTitle(failure) {
      return failure.title || {
        FUNCTION_NOT_BOUND: '脚本函数未绑定',
        INVALID_SCRIPT: '发布脚本无法编译',
        ARTIFACT_INVALID: '发布制品不可用'
      }[failure.code] || '规则预热失败'
    }
  }
}
</script>

<style scoped>
.execution-metrics-panel {
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-bg-color);
  box-shadow: var(--tianshu-shadow-sm);
}

.execution-metrics-panel__header,
.execution-metrics-group__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.execution-metrics-panel__header {
  margin-bottom: 16px;
}

.execution-metrics-panel h2,
.execution-metrics-panel h3 {
  margin: 0;
  color: var(--el-text-color-primary);
}

.execution-metrics-panel h2 { font-size: 17px; }
.execution-metrics-panel h3 { font-size: 15px; }

.execution-metrics-panel p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.execution-metrics-panel p [data-testid="execution-metrics-instance"] {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.execution-metrics-panel__actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 10px;
}

.execution-metrics-panel__updated {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.execution-metrics-panel__error { margin-bottom: 14px; }

.execution-metrics-journal-details {
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}
.execution-metrics-journal-details summary { cursor: pointer; }

.execution-metrics-panel__state {
  display: grid;
  min-height: 120px;
  place-content: center;
  color: var(--el-text-color-secondary);
}

.execution-metrics-panel__content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.execution-metrics-group {
  min-width: 0;
  padding: 15px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--tianshu-bg-subtle);
}

.execution-metrics-group__heading { margin-bottom: 12px; }

.execution-metrics-group__heading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.execution-metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.execution-metrics-failures {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.execution-metrics-failure {
  display: grid;
  gap: 3px;
  padding: 9px 10px;
  border-left: 3px solid var(--el-color-warning);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.execution-metrics-failure strong { color: var(--el-text-color-primary); }
.execution-metrics-failure small { color: var(--el-text-color-placeholder); }

@media (max-width: 760px) {
  .execution-metrics-panel__header,
  .execution-metrics-group__heading { flex-direction: column; }
  .execution-metrics-panel__content { grid-template-columns: 1fr; }
}
</style>
