<template>
  <el-card class="lifecycle-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <span class="eyebrow">规则生命周期</span>
          <div class="state-line">
            <strong>修订 v{{ revision.revisionNo || '—' }}</strong>
            <el-tag :type="stateType" size="small">{{ state }}</el-tag>
          </div>
        </div>
        <div v-if="onlineArtifactDigest" class="online-artifact">
          <span>线上制品</span>
          <code>{{ shortDigest(onlineArtifactDigest) }}</code>
        </div>
      </div>
    </template>

    <el-alert v-if="state === 'REVIEW'" title="评审中的修订已冻结；如需修改，请填写原因退回草稿。" type="info" :closable="false" />
    <el-alert v-if="state === 'APPROVED'" title="制品已固化，可发布；后续源模型变化不会修改该制品。" type="success" :closable="false" />

    <div v-if="validationReport && validationReport.breakingSchemaChange && state === 'REVIEW'" class="risk-reason">
      <label>风险接受原因</label>
      <el-input v-model="forceReason" type="textarea" :rows="2" placeholder="说明兼容窗口、调用方通知或回滚安排" />
    </div>
    <div v-if="state === 'REVIEW'" class="risk-reason">
      <label>评审意见 / 退回原因</label>
      <el-input v-model="comment" type="textarea" :rows="2" placeholder="退回草稿时必须填写原因" />
    </div>

    <div class="action-row">
      <el-button v-if="state === 'DRAFT'" data-testid="preflight" @click="emitAction('preflight')">发布前校验</el-button>
      <el-button v-if="state === 'DRAFT'" data-testid="submit" type="primary" @click="emitAction('submit')">提交评审</el-button>
      <el-button v-if="state === 'REVIEW'" data-testid="return" :disabled="!comment.trim()" @click="emitAction('return')">退回草稿</el-button>
      <el-button v-if="state === 'REVIEW'" data-testid="approve" type="primary" :disabled="approvalReasonMissing" @click="emitAction('approve')">批准并固化制品</el-button>
      <el-button v-if="state === 'APPROVED'" data-testid="publish" type="primary" @click="emitAction('publish')">发布制品</el-button>
      <el-button v-if="revision.artifactId" data-testid="download" @click="emitAction('download')">下载制品</el-button>
      <el-button v-if="state === 'PUBLISHED'" data-testid="offline" @click="emitAction('offline')">下线</el-button>
    </div>
  </el-card>
</template>

<script>
export default {
  name: 'RuleLifecyclePanel',
  props: {
    revision: { type: Object, default: () => ({ state: 'DRAFT' }) },
    validationReport: { type: Object, default: null },
    onlineArtifactDigest: { type: String, default: '' }
  },
  emits: ['action'],
  data() { return { forceReason: '', comment: '' } },
  computed: {
    state() { return this.revision.state || 'DRAFT' },
    stateType() { return ({ DRAFT: 'info', REVIEW: 'warning', APPROVED: 'success', PUBLISHED: 'success', OFFLINE: 'info' })[this.state] || 'info' },
    approvalReasonMissing() { return Boolean(this.validationReport && this.validationReport.breakingSchemaChange && !this.forceReason.trim()) }
  },
  methods: {
    emitAction(action) { this.$emit('action', { action, comment: this.comment, forcePublishReason: this.forceReason.trim() }) },
    shortDigest(value) { return value ? `${value.slice(0, 10)}…${value.slice(-8)}` : '—' }
  }
}
</script>

<style scoped>
.lifecycle-panel { border-top: 4px solid #315ca8; }
.panel-header, .state-line, .action-row { display: flex; align-items: center; gap: 12px; }
.panel-header { justify-content: space-between; }
.eyebrow, .online-artifact span { color: #6b7280; font-size: 12px; font-weight: 600; letter-spacing: .04em; }
.state-line { margin-top: 4px; }
.online-artifact { display: grid; justify-items: end; gap: 4px; }
.online-artifact code { color: #334155; }
.risk-reason { display: grid; gap: 8px; margin-top: 16px; max-width: 720px; }
.risk-reason label { color: #4b5563; font-size: 13px; font-weight: 600; }
.action-row { flex-wrap: wrap; margin-top: 16px; }
</style>
