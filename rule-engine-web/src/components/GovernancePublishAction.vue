<template>
  <resource-version-manager v-if="resourceId" ref="versions" :resource-type="resourceType" :resource-id="resourceId" :show-trigger="showVersions" />
  <el-button v-if="showPublish" link type="success" data-action="publish" size="small" :disabled="disabled" :loading="busy" @click="publish">发布</el-button>
</template>
<script>
import { getWorkspaceDraft, submitGovernanceRequest } from '@/api/governance'
import { watchResourceState } from '@/utils/workspaceDraftState'
import { hasPermission } from '@/security/permissionState'
import { resourceActionPermission } from '@/security/pickerAccess'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'

export default {
  name: 'GovernancePublishAction',
  components: { ResourceVersionManager },
  props: { resourceType: { type: String, required: true }, resourceId: { type: [Number, String], default: null }, showVersions: { type: Boolean, default: true } },
  data: () => ({ state: {}, release: null, busy: false, sequence: 0 }),
  computed: {
    canEdit() { return /^[1-9]\d*$/.test(String(this.resourceId)) && hasPermission(resourceActionPermission(this.resourceType, 'edit')) },
    showPublish() {
      if (!this.canEdit || !this.state.ready || this.state.error || this.state.effectiveStatus === 'DELETED') return false
      if (!hasPermission(resourceActionPermission(this.resourceType, 'submit'))) return false
      if (this.state.requestStatus === 'PENDING') return false
      if (this.state.requestStatus === 'EDITING') {
        return this.state.owned && hasPermission('approval:submit') && ['CREATE', 'UPDATE', 'RESTORE', 'ENABLE'].includes(this.state.action)
      }
      return this.resourceType === 'RULE' && Boolean(this.state.hasApprovedRuleRevision || this.state.hasRuleDraft && hasPermission('approval:submit'))
    },
    disabled() { return this.busy || !this.showPublish },
  },
  watch: { resourceType: 'subscribe', resourceId: 'subscribe' },
  mounted() { this.subscribe() },
  beforeUnmount() { this.sequence++; this.release?.() },
  methods: {
    subscribe() {
      this.sequence++; this.release?.(); this.state = {}
      this.release = watchResourceState(this.resourceType, this.resourceId, value => { this.state = value })
    },
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
