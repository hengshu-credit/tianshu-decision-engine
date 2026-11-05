<template>
  <el-card class="lifecycle-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <span class="eyebrow">规则生命周期</span>
          <div class="state-line">
            <strong>修订 v{{ lifecycleRevision.revisionNo || '—' }}</strong>
            <el-tag :type="stateType" size="small">{{ stateLabel }}</el-tag>
          </div>
        </div>
        <div v-if="onlineArtifactDigest" class="online-artifact">
          <span>线上制品</span>
          <code>{{ shortDigest(onlineArtifactDigest) }}</code>
        </div>
      </div>
    </template>

    <div class="release-overview" role="region" aria-label="发布就绪概览">
      <section><span>当前修订</span><strong>v{{ lifecycleRevision.revisionNo || '—' }} · {{ stateLabel }}</strong><small>修订 ID {{ lifecycleRevision.id }} · 内容锁版本 {{ lifecycleRevision.lockVersion ?? '—' }}</small></section>
      <section><span>线上版本</span><strong>{{ onlineRevision.id ? `修订 v${onlineRevision.revisionNo}` : '暂无已发布修订' }}</strong><small>{{ onlineRevision.publishTime || '保存草稿不会更新线上版本' }}</small><code v-if="onlineArtifactDigest">{{ shortDigest(onlineArtifactDigest) }}</code></section>
      <section><span>依赖变更</span><strong>{{ freshValidation ? (dependencyIssues.length ? `${dependencyIssues.length} 项需要核对` : '本次预检未报告依赖问题') : '尚未取得当前修订的预检结果' }}</strong><ul v-if="freshValidation && dependencyIssues.length"><li v-for="(issue, i) in dependencyIssues" :key="i">{{ issue.message }}</li></ul><small v-if="freshValidation && validationReport.dependencyDigest">当前依赖摘要：{{ shortDigest(validationReport.dependencyDigest) }}</small></section>
      <section><span>回归证据</span><strong>{{ regression ? regression.message : '尚未复核，不代表通过' }}</strong><small v-if="regression">启用用例 {{ regression.requiredCount }} · 有效通过 {{ regression.passedCount }} · 过期 {{ regression.staleCount }} · 失败 {{ regression.failedCount }} · 缺失 {{ regression.missingCount }}</small><el-button link type="primary" @click="$emit('show-regression')">查看回归证据</el-button></section>
    </div>

    <el-alert
      v-if="state === 'REVIEW' && approvalRequestId"
      title="修订已进入统一审批中心并被冻结；请在审批详情中完成通过、驳回或取消。"
      type="info"
      :closable="false"
    />
    <el-alert v-else-if="state === 'REVIEW'" title="评审中的修订已冻结；驳回后本次草稿终止，规则继续使用当前生效版本。" type="info" :closable="false" />
    <el-alert v-if="state === 'REJECTED'" title="本次申请已驳回并终止；审批记录已保留，规则继续使用当前生效版本。" type="warning" :closable="false" />
    <el-alert v-if="state === 'APPROVED'" title="制品已固化，可发布；后续源模型变化不会修改该制品。" type="success" :closable="false" />

    <div v-if="validationReport && validationReport.breakingSchemaChange && state === 'REVIEW'" class="risk-reason">
      <label>风险接受原因</label>
      <el-input v-model="forceReason" type="textarea" :rows="2" placeholder="说明兼容窗口、调用方通知或回滚安排" />
    </div>
    <div v-if="state === 'REVIEW' && !approvalRequestId" class="risk-reason">
      <label>评审意见 / 驳回原因</label>
      <el-input v-model="comment" type="textarea" :rows="2" placeholder="驳回申请时必须填写原因" />
    </div>

    <div class="action-row">
      <el-button v-if="lifecycleRevision.id" data-testid="view-design" :disabled="actionLoading" @click="emitAction('view-design')">{{ state === 'DRAFT' ? '进入设计' : '查看设计' }}</el-button>
      <el-button v-if="state === 'DRAFT'" data-testid="preflight" @click="emitAction('preflight')">发布前校验</el-button>
      <el-button v-if="state === 'DRAFT'" data-testid="submit" type="primary" @click="emitAction('submit')">提交评审</el-button>
      <el-button v-if="state === 'REVIEW' && approvalRequestId" data-testid="go-approval" type="primary" @click="emitAction('go-approval')">前往统一审批</el-button>
      <el-button v-if="state === 'REVIEW' && !approvalRequestId" data-testid="reject" :disabled="!comment.trim()" @click="emitAction('reject')">驳回本次申请</el-button>
      <el-button v-if="state === 'REVIEW' && !approvalRequestId" data-testid="approve" type="primary" :disabled="approvalReasonMissing" @click="emitAction('approve')">批准并固化制品</el-button>
      <el-button v-if="state === 'APPROVED'" data-testid="publish" type="primary" @click="emitAction('publish')">发布修订 v{{ lifecycleRevision.revisionNo }}</el-button>
      <el-button v-if="['APPROVED', 'PUBLISHED', 'OFFLINE'].includes(state)" data-testid="edit-design" :loading="actionLoading" :disabled="actionLoading" @click="emitAction('edit-design')">基于此修订编辑</el-button>
      <el-button v-if="['APPROVED', 'PUBLISHED', 'OFFLINE'].includes(state) && lifecycleRevision.artifactId" data-testid="download" @click="emitAction('download')">下载制品</el-button>
      <el-button v-if="['APPROVED', 'PUBLISHED', 'OFFLINE'].includes(state) && lifecycleRevision.artifactId" data-testid="migration-download" @click="emitAction('migration-download')">下载当前配置迁移包</el-button>
      <el-button v-if="state === 'PUBLISHED'" data-testid="offline" @click="emitAction('offline')">下线</el-button>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'RuleLifecyclePanel',
  props: {
    lifecycleRevision: { type: Object, default: () => ({ state: 'DRAFT' }) },
    validationReport: { type: Object, default: null },
    onlineArtifactDigest: { type: String, default: '' },
    onlineRevision: { type: Object, default: () => ({}) },
    actionLoading: { type: Boolean, default: false },
    approvalRequestId: { type: Number, default: null }
  },
  emits: ['action', 'show-regression'],
  data() { return { forceReason: '', comment: '' } },
  computed: {
    freshValidation() { return this.validationReport && String(this.validationReport.revisionId) === String(this.lifecycleRevision.id) && this.validationReport.lockVersion != null && String(this.validationReport.lockVersion) === String(this.lifecycleRevision.lockVersion) },
    regression() { return this.freshValidation ? this.validationReport.regression : null },
    dependencyIssues() { return [...(this.validationReport?.errors || []), ...(this.validationReport?.warnings || [])].filter(issue => /DEPENDENCY|REFERENCE|MODEL_VERSION/.test(issue.code || '')) },
    state() { return this.lifecycleRevision.state || 'DRAFT' },
    stateLabel() { return ({ DRAFT: '草稿', REVIEW: '评审中', REJECTED: '已驳回', APPROVED: '已批准', PUBLISHED: '已发布', OFFLINE: '已下线' })[this.state] || this.state },
    stateType() { return ({ DRAFT: 'info', REVIEW: 'warning', REJECTED: 'danger', APPROVED: 'success', PUBLISHED: 'success', OFFLINE: 'info' })[this.state] || 'info' },
    approvalReasonMissing() { return Boolean(this.validationReport && this.validationReport.breakingSchemaChange && !this.forceReason.trim()) }
  },
  methods: {
    emitAction(action) { this.$emit('action', { action, comment: this.comment, forcePublishReason: this.forceReason.trim() }) },
    shortDigest(value) { return value ? `${value.slice(0, 10)}…${value.slice(-8)}` : '—' }
  }
}
</script>

<style scoped>
.lifecycle-panel { border-top: 4px solid var(--el-color-primary); }
.panel-header, .state-line, .action-row { display: flex; align-items: center; gap: 12px; }
.panel-header { justify-content: space-between; }
.eyebrow, .online-artifact span { color: var(--tianshu-text-secondary); font-size: 12px; font-weight: 600; letter-spacing: .04em; }
.state-line { margin-top: 4px; }
.online-artifact { display: grid; justify-items: end; gap: 4px; }
.online-artifact code { color: var(--tianshu-text-primary); }
.release-overview { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); gap: 12px; margin-bottom: 16px; }
.release-overview section { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--tianshu-bg-soft); border-left: 3px solid var(--tianshu-primary-text); overflow-wrap: anywhere; }
.release-overview span, .release-overview small { color: var(--tianshu-text-secondary); }
.release-overview strong { font-size: 14px; }
.release-overview ul { margin: 0; padding-left: 16px; font-size: 12px; }
.risk-reason { display: grid; gap: 8px; margin-top: 16px; max-width: 720px; }
.risk-reason label { color: #4b5563; font-size: 13px; font-weight: 600; }
.action-row { flex-wrap: wrap; margin-top: 16px; }
</style>
