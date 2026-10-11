<template>
  <span class="governance-resource-status">
    <el-tag v-if="summary.label" :type="summary.type" :title="summary.detail" size="small">{{ summary.label }}</el-tag>
    <slot v-else />
  </span>
</template>
<script>
import { watchResourceState } from '@/utils/workspaceDraftState'

export default {
  name: 'GovernanceResourceStatus',
  props: { resourceType: { type: String, required: true }, resourceId: { type: [String, Number], default: null }, status: { type: Number, default: null } },
  data: () => ({ state: {}, release: null }),
  computed: {
    baseState() {
      if (this.state.effectiveStatus === 'DELETED' || this.status === -1) return 'DELETED'
      if (this.resourceType === 'RULE' && this.status !== null) return { 0: 'DRAFT', 1: 'ACTIVE', 2: 'DISABLED' }[this.status] || 'UNKNOWN'
      if (['DRAFT', 'ACTIVE', 'DISABLED'].includes(this.state.effectiveStatus)) return this.state.effectiveStatus
      return { 0: 'DISABLED', 1: 'ACTIVE', 2: 'DISABLED' }[this.status] || 'UNKNOWN'
    },
    summary() {
      if (this.state.error) return { label: '状态未知', type: 'warning', detail: '状态读取失败，请刷新后重试。' }
      if (!this.state.ready) return { label: '' }
      const base = this.baseState
      const label = { DRAFT: '草稿', ACTIVE: '已发布', DISABLED: '已停用', DELETED: '已删除', UNKNOWN: '状态未知' }[base]
      const detail = { DRAFT: '内容尚未发布到生产。', ACTIVE: '当前生产版本仍然生效。', DISABLED: '内容当前未在生产生效。', DELETED: '内容已删除。', UNKNOWN: '尚无法确认生产状态。' }[base]
      if (base === 'DELETED') return { label, type: 'info', detail }
      const status = this.state.requestStatus
      const operation = { CREATE: '新增发布', UPDATE: '修改发布', RESTORE: '版本恢复', ENABLE: '启用', DISABLE: '停用', DELETE: '删除' }[this.state.action] || '发布'
      if (status === 'PENDING') return {
        label: ['ACTIVE', 'DISABLED'].includes(base) ? `${label}·审批中` : '审批中',
        type: 'warning', detail: `${detail}${operation}申请正在审批，审批通过前不会改变生产内容。`,
      }
      if (status === 'EDITING' || this.state.hasRuleDraft || this.state.hasApprovedRuleRevision) return {
        label: base === 'DRAFT' ? '草稿' : `${label}·待更新`,
        type: base === 'DRAFT' ? 'info' : 'warning',
        detail: this.state.hasApprovedRuleRevision && status !== 'EDITING' && !this.state.hasRuleDraft
          ? `${detail}另有已批准的规则修订尚未发布，请选择具体修订后发布。`
          : `${detail}已保存${status === 'EDITING' ? operation : '发布'}草稿，尚未提交审批。`,
      }
      if (status === 'REJECTED' || status === 'CONFLICT') return {
        label: `${label}·需修改`, type: 'danger',
        detail: `${detail}${operation}申请${status === 'REJECTED' ? '已被驳回' : '存在冲突'}，请查看审批详情，处理后重新提交。`,
      }
      return { label, type: base === 'ACTIVE' ? 'success' : 'info', detail }
    },
  },
  watch: { resourceType: 'subscribe', resourceId: 'subscribe' },
  mounted() { this.subscribe() },
  beforeUnmount() { this.release?.() },
  methods: {
    subscribe() {
      this.release?.(); this.state = {}
      this.release = watchResourceState(this.resourceType, this.resourceId, value => { this.state = value })
    },
  },
}
</script>
<style scoped>
.governance-resource-status { display: inline-flex; flex-flow: row nowrap; align-items: center; gap: 6px; vertical-align: middle; white-space: nowrap; }
.governance-resource-status :deep(.el-tag) { flex: 0 0 auto; white-space: nowrap; }
</style>
