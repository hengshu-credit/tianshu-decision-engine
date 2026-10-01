<template>
  <section class="resource-preflight-panel" data-testid="resource-preflight-panel" aria-label="配置预检">
    <div class="resource-preflight-heading">
      <div>
        <div class="resource-preflight-title">配置预检</div>
        <div class="resource-preflight-subtitle">
          只检查已保存生效配置，不读取当前未保存草稿，也不代表真实网络连接或审批通过。
        </div>
      </div>
      <el-button
        v-if="hasResourceId"
        data-action="run-resource-preflight"
        class="resource-preflight-button"
        size="small"
        type="primary"
        :loading="loading"
        @click="runCheck"
      >检查已保存配置</el-button>
    </div>

    <el-alert
      v-if="!hasResourceId"
      type="info"
      :closable="false"
      show-icon
      title="新建资源需要先保存并完成审批，之后才能检查已保存配置。"
    />
    <el-alert
      v-else-if="requestError"
      type="error"
      :closable="false"
      show-icon
      title="配置检查失败，未生成正常报告"
      :description="requestError"
    >
      <el-button size="small" data-action="retry-resource-preflight" @click="runCheck">重试</el-button>
    </el-alert>
    <el-alert
      v-if="isStale"
      type="warning"
      :closable="false"
      show-icon
      title="配置已发生变化，上次报告已过期"
      description="请先保存并完成审批，再重新检查已保存配置。"
    />
    <el-alert
      v-if="report"
      type="info"
      :closable="false"
      show-icon
      :title="report.valid ? '已保存配置检查通过' : '已保存配置存在阻断项'"
      :description="reportDescription"
    />
    <rule-validation-report
      v-if="report"
      :report="report"
      :locatable="false"
    />
  </section>
</template>

<script>
import { ElMessage } from 'element-plus'
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'
import { getResourcePreflight } from '@/api/preflight'

const MAX_ATTEMPTS = 3

export default {
  name: 'ResourcePreflightPanel',
  components: { RuleValidationReport },
  props: {
    resourceType: { type: String, required: true },
    resourceId: { type: [String, Number], default: null },
    configSignature: { type: String, default: '' }
  },
  emits: ['checked'],
  data() {
    return {
      loading: false,
      report: null,
      requestError: '',
      checkedResourceId: null,
      checkedResourceType: '',
      checkedConfigSignature: ''
    }
  },
  computed: {
    hasResourceId() {
      return this.resourceId !== null && this.resourceId !== undefined && this.resourceId !== ''
    },
    isStale() {
      if (!this.report) return false
      return String(this.checkedResourceId) !== String(this.resourceId) ||
        this.checkedResourceType !== String(this.resourceType || '').toUpperCase() ||
        this.checkedConfigSignature !== String(this.configSignature || '')
    },
    reportDescription() {
      const scope = this.report.checkScope || 'SAVED_CONFIGURATION'
      const checkedAt = this.report.checkedAt ? `检查时间：${this.report.checkedAt}` : '已记录检查时间'
      return `检查范围：${scope}。${checkedAt}。此结果仅表示保存配置检查结果。`
    }
  },
  watch: {
    resourceId(value, oldValue) {
      if (String(value ?? '') === String(oldValue ?? '')) return
      this.resetReport()
    },
    resourceType(value, oldValue) {
      if (String(value || '').toUpperCase() === String(oldValue || '').toUpperCase()) return
      this.resetReport()
    },
    configSignature(value, oldValue) {
      if (!this.report || String(value || '') === String(oldValue || '')) return
      // 让用户看到旧报告已经失效，但保留它用于对照，避免误以为请求失败。
      this.requestError = ''
    }
  },
  methods: {
    resetReport() {
      this.report = null
      this.requestError = ''
      this.checkedResourceId = null
      this.checkedResourceType = ''
      this.checkedConfigSignature = ''
    },
    normalizeReport(response) {
      const payload = response && response.data !== undefined ? response.data : response
      const report = payload && payload.data && !payload.resourceType ? payload.data : payload
      if (!report || typeof report !== 'object' || !report.resourceType || !Array.isArray(report.errors) || !Array.isArray(report.warnings)) {
        throw new Error('服务未返回完整的配置检查报告')
      }
      return {
        ...report,
        valid: report.valid === true,
        checkedAt: report.checkedAt || new Date().toISOString(),
        checkScope: report.checkScope || 'SAVED_CONFIGURATION'
      }
    },
    async runCheck() {
      if (!this.hasResourceId) {
        ElMessage.warning('请先保存并完成审批，再检查已保存配置')
        return
      }
      this.loading = true
      this.requestError = ''
      let lastError = null
      try {
        for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
          try {
            const response = await getResourcePreflight(this.resourceType, this.resourceId)
            const report = this.normalizeReport(response)
            this.report = report
            this.checkedResourceId = this.resourceId
            this.checkedResourceType = String(this.resourceType || '').toUpperCase()
            this.checkedConfigSignature = String(this.configSignature || '')
            this.$emit('checked', report)
            return report
          } catch (error) {
            lastError = error
            if (attempt < MAX_ATTEMPTS) await this.waitBeforeRetry(attempt)
          }
        }
        // 请求失败时不创建 valid=true 的兜底报告，避免页面误显示正常。
        this.report = null
        this.requestError = lastError && lastError.message ? lastError.message : '配置检查请求失败，请重试'
        return null
      } finally {
        this.loading = false
      }
    },
    waitBeforeRetry(attempt) {
      return new Promise(resolve => window.setTimeout(resolve, Math.min(attempt * 200, 600)))
    }
  }
}
</script>

<style scoped>
.resource-preflight-panel {
  display: grid;
  gap: 12px;
  margin: 14px 0;
  padding: 14px 16px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-soft);
}
.resource-preflight-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.resource-preflight-title { color: var(--tianshu-text-primary); font-weight: 700; }
.resource-preflight-subtitle { margin-top: 4px; color: var(--tianshu-text-tertiary); font-size: 12px; line-height: 1.6; }
.resource-preflight-panel :deep(.resource-preflight-button.is-disabled) {
  background-color: var(--el-color-primary-dark-1) !important;
  border-color: var(--el-color-primary-dark-1) !important;
  color: #fff !important;
  opacity: 0.72;
}
@media (max-width: 720px) {
  .resource-preflight-heading { align-items: stretch; flex-direction: column; }
}
</style>

