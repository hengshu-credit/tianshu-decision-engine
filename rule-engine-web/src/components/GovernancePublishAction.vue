<template>
  <resource-version-manager v-if="resourceId" ref="versions" :resource-type="resourceType" :resource-id="resourceId" :show-trigger="showVersions" />
  <el-button v-if="showPublish" link type="success" data-action="publish" size="small" :disabled="disabled" :loading="busy" @click="publish">发布</el-button>
  <el-dialog v-model="issueVisible" title="发布前检查未通过" width="min(640px, 100vw)" append-to-body>
    <p>当前草稿尚未提交，请修复以下问题后重新发布。</p>
    <ul><li v-for="(issue, index) in preflightErrors" :key="index">{{ issue.message }}<small v-if="issue.referencePath">（{{ issue.referencePath }}）</small></li></ul>
    <template #footer><el-button @click="issueVisible = false">关闭</el-button><el-button v-if="canViewApproval" type="primary" @click="openPreflight">查看预检详情</el-button></template>
  </el-dialog>
</template>
<script>
import { getWorkspaceDraft, preflightGovernanceRequest, submitGovernanceRequest } from '@/api/governance'
import { watchResourceState } from '@/utils/workspaceDraftState'
import { hasPermission } from '@/security/permissionState'
import { resourceActionPermission } from '@/security/pickerAccess'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'

export default {
  name: 'GovernancePublishAction',
  components: { ResourceVersionManager },
  props: { resourceType: { type: String, required: true }, resourceId: { type: [Number, String], default: null }, showVersions: { type: Boolean, default: true } },
  data: () => ({ state: {}, release: null, busy: false, sequence: 0, issueVisible: false, preflightErrors: [], checkedRequestId: null }),
  computed: {
    canViewApproval() { return hasPermission('approval:view', this.permissionScope) },
    permissionScope() { return this.state.ready && Object.hasOwn(this.state, 'projectId') ? { projectId: this.state.projectId } : undefined },
    canEdit() { return /^[1-9]\d*$/.test(String(this.resourceId)) && hasPermission(resourceActionPermission(this.resourceType, 'edit'), this.permissionScope) },
    showPublish() {
      if (!this.canEdit || !this.state.ready || this.state.error || this.state.effectiveStatus === 'DELETED') return false
      if (!hasPermission(resourceActionPermission(this.resourceType, 'submit'), this.permissionScope)) return false
      if (this.state.requestStatus === 'PENDING') return false
      if (this.state.requestStatus === 'EDITING') {
        return this.state.owned && hasPermission('approval:submit', this.permissionScope) && ['CREATE', 'UPDATE', 'RESTORE', 'ENABLE'].includes(this.state.action)
      }
      return this.resourceType === 'RULE' && Boolean(this.state.hasApprovedRuleRevision || this.state.hasRuleDraft && hasPermission('approval:submit', this.permissionScope))
    },
    disabled() { return this.busy || !this.showPublish },
  },
  watch: { resourceType: 'subscribe', resourceId: 'subscribe' },
  mounted() { this.subscribe() },
  beforeUnmount() { this.sequence++; this.release?.() },
  methods: {
    subscribe() {
      this.sequence++; this.release?.(); this.state = {}; this.issueVisible = false; this.preflightErrors = []; this.checkedRequestId = null
      this.release = watchResourceState(this.resourceType, this.resourceId, value => { this.state = value })
    },
    openPreflight() { this.issueVisible = false; this.$router.push('/approval/' + this.checkedRequestId) },
    async publish() {
      if (this.disabled) return
      // Rules have multiple revisions and publication bindings; the existing lifecycle page selects the exact one.
      if (this.resourceType === 'RULE') {
        await this.$router.push({ path: '/rule/' + this.resourceId, query: { focus: 'lifecycle' } }); return
      }
      this.busy = true
      const sequence = this.sequence
      try {
        const response = await getWorkspaceDraft(this.resourceType, this.resourceId)
        if (sequence !== this.sequence) return
        const draft = response.data
        if (!draft?.id && this.state.requestId) { await this.$refs.versions.open(); return }
        if (!draft?.id || !['CREATE', 'UPDATE', 'RESTORE', 'ENABLE'].includes(draft.action)) throw new Error('草稿状态已变化，请刷新后重试')
        const preflight = await preflightGovernanceRequest(draft.id)
        if (sequence !== this.sequence) return
        if (preflight?.data?.valid !== true) {
          this.checkedRequestId = draft.id
          this.preflightErrors = preflight?.data?.errors?.length ? preflight.data.errors : [{ message: '未取得完整检查结果，请在预检详情中重试。' }]
          this.issueVisible = true
          return
        }
        await this.$confirm('提交当前已保存的草稿？审批通过后才会发布到生产。', '提交发布审批')
        if (sequence !== this.sequence) return
        await submitGovernanceRequest(draft.id, '从列表提交发布审批', draft.snapshotDigest)
        this.$message.success('已提交发布审批，生产内容在审批通过前保持原状')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close' && !error?.requestErrorNotified) this.$message.error(error.message || '提交失败')
      } finally { this.busy = false }
    },
  },
}
</script>
