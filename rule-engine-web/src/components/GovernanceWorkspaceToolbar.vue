<template>
  <span v-if="canView" class="governance-workspace-entry">
    <el-button size="small" @click="open">草稿与审批</el-button>
    <el-drawer v-model="visible" title="草稿与审批" size="min(960px, 100vw)" append-to-body>
      <div class="uiue-search-container uiue-filter-toolbar">
        <el-form inline size="small" @submit.prevent="search">
          <el-form-item label="查看范围"><el-select v-model="viewScope" aria-label="查看范围" @change="search">
            <el-option label="我的申请" value="MINE" /><el-option v-if="canReview" label="全部可见申请" value="ALL" />
          </el-select></el-form-item>
          <el-form-item label="审批状态"><el-select v-model="status" aria-label="审批状态" @change="search">
            <el-option label="全部状态" value="ALL" /><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select></el-form-item>
          <el-form-item><el-button :loading="loading" @click="load">刷新</el-button></el-form-item>
        </el-form>
      </div>
      <p class="workspace-help">保存草稿后仍可编辑；发布审批通过后才生效。新增内容待审批时须先驳回才能删除。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table :aria-busy="loading" :data="rows" empty-text="当前范围没有草稿或审批申请">
        <el-table-column label="内容" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ title(row) }}</template></el-table-column>
        <el-table-column label="变更" width="90"><template #default="{ row }">{{ actionLabel(row.action) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="applicant" label="申请人" min-width="100" show-overflow-tooltip />
        <el-table-column prop="reviewComment" label="审批意见" min-width="150" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" :width="operationColumnWidth(['发布', '查看详情', '删除草稿', '撤回'])" fixed="right"><template #default="{ row }">
          <el-button v-if="row.status === 'EDITING' && canPublish(row)" :data-request-id="row.id" link type="success" :disabled="busy" @click="publish(row)">{{ submitLabel(row) }}</el-button>
          <el-button v-if="canReview" :data-review-id="row.id" link type="primary" :disabled="busy" @click="review(row)">查看详情</el-button>
          <el-button v-if="viewScope === 'MINE' && row.status === 'EDITING' && row.action === 'CREATE' && canEdit(row)" link type="danger" :disabled="busy" @click="discard(row)">删除草稿</el-button>
          <el-button v-if="viewScope === 'MINE' && row.status === 'PENDING' && !isNewContentRequest(row) && canEdit(row)" link :disabled="busy" @click="withdraw(row)">撤回</el-button>
          <span v-if="row.status === 'PENDING' && isNewContentRequest(row)" class="workspace-help">驳回后可删除</span>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </el-drawer>
  </span>
</template>
<script>
import { listWorkspaceDrafts, listGovernanceRequests, submitGovernanceRequest, cancelGovernanceRequest, deleteResource } from '@/api/governance'
import { hasPermission } from '@/security/permissionState'
import { resourceActionPermission, resourceViewPermission } from '@/security/pickerAccess'
import { isNewContentRequest } from '@/utils/governanceWorkspace'
import { parseMetadataResponse } from '@/utils/metadataResponse'
import { routeProjectId, routeSupportsProjectContext } from '@/utils/projectContext'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'

const statuses = [{ value: 'EDITING', label: '草稿' }, { value: 'PENDING', label: '审批中' }, { value: 'APPROVED', label: '已通过' }, { value: 'REJECTED', label: '已驳回' }, { value: 'CONFLICT', label: '审批冲突' }, { value: 'CANCELLED', label: '已取消' }]
export default {
  name: 'GovernanceWorkspaceToolbar',
  props: { resourceType: { type: String, default: '' }, projectId: { type: [String, Number], default: null } },
  data: () => ({ visible: false, loading: false, busy: false, status: 'EDITING', viewScope: 'MINE', page: 1, total: 0, rows: [], error: '', sequence: 0, statuses }),
  computed: {
    tab() { return this.resourceType ? '' : 'FIELD' },
    canView() { return hasPermission(this.resourceType ? resourceViewPermission(this.resourceType) : 'field:view') },
    canReview() { return hasPermission('approval:view') },
    contextProjectId() {
      const currentProject = routeSupportsProjectContext(this.$route?.path) ? this.$store?.state?.currentProject : null
      return this.projectId || routeProjectId(this.$route, currentProject) || undefined
    },
    contextKey() { return `${this.resourceType}:${this.tab}:${this.contextProjectId || ''}` },
  },
  watch: { contextKey() { this.sequence++; this.visible = false; this.rows = []; this.loading = false } },
  mounted() { window.addEventListener('tianshu:resource-draft-change', this.refreshIfOpen) },
  beforeUnmount() { this.sequence++; window.removeEventListener('tianshu:resource-draft-change', this.refreshIfOpen) },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    isNewContentRequest,
    canEdit(row) { return hasPermission(resourceActionPermission(row.resourceType, 'edit')) },
    canPublish(row) { return this.viewScope === 'MINE' && this.canEdit(row) && hasPermission('approval:submit') && hasPermission(resourceActionPermission(row.resourceType, 'submit')) },
    statusLabel(status) { return statuses.find(item => item.value === status)?.label || status },
    statusType(status) { return { APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', CONFLICT: 'danger' }[status] || 'info' },
    actionLabel(action) { return { CREATE: '新增', UPDATE: '修改', RESTORE: '恢复版本', ENABLE: '启用', DISABLE: '停用', DELETE: '删除' }[action] || action },
    submitLabel(row) { return row.action === 'DELETE' ? '提交删除审批' : row.action === 'DISABLE' ? '提交停用审批' : '发布' },
    refreshIfOpen() { if (this.visible) this.load() },
    open() { this.visible = true; this.search() },
    search() { this.page = 1; return this.load() },
    async load() {
      const sequence = ++this.sequence
      this.loading = true; this.error = ''
      try {
        const params = { ...(this.resourceType ? { resourceType: this.resourceType } : { tab: this.tab }), status: this.status, projectId: this.contextProjectId, pageNum: this.page, pageSize: 20 }
        const response = this.viewScope === 'ALL' && this.canReview
          ? await listGovernanceRequests({ ...params, taskScope: 'ALL', status: this.status === 'ALL' ? undefined : this.status })
          : await listWorkspaceDrafts(params)
        if (sequence !== this.sequence) return
        this.rows = response.data?.records || []; this.total = response.data?.total || 0
      } catch (error) { if (sequence === this.sequence) this.error = error.message || '草稿与审批读取失败，请重试' }
      finally { if (sequence === this.sequence) this.loading = false }
    },
    title(row) {
      let value = {}
      try { value = parseMetadataResponse(row.draftSnapshotJson || '{}') } catch { /* Preserve the request number for older incomplete records. */ }
      const keys = { RULE: 'ruleName', PROJECT: 'projectName', VARIABLE: 'varLabel', DATA_OBJECT: 'objectLabel', MODEL: 'modelName', FUNCTION: 'funcName', EXTERNAL_API: 'apiName', EXTERNAL_DATASOURCE: 'datasourceName', DATABASE: 'datasourceName', SFTP: 'datasourceName', EXPERIMENT: 'experimentName', LIST_LIBRARY: 'listName', FIELD_VALIDATION: 'validationName', BILLING_CONFIG: 'billingName' }
      return value[keys[row.resourceType]] || row.requestNo
    },
    review(row) { this.visible = false; this.$router.push('/approval/' + row.id) },
    async publish(row) {
      if (this.busy || !this.canPublish(row)) return
      this.busy = true
      try {
        await this.$confirm(`${this.submitLabel(row)}「${this.title(row)}」？审批通过后才会生效。`, '提交发布审批')
        await submitGovernanceRequest(row.id, '提交发布审批', row.snapshotDigest)
        this.$message.success('已提交审批'); await this.load()
      } catch (error) { if (error !== 'cancel' && error !== 'close' && !error?.requestErrorNotified) this.$message.error(error.message || '提交失败') }
      finally { this.busy = false }
    },
    async discard(row) {
      try {
        await this.$confirm(`删除「${this.title(row)}」的未发布草稿？`, '删除草稿')
        this.busy = true
        if (String(row.resourceId || '').match(/^[1-9]\d*$/)) {
          const response = await deleteResource(row.resourceType, row.resourceId)
          if (!response.data?.deleted) { this.$message.success('已生成删除审批草稿'); await this.load(); return }
        } else await cancelGovernanceRequest(row.id, '删除未发布草稿')
        this.$message.success('草稿已删除，无需审批'); await this.load()
      } catch (error) { if (error !== 'cancel' && error !== 'close' && !error?.requestErrorNotified) this.$message.error(error.message || '删除失败') }
      finally { this.busy = false }
    },
    async withdraw(row) {
      this.busy = true
      try { await cancelGovernanceRequest(row.id, '撤回发布申请'); await this.load() }
      catch (error) { if (!error?.requestErrorNotified) this.$message.error(error.message || '撤回失败') }
      finally { this.busy = false }
    },
  },
}
</script>
<style scoped>
.governance-workspace-entry { display: inline-flex; vertical-align: middle; }
.workspace-help { color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.6; }
.el-form { display: flex; flex-wrap: wrap; gap: 8px; }
.el-select { width: 160px; }
.el-pagination { margin-top: 16px; }
</style>
