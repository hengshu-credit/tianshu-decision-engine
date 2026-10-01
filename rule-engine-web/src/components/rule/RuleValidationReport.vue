<template>
  <section class="validation-report" aria-label="发布前校验报告">
    <div class="validation-summary">
      <div>
        <span class="summary-label">校验结果</span>
        <strong>{{ report.valid ? '可继续' : '存在阻断项' }}</strong>
      </div>
      <el-tag :type="report.valid ? 'success' : 'danger'" size="small">
        {{ errors.length }} 个错误 · {{ warnings.length }} 个提醒
      </el-tag>
    </div>
    <el-alert
      v-if="report.breakingSchemaChange"
      title="检测到破坏性 Schema 变更，批准时必须填写风险接受原因"
      type="warning"
      :closable="false"
      show-icon
    />
    <div v-if="errors.length" class="issue-group issue-group--error">
      <div class="issue-title">阻断项</div>
      <div v-for="(issue, index) in errors" :key="`error-${index}`" class="issue-row">
        <strong>{{ issueTitle(issue, '校验错误') }}</strong>
        <span>{{ issue.message }}</span>
        <code v-if="issue.path">{{ issue.path }}</code>
        <div class="issue-repair">
          <span>{{ issue.nextAction || repairHint(issue) }}</span>
          <el-button v-if="locatable" link type="primary" data-action="locate-issue" @click="$emit('locate', issue)">定位到规则设计</el-button>
        </div>
      </div>
    </div>
    <div v-if="warnings.length" class="issue-group issue-group--warning">
      <div class="issue-title">提醒</div>
      <div v-for="(issue, index) in warnings" :key="`warning-${index}`" class="issue-row">
        <strong>{{ issueTitle(issue, '校验提醒') }}</strong>
        <span>{{ issue.message }}</span>
        <div class="issue-repair">
          <span>{{ issue.nextAction || repairHint(issue) }}</span>
          <el-button v-if="locatable" link type="primary" data-action="locate-issue" @click="$emit('locate', issue)">查看配置位置</el-button>
        </div>
      </div>
    </div>
    <el-empty v-if="report.valid && !warnings.length" description="格式、Schema 与依赖校验均已通过" />
  </section>
</template>

<script>
import { validationRepairHint } from '@/utils/validationIssueLocation'
export default {
  name: 'RuleValidationReport',
  props: {
    locatable: { type: Boolean, default: false },
    report: {
      type: Object,
      default: () => ({ valid: true, errors: [], warnings: [] })
    }
  },
  emits: ['locate'],
  computed: {
    errors() { return this.report.errors || [] },
    warnings() { return this.report.warnings || [] }
  },
  methods: {
    repairHint: validationRepairHint,
    issueTitle(issue, fallback) {
      const labels = {
        MODEL_VERSION_UPDATED: '模型版本已更新',
        BREAKING_SCHEMA_CHANGE: '存在破坏性字段变更',
        DEPENDENCY_CHANGED: '依赖项已变化',
        MISSING_REFERENCE: '存在失效引用'
      }
      return labels[issue && issue.code] || (issue && issue.title) || (issue && issue.code) || fallback
    }
  }
}
</script>

<style scoped>
.validation-report { display: grid; gap: 12px; }
.validation-summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.validation-summary > div { display: grid; gap: 4px; }
.summary-label, .issue-title { color: var(--tianshu-text-tertiary); font-size: 12px; font-weight: 600; letter-spacing: .04em; }
.issue-group { border: 1px solid var(--tianshu-status-warning-border); border-left-width: 4px; border-radius: 4px; background: var(--tianshu-status-warning-bg); padding: 12px 16px; color: var(--tianshu-text-primary); }
.issue-group--warning { border-left-color: var(--tianshu-status-warning-solid); }
.issue-group--error { border-color: var(--tianshu-status-danger-border); border-left-color: var(--tianshu-status-danger-solid); background: var(--tianshu-status-danger-bg); }
.issue-row { display: grid; grid-template-columns: minmax(140px, auto) 1fr auto; gap: 12px; margin-top: 8px; font-size: 13px; line-height: 1.5; }
.issue-row code { color: var(--tianshu-text-tertiary); }
.issue-repair { grid-column: 1 / -1; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; color: var(--tianshu-text-secondary); }
.issue-repair > span { flex: 1; min-width: 200px; }
</style>
