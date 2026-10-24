<template>
  <span class="resource-version-entry">
    <el-button v-if="canView && showTrigger" link type="primary" data-action="version" size="small" @click.stop="open">版本</el-button>
    <el-drawer v-model="visible" title="版本管理" size="min(1120px, 100vw)" append-to-body destroy-on-close :before-close="close">
      <div v-loading="loading" class="resource-version-manager" :aria-busy="loading"
        element-loading-background="transparent" element-loading-text="正在加载版本…">
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-select v-model="selected" aria-label="查看版本" :disabled="editing || busy" @change="comparison = ''">
            <el-option label="最新版（当前生产）" value="LATEST" :disabled="!workspace.latestVersionId" />
            <el-option-group label="业务版本">
              <el-option v-for="version in activeVersions" :key="version.versionId" :label="label(version)" :value="'r:' + version.versionId" />
            </el-option-group>
            <el-option v-if="selected.startsWith('r:') && currentVersion && !activeVersions.some(row => String(row.versionId) === String(currentVersion.id))" :value="selected" :label="`v${currentVersion.versionNo} · 历史修订 ${currentVersion.revisionNo}`" />
            <el-option-group label="草稿与审批">
              <el-option v-for="draft in workspace.drafts || []" :key="draft.id" :label="`草稿 #${draft.id} · ${statusLabel(draft.status)}`" :value="'d:' + draft.id" />
            </el-option-group>
          </el-select>
          <div class="version-actions uiue-action-group">
            <el-button :disabled="busy || editing" @click="load">刷新</el-button>
            <el-button v-if="hasLegacy" :disabled="busy || editing" @click="visible = false; $emit('legacy')">早期历史快照</el-button>
            <el-button v-if="canExport" :disabled="!snapshot || !!currentDraft || editing || busy || selectedVersionDeleted" @click="exportVersion">迁移此版本</el-button>
            <el-button v-if="canEdit" type="primary" :disabled="!snapshot || busy || editing || selectedVersionDeleted || !!currentDraft && !editableDraft" @click="edit">{{ editableDraft ? '编辑草稿' : '基于此版本编辑' }}</el-button>
          </div>
        </div>
        <p class="version-help">草稿与生产并存。新增发布形成新版本；覆盖只更新指定版本，历史修订和变更记录保留。覆盖旧版本不会切换当前最新版。</p>
        <template v-if="editing">
          <el-form label-width="100px" class="version-edit-form">
            <el-form-item label="变更说明"><el-input v-model="changeSummary" maxlength="500" placeholder="说明本次修改及原因" /></el-form-item>
            <el-form-item v-if="!currentDraft" label="发布方式">
              <el-radio-group v-model="publishMode"><el-radio value="NEW">新增版本</el-radio><el-radio value="OVERWRITE">覆盖指定版本</el-radio></el-radio-group>
              <el-select v-if="publishMode === 'OVERWRITE'" v-model="targetVersionId" aria-label="覆盖目标版本"><el-option v-for="version in writableVersions" :key="version.versionId" :value="String(version.versionId)" :label="label(version)" /></el-select>
            </el-form-item>
            <el-form-item v-for="field in editableFields" :key="field.key" :label="field.label"><el-input :model-value="String(editValue[field.key] ?? '')" :type="field.multiline ? 'textarea' : 'text'" :rows="field.multiline ? 3 : undefined" :aria-label="field.label" @update:model-value="editField(field.key, $event)" /></el-form-item>
            <el-collapse><el-collapse-item title="完整配置（结构与高级参数）" name="raw"><el-form-item label="版本内容"><MonacoEditor v-model:value="editJson" language="json" height="300px" aria-label="版本内容" :options="{ minimap: { enabled: false }, wordWrap: 'on' }" /></el-form-item></el-collapse-item></el-collapse>
          </el-form>
          <div class="version-actions uiue-action-group"><el-button :disabled="busy" @click="cancelEdit">取消编辑</el-button><el-button type="primary" :loading="busy" @click="save">保存草稿</el-button></div>
        </template>
        <el-tabs v-else v-model="tab">
          <el-tab-pane label="预览与对比" name="preview">
            <el-select v-model="comparison" clearable placeholder="选择另一版本对比" aria-label="对比版本">
              <el-option v-for="item in workspace.history || []" :key="item.id" :value="String(item.id)" :label="`v${item.versionNo} · 修订 ${item.revisionNo}`" />
            </el-select>
            <json-version-diff v-if="comparison && snapshot" :original="compared?.snapshotJson || '{}'" :modified="snapshot" :original-label="`对比版本 v${compared?.versionNo}`" modified-label="当前所选版本" />
            <template v-else-if="snapshot">
              <el-descriptions :column="2" border class="version-preview-summary"><el-descriptions-item v-for="field in previewFields" :key="field.key" :label="field.label">{{ field.value }}</el-descriptions-item></el-descriptions>
              <el-collapse><el-collapse-item title="完整配置快照" name="raw"><pre class="version-preview">{{ pretty(snapshot) }}</pre></el-collapse-item></el-collapse>
            </template>
            <el-empty v-else-if="!loading && !error" description="尚无生产版本，请先保存并发布资源" />
            <div v-else class="version-preview-placeholder" aria-hidden="true" />
            <div v-if="currentDraft" class="version-actions uiue-action-group">
              <el-button v-if="canReview" :disabled="busy" @click="review">查看审批详情</el-button>
              <el-button v-if="editableDraft && canEdit" type="danger" :disabled="busy" @click="discard">删除此草稿</el-button>
              <el-button v-if="editableDraft && canSubmit" type="primary" :loading="busy" @click="publish">提交发布审批</el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="版本与修订" name="versions">
            <el-table :data="workspace.versions || []" row-key="versionId">
              <el-table-column label="版本" width="140"><template #default="{ row }">{{ label(row) }}</template></el-table-column>
              <el-table-column prop="revision" label="当前修订" width="100" />
              <el-table-column prop="changeSummary" label="变更说明" min-width="200" />
              <el-table-column prop="createBy" label="发布人" width="110" />
              <el-table-column prop="createTime" label="发布时间" min-width="170" />
              <el-table-column label="操作" :width="operationColumnWidth(['预览', '删除版本'])"><template #default="{ row }"><el-button link @click="selected = 'r:' + row.versionId; tab = 'preview'">预览</el-button><el-button v-if="canEdit && !row.latest && !row.deleted" link type="danger" :disabled="busy" @click="remove(row)">删除版本</el-button></template></el-table-column>
            </el-table>
            <el-table :data="workspace.history || []" row-key="id" class="version-history">
              <el-table-column label="历史修订" width="170"><template #default="{ row }">v{{ row.versionNo }} · 修订 {{ row.revisionNo }}</template></el-table-column>
              <el-table-column prop="changeSummary" label="变更说明" />
              <el-table-column prop="createTime" label="时间" width="180" />
              <el-table-column label="操作" :width="operationColumnWidth(['预览'])"><template #default="{ row }"><el-button link @click="selected = 'r:' + row.id; tab = 'preview'">预览</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="变更记录" name="events">
            <el-table :data="events" row-key="id"><el-table-column prop="createTime" label="时间" width="180" /><el-table-column prop="actor" label="操作人" width="120" /><el-table-column label="操作" width="130"><template #default="{ row }">{{ eventLabel(row.action) }}</template></el-table-column><el-table-column prop="comment" label="说明" /><el-table-column prop="requestId" label="审批单" width="100" /></el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </span>
</template>
<script>
import { getResourceVersions, getResourceVersionEvents, getVersionDeletionImpact, createVersionDraft, deleteResourceVersion } from '@/api/resourceVersions'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { saveGovernanceDraft, submitGovernanceRequest, cancelGovernanceRequest } from '@/api/governance'
import { exportResourceTransfer } from '@/api/transfer'
import { hasPermission } from '@/security/permissionState'
import { resourceActionPermission } from '@/security/pickerAccess'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
import { parseMetadataResponse } from '@/utils/metadataResponse'
import JsonVersionDiff from './JsonVersionDiff.vue'

const fields = [
  ['varCode', '变量编码'], ['varLabel', '变量名称'], ['varType', '数据类型'], ['defaultValue', '默认值'],
  ['ruleCode', '规则编码'], ['ruleName', '规则名称'], ['modelCode', '模型编码'], ['modelName', '模型名称'],
  ['apiCode', '接口编码'], ['apiName', '接口名称'], ['endpointUrl', '接口地址'],
  ['datasourceCode', '数据源编码'], ['datasourceName', '数据源名称'], ['objectCode', '对象编码'], ['objectLabel', '对象名称'],
  ['experimentCode', '实验编码'], ['experimentName', '实验名称'], ['funcCode', '函数编码'], ['funcName', '函数名称'],
  ['implType', '实现方式'], ['implMethod', '实际方法'], ['implClass', 'Java 类'], ['implBeanName', 'Bean 名称'],
  ['returnType', '返回类型'], ['paramsJson', '参数定义', true],
  ['projectCode', '项目编码'], ['projectName', '项目名称'], ['billingCode', '计费编码'], ['billingName', '计费名称'],
  ['description', '说明', true],
].map(([key, label, multiline]) => ({ key, label, multiline }))

export default {
  name: 'ResourceVersionManager',
  components: { JsonVersionDiff, MonacoEditor },
  props: { resourceType: { type: String, required: true }, resourceId: { type: [String, Number], required: true }, showTrigger: { type: Boolean, default: true }, hasLegacy: Boolean },
  emits: ['changed', 'legacy'],
  data: () => ({ visible: false, loading: false, busy: false, sequence: 0, contextGeneration: 0, workspace: {}, events: [], error: '', selected: 'LATEST', tab: 'preview', comparison: '', editing: false, editJson: '', changeSummary: '', publishMode: 'NEW', targetVersionId: '' }),
  computed: {
    contextKey() { return `${this.resourceType}:${this.resourceId}` },
    canView() { return hasPermission(resourceActionPermission(this.resourceType, 'view')) },
    permissionScope() { return Object.hasOwn(this.workspace, 'projectId') ? { projectId: this.workspace.projectId, scope: this.workspace.scope } : undefined },
    canEdit() {
      if (this.resourceType === 'FUNCTION') {
        try { if (['JAVA', 'BEAN'].includes(parseMetadataResponse(this.snapshot || '{}').implType) && !hasPermission('function:native')) return false } catch { return false }
      }
      return hasPermission(resourceActionPermission(this.resourceType, 'edit'), this.permissionScope)
    },
    canSubmit() { return this.canEdit && hasPermission('approval:submit', this.permissionScope) && hasPermission(resourceActionPermission(this.resourceType, 'submit'), this.permissionScope) },
    canReview() { return hasPermission('approval:view') },
    canExport() { return hasPermission('transfer:export', this.permissionScope) },
    activeVersions() { return (this.workspace.versions || []).filter(row => !row.deleted) },
    writableVersions() { return this.activeVersions.filter(row => !row.status || row.status === 'ACTIVE') },
    currentDraft() { return this.selected.startsWith('d:') ? (this.workspace.drafts || []).find(row => String(row.id) === this.selected.slice(2)) : null },
    editableDraft() { return this.currentDraft?.status === 'EDITING' && (this.workspace.actorNames || [this.workspace.actor]).includes(this.currentDraft.applicant) },
    currentVersion() { if (this.selected !== 'LATEST' && !this.selected.startsWith('r:')) return null; const id = this.selected === 'LATEST' ? this.workspace.latestVersionId : this.selected.slice(2); return (this.workspace.history || []).find(row => String(row.id) === String(id)) },
    selectedVersionDeleted() { return !!this.currentVersion && (this.workspace.versions || []).some(row => row.deleted && String(row.version) === String(this.currentVersion.versionNo)) },
    snapshot() { return this.currentDraft?.draftSnapshotJson || this.currentVersion?.snapshotJson || '' },
    compared() { return (this.workspace.history || []).find(row => String(row.id) === this.comparison) },
    editValue() { try { return parseMetadataResponse(this.editJson) || {} } catch { return {} } },
    editableFields() { return fields.filter(field => field.key !== 'varType' && Object.hasOwn(this.editValue, field.key)) },
    previewFields() { let value = {}; try { value = parseMetadataResponse(this.snapshot || '{}') } catch { /* raw content stays available */ } return fields.filter(field => Object.hasOwn(value, field.key)).map(field => ({ ...field, value: value[field.key] ?? '—' })) },
  },
  watch: { contextKey() { this.sequence++; this.contextGeneration++; this.visible = false; this.editing = false; this.workspace = {}; this.events = []; this.selected = 'LATEST'; this.loading = false; this.busy = false } },
  beforeUnmount() { this.sequence++; this.contextGeneration++ },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    label(row) { return `v${row.version}${row.latest ? ' · 最新' : ''}${row.deleted ? ' · 已删除' : ''}` },
    pretty(json) { try { return JSON.stringify(parseMetadataResponse(json), null, 2) } catch { return json } },
    statusLabel(value) { return { EDITING: '编辑中', PENDING: '审批中', CONFLICT: '审批冲突', REJECTED: '已驳回' }[value] || value },
    eventLabel(value) { return { CREATE_DRAFT: '创建草稿', SAVE_DRAFT: '修改草稿', SUBMIT: '提交发布', APPROVE: '审批通过', REJECT: '驳回', CANCEL: '删除或撤回草稿', DELETE_VERSION: '删除版本' }[value] || value },
    async open() { if (!this.canView) return; this.visible = true; this.selected = 'LATEST'; this.editing = false; await this.load() },
    async load() {
      const sequence = ++this.sequence; this.loading = true; this.error = ''
      try {
        const [versions, events] = await Promise.all([getResourceVersions(this.resourceType, this.resourceId), getResourceVersionEvents(this.resourceType, this.resourceId)])
        if (sequence !== this.sequence) return
        this.workspace = versions.data || {}; this.events = events.data || []
        if (!this.snapshot) this.selected = this.workspace.latestVersionId ? 'LATEST' : this.workspace.drafts?.length ? 'd:' + this.workspace.drafts[0].id : 'LATEST'
      } catch (error) { if (sequence === this.sequence) this.error = error.message || '版本读取失败，请重试' }
      finally { if (sequence === this.sequence) this.loading = false }
    },
    edit() { this.editJson = this.pretty(this.snapshot); this.changeSummary = this.currentDraft?.changeSummary || ''; this.publishMode = 'NEW'; this.targetVersionId = ''; this.editing = true },
    editField(key, value) { this.editJson = JSON.stringify({ ...this.editValue, [key]: value }, null, 2) },
    async cancelEdit() { if (this.editJson !== this.pretty(this.snapshot)) { try { await this.$confirm('放弃当前未保存的修改？', '取消编辑') } catch { return } } this.editing = false },
    async close(done) { if (this.busy) return; if (this.editing) { await this.cancelEdit(); if (this.editing) return } done() },
    async save() {
      if (this.busy || !this.canEdit) return
      const target = this.writableVersions.find(row => String(row.versionId) === this.targetVersionId)
      if (!this.currentDraft && this.publishMode === 'OVERWRITE' && !target) { this.$message.warning('请选择覆盖目标版本'); return }
      try { const value = parseMetadataResponse(this.editJson); if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error() } catch { this.$message.error('版本内容必须是有效的 JSON 对象'); return }
      const context = this.contextGeneration; this.busy = true
      try {
        const data = { snapshotJson: this.editJson, changeSummary: this.changeSummary }
        const response = this.currentDraft
          ? await saveGovernanceDraft(this.currentDraft.id, { ...data, expectedSnapshotDigest: this.currentDraft.snapshotDigest })
          : await createVersionDraft(this.resourceType, this.resourceId, { ...data, sourceVersionId: this.currentVersion.id, publishMode: this.publishMode, ...(target ? { targetVersionId: target.versionId, expectedTargetSnapshotId: target.versionId } : {}) })
        if (context !== this.contextGeneration) return
        this.selected = 'd:' + response.data.id; this.editing = false; await this.load()
        if (context === this.contextGeneration) { this.$message.success('草稿已保存，生产版本保持生效'); this.$emit('changed') }
      } catch (error) { if (context === this.contextGeneration) this.showError(error, '保存失败') } finally { if (context === this.contextGeneration) this.busy = false }
    },
    async publish() { const draft = this.currentDraft; if (!draft) return; await this.mutate(() => submitGovernanceRequest(draft.id, draft.changeSummary || '提交版本发布', draft.snapshotDigest), '提交此草稿的发布审批？', '已提交发布审批') },
    review() { if (this.busy || !this.currentDraft) return; const id = this.currentDraft.id; this.visible = false; this.$router.push('/approval/' + id) },
    async discard() { const draft = this.currentDraft; if (!draft) return; await this.mutate(() => cancelGovernanceRequest(draft.id, '删除版本草稿'), '删除此草稿？生产版本不受影响。', '草稿已删除') },
    async remove(row) {
      const { resourceType, resourceId } = this; const { versionId, version } = row
      await this.mutate(() => deleteResourceVersion(resourceType, resourceId, versionId, versionId), `删除 v${version}？该版本将不可再选用，历史记录仍保留。`, '版本已删除', async () => {
        const response = await getVersionDeletionImpact(resourceType, resourceId, versionId)
        if (!response.data?.allowed) throw new Error((response.data?.issues || []).map(issue => issue.message).join('；') || '当前版本不能删除')
        if (String(response.data.expectedSnapshotId) !== String(versionId)) throw new Error('版本已更新，请刷新后重新删除')
      })
    },
    async mutate(action, question, success, beforeConfirm) {
      if (this.busy) return
      const context = this.contextGeneration; this.busy = true
      try {
        if (beforeConfirm) { await beforeConfirm(); if (context !== this.contextGeneration) return }
        await this.$confirm(question, '版本管理'); if (context !== this.contextGeneration) return
        await action(); if (context !== this.contextGeneration) return
        await this.load(); if (context === this.contextGeneration) { this.$message.success(success); this.$emit('changed') }
      } catch (error) { if (context === this.contextGeneration) this.showError(error, '操作失败') } finally { if (context === this.contextGeneration) this.busy = false }
    },
    showError(error, fallback) { if (error !== 'cancel' && error !== 'close' && !error?.requestErrorNotified) this.$message.error(error.message || fallback) },
    async exportVersion() {
      if (this.busy || !this.currentVersion || this.selectedVersionDeleted) return
      const context = this.contextGeneration
      const filename = `${this.resourceType}-${this.resourceId}-v${this.currentVersion.versionNo}.zip`
      this.busy = true
      try {
        const response = await exportResourceTransfer([{ resourceType: this.resourceType, resourceId: this.resourceId, versionMode: this.selected === 'LATEST' ? 'LATEST' : 'FIXED', versionId: this.selected === 'LATEST' ? null : this.currentVersion.id }])
        const url = URL.createObjectURL(response.data); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url)
      } catch (error) { if (context === this.contextGeneration) this.showError(error, '版本迁移包生成失败') } finally { if (context === this.contextGeneration) this.busy = false }
    },
  },
}
</script>
<style scoped>
.resource-version-entry { display: inline-flex; vertical-align: middle; }
.resource-version-manager { min-width: 0; }
.version-preview-placeholder { min-height: 240px; }
.uiue-filter-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.version-actions { justify-content: flex-end; margin-left: auto; }
.version-help { color: var(--tianshu-text-secondary); line-height: 1.6; margin: 16px 0; white-space: normal; }
.el-select { width: 256px; max-width: 100%; }
.version-preview { background: var(--tianshu-bg-secondary); color: var(--tianshu-text-primary); padding: 16px; max-height: 480px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 13px; }
.version-history { margin-top: 24px; }
.version-preview-summary { margin: 16px 0; overflow-wrap: anywhere; }
.version-edit-form :deep(.el-form-item__content) { gap: 12px; }
</style>
