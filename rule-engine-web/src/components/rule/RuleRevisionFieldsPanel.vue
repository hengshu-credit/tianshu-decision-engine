<template>
  <section class="revision-fields" data-testid="revision-fields-panel">
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small">
        <el-form-item label="查看修订" class="revision-selector-field">
          <el-select :model-value="modelValue" data-testid="field-revision-select" @update:model-value="$emit('update:modelValue', $event)">
            <el-option v-for="item in revisions" :key="item.id" :value="item.id" :label="revisionLabel(item)" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="uiue-btn-bar">
        <el-button size="small" :loading="loading" :disabled="!modelValue" @click="load">刷新字段视图</el-button>
        <el-button v-if="workspaceAvailable" size="small" @click="$emit('workspace')">工作区字段配置</el-button>
      </div>
    </div>
    <p v-if="loading" role="status" data-testid="revision-fields-loading">正在读取所选修订字段…</p>
    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon data-testid="revision-fields-error" />
    <template v-else-if="view">
      <el-alert :title="sourceTitle" :type="view.status === 'AVAILABLE' ? 'info' : view.status === 'INVALID' ? 'error' : 'warning'" :closable="false" show-icon data-testid="revision-fields-source">
        <span v-if="view.source === 'FROZEN_ARTIFACT'">按制品保存的名称、路径和引用展示，查看不会修改工作区字段。</span>
        <span v-else-if="view.source === 'DRAFT_ANALYSIS'">根据这份草稿与当前环境资源解析；资源变化后可刷新查看。</span>
        <ul v-if="view.diagnostics?.length" class="revision-field-diagnostics">
          <li v-for="(issue, index) in view.diagnostics" :key="index">{{ issue.message || issue.code }}<span v-if="issue.path">（{{ issue.path }}）</span></li>
        </ul>
      </el-alert>
      <template v-if="view.status === 'AVAILABLE'">
        <div class="revision-fields-caption">
          <strong>{{ direction === 'input' ? '输入字段' : '输出字段' }} · {{ fields.length }} 个</strong>
          <el-tag size="small" type="info">只读</el-tag>
        </div>
        <el-table :data="pagedFields" border size="small" max-height="500" data-testid="revision-fields-table">
          <el-table-column label="字段名称" min-width="170">
            <template #default="{ row }"><span class="revision-field-name">{{ row.fieldLabel || row.fieldName || row.scriptName || '未命名字段' }}</span></template>
          </el-table-column>
          <el-table-column label="字段路径" min-width="210">
            <template #default="{ row }"><code class="revision-field-path">{{ row.scriptName || row.fieldName || '—' }}</code></template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ typeLabel(row.fieldType) }}</template>
          </el-table-column>
          <el-table-column label="引用" min-width="170">
            <template #default="{ row }">
              <span>{{ referenceLabel(row) }}</span><code v-if="row.varId != null" class="revision-field-reference">{{ row.varId }}</code>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="120">
            <template #default="{ row }"><code>{{ valueText(row.defaultValue) }}</code></template>
          </el-table-column>
          <el-table-column v-if="direction === 'input'" label="字段校验引用" min-width="160">
            <template #default="{ row }"><code>{{ valueText(row.validationRuleIds) }}</code></template>
          </el-table-column>
        </el-table>
        <p v-if="!fields.length" data-testid="revision-fields-empty" class="revision-field-note">该修订已确认没有{{ direction === 'input' ? '输入' : '输出' }}字段。</p>
        <el-pagination v-if="fields.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="fields.length" layout="total,prev,pager,next" />
        <p class="revision-field-note" v-if="view.validationSource === 'FROZEN_REFERENCE_IDS'">校验引用为制品记录的原始 ID；具体校验规则仍按独立生效配置管理。</p>
        <p class="revision-field-note" v-if="view.referenceNamespace === 'ARTIFACT_SOURCE'">引用标识属于制品来源，不链接当前环境中的同号资源。</p>
      </template>
      <details v-if="view.artifactId" class="revision-field-note">
        <summary>制品标识</summary>
        <div>ID：{{ view.artifactId }}</div><div class="revision-field-path">摘要：{{ view.artifactDigest || '未记录' }}</div>
      </details>
    </template>
  </section>
</template>

<script>
import { getRuleRevisionFieldView } from '@/api/definition'
import { varTypeLabel } from '@/constants/varTypes'

const STATES = { DRAFT: '草稿', REVIEW: '评审中', APPROVED: '已批准', REJECTED: '已驳回', PUBLISHED: '已发布', OFFLINE: '已下线' }

export default {
  name: 'RuleRevisionFieldsPanel',
  props: {
    definitionId: { type: [Number, String], default: null },
    modelValue: { type: [Number, String], default: null },
    revisions: { type: Array, default: () => [] },
    direction: { type: String, default: 'input' },
    workspaceAvailable: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'workspace'],
  data() { return { view: null, loading: false, error: '', requestId: 0, active: true, page: 1, pageSize: 20 } },
  computed: {
    selectedRevision() { return this.revisions.find(item => String(item.id) === String(this.modelValue)) || {} },
    requestContext() { return JSON.stringify([this.definitionId, this.modelValue, this.selectedRevision.state, this.selectedRevision.lockVersion]) },
    fields() { return this.view?.status === 'AVAILABLE' ? this.view[this.direction === 'input' ? 'inputFields' : 'outputFields'] : [] },
    pagedFields() { return this.fields.slice((this.page - 1) * this.pageSize, this.page * this.pageSize) },
    sourceTitle() {
      if (this.view.status === 'INVALID') return '该修订字段校验未通过'
      if (this.view.status !== 'AVAILABLE') return '该修订没有可验证的字段视图'
      return this.view.source === 'FROZEN_ARTIFACT' ? '制品冻结字段' : '草稿字段解析（尚未冻结）'
    },
  },
  watch: {
    requestContext: { immediate: true, handler() { this.load() } },
    direction() { this.page = 1 },
  },
  beforeUnmount() { this.active = false; this.requestId++ },
  methods: {
    revisionLabel(item) { return `修订 ${item.revisionNo ?? item.id} · ${STATES[item.state] || item.state || '未知状态'}` },
    typeLabel(value) { return value === 'INTEGER' ? '整数' : varTypeLabel(value) || '—' },
    valueText(value) { return value == null ? '—' : value === '' ? '""' : typeof value === 'object' ? JSON.stringify(value) : String(value) },
    referenceLabel(row) {
      if (row.varId == null && (this.view?.localOutputNames || []).includes(row.scriptName || row.fieldName)) return '本地输出字段'
      return { VARIABLE: '变量', CONSTANT: '常量', DATA_OBJECT: '数据对象字段', MODEL: '模型', MODEL_OUTPUT: '模型输出' }[row.refType] || row.refType || '未记录引用类型'
    },
    async load() {
      const requestId = ++this.requestId, context = this.requestContext
      const definitionId = String(this.definitionId ?? '').trim()
      const validDefinitionId = /^[1-9]\d*$/.test(definitionId)
      this.view = null; this.error = ''; this.page = 1; this.loading = Boolean(validDefinitionId && this.modelValue)
      if (!validDefinitionId || !this.modelValue) {
        if (!this.modelValue) this.error = '请选择要查看的修订'
        return
      }
      try {
        const response = await getRuleRevisionFieldView(this.definitionId, this.modelValue)
        if (!this.active || requestId !== this.requestId || context !== this.requestContext) return
        const view = response?.data !== undefined ? response.data : response
        if (!view || String(view.definitionId) !== String(this.definitionId) || String(view.revisionId) !== String(this.modelValue)
          || view.readOnly !== true || !['AVAILABLE', 'UNAVAILABLE', 'INVALID'].includes(view.status)) throw new Error('字段视图响应与所选修订不一致，请重试')
        if (view.status === 'AVAILABLE' && (!Array.isArray(view.inputFields) || !Array.isArray(view.outputFields)
          || !['FROZEN_ARTIFACT', 'DRAFT_ANALYSIS'].includes(view.source))) throw new Error('字段视图内容不完整，请重试')
        this.view = view
      } catch (error) {
        if (this.active && requestId === this.requestId && context === this.requestContext) this.error = error.message || '读取修订字段失败，请重试'
      } finally {
        if (this.active && requestId === this.requestId) this.loading = false
      }
    },
  },
}
</script>

<style scoped>
.revision-fields { color: var(--tianshu-text-primary); }
.revision-selector-field { width: 310px; max-width: 100%; margin-right: 0; }
.revision-fields-caption { display: flex; align-items: center; gap: 10px; margin: 16px 0 10px; }
.revision-field-diagnostics { margin: 8px 0 0; padding-left: 20px; line-height: 1.6; }
.revision-field-name, .revision-field-path { white-space: pre-wrap; overflow-wrap: anywhere; }
.revision-field-path, .revision-field-reference { color: var(--tianshu-text-secondary); }
.revision-field-reference { display: block; font-size: 12px; }
.revision-field-note { margin: 12px 0; color: var(--tianshu-text-secondary); line-height: 1.6; overflow-wrap: anywhere; }
.revision-field-note summary { cursor: pointer; }
@media (max-width: 768px) {
  .revision-selector-field { flex-direction: column; align-items: stretch; }
}
</style>
