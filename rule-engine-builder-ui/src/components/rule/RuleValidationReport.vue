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
      </div>
    </div>
    <div v-if="warnings.length" class="issue-group">
      <div class="issue-title">提醒</div>
      <div v-for="(issue, index) in warnings" :key="`warning-${index}`" class="issue-row">
        <strong>{{ issueTitle(issue, '校验提醒') }}</strong>
        <span>{{ issue.message }}</span>
      </div>
    </div>
    <el-empty v-if="report.valid && !warnings.length" description="格式、Schema 与依赖校验均已通过" />
  </section>
</template>

<script>
export default {
  name: 'RuleValidationReport',
  props: {
    report: {
      type: Object,
      default: () => ({ valid: true, errors: [], warnings: [] })
    }
  },
  computed: {
    errors() { return this.report.errors || [] },
    warnings() { return this.report.warnings || [] }
  },
  methods: {
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
.summary-label, .issue-title { color: #6b7280; font-size: 12px; font-weight: 600; letter-spacing: .04em; }
.issue-group { border-left: 4px solid #d6a23a; background: #fffbeb; padding: 12px 16px; }
.issue-group--error { border-left-color: #d34a4a; background: #fff2f2; }
.issue-row { display: grid; grid-template-columns: minmax(140px, auto) 1fr auto; gap: 12px; margin-top: 8px; font-size: 13px; line-height: 1.5; }
.issue-row code { color: #6b7280; }
</style>
