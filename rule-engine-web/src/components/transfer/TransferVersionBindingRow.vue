<template>
  <section class="version-binding-row">
    <div class="version-binding-source">
      <strong>{{ source.label || source.refType + ' #' + source.refId }}</strong>
      <span>{{ source.versionSource === 'LATEST' ? '跟随最新版' : '指定版本 ' + (source.version != null ? 'v' + source.version : '#' + source.versionId) }} · {{ source.sourceResourceKey }}</span>
      <code>{{ source.path || '早期版本绑定' }}</code>
    </div>
    <div class="version-binding-target">
      <remote-filter-select :value="modelValue.resourceId || ''" :selected-option="selectedResourceOption" :fetch-options="fetchResources" option-label-key="label" option-value-key="id" placeholder="选择目标资源" aria-label="固定版本目标资源" :disabled="disabled" @change="selectResource" />
      <template v-if="source.versionSource !== 'LATEST'">
        <el-select :model-value="modelValue.versionId == null ? '' : String(modelValue.versionId)" :disabled="disabled || !modelValue.resourceId" :loading="loading" aria-label="目标业务版本" placeholder="选择目标版本" @visible-change="load" @change="selectVersion">
          <el-option v-for="option in versions" :key="option.versionId" :value="String(option.versionId)" :label="`v${option.version}${option.revision ? ' · 修订 ' + option.revision : ''}`" />
        </el-select>
        <el-select v-if="needsField" :model-value="modelValue.fieldId == null ? '' : String(modelValue.fieldId)" :disabled="disabled || !selectedVersion" aria-label="目标版本字段" placeholder="选择版本内字段" @change="selectField">
          <el-option v-for="field in selectedVersion?.fields || []" :key="field.id" :value="String(field.id)" :label="`${field.label} · ${field.valueType || '未声明类型'}`" />
        </el-select>
      </template>
      <el-tag v-else type="info">保持最新版策略</el-tag>
    </div>
    <p v-if="error || !bindingValidated" role="status" class="version-binding-message">{{ error || (!source.valid && source.message) || '选择已变更，请重新预检' }}</p>
    <el-tag v-else type="success" size="small">归属、类型和修订校验通过</el-tag>
  </section>
</template>
<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listTransferResources, listTransferVersionCandidates } from '@/api/transfer'
export default {
  name: 'TransferVersionBindingRow', components: { RemoteFilterSelect },
  props: { source: { type: Object, required: true }, modelValue: { type: Object, default: () => ({}) }, disabled: Boolean },
  emits: ['update:modelValue'], data: () => ({ versions: [], loading: false, sequence: 0, error: '' }),
  computed: {
    identity() { return `${this.source.referenceKey}:${this.modelValue.resourceId || ''}` },
    selectedVersion() { return this.versions.find(row => String(row.versionId) === String(this.modelValue.versionId)) },
    needsField() { return ['MODEL_OUTPUT', 'DATA_OBJECT'].includes(this.source.refType) },
    bindingValidated() { return this.source.valid && this.source.binding && ['resourceId', 'versionId', 'fieldId', 'expectedSnapshotId'].every(key => String(this.source.binding[key] ?? '') === String(this.modelValue[key] ?? '')) },
    selectedResourceOption() { const target = this.source.target; return target && String(target.resourceId) === String(this.modelValue.resourceId) ? { id: this.modelValue.resourceId, label: target.ownerLabel || target.label || target.code || String(target.resourceId) } : null },
  },
  watch: { identity() { this.sequence++; this.versions = []; this.loading = false; this.error = ''; if (this.modelValue.resourceId) this.load(true) } },
  mounted() { if (this.modelValue.resourceId) this.load(true) },
  beforeUnmount() { this.sequence++ },
  methods: {
    async fetchResources({ query, pageNum, pageSize }) {
      const type = this.source.targetResourceType
      const response = await listTransferResources({ nodeType: ({ EXTERNAL_API: 'API', DATABASE: 'DB', SFTP: 'SFTP', EXTERNAL_DATASOURCE: 'DATASOURCE' })[type] || type, keyword: query, pageNum, pageSize })
      return { ...response.data, records: (response.data?.records || []).map(row => ({ id: row.id, label: `${row.displayName || row.name || row.code} · ${row.scope === 'GLOBAL' ? '全局' : row.projectName || '项目 ' + row.projectId}` })) }
    },
    selectResource(id) { this.$emit('update:modelValue', { resourceId: id || null, versionId: null, fieldId: null, expectedSnapshotId: null }) },
    async load(open) {
      if (!open || !this.modelValue.resourceId || this.source.versionSource === 'LATEST') return
      const sequence = ++this.sequence; this.loading = true; this.error = ''
      try { const response = await listTransferVersionCandidates(this.source.refType, this.modelValue.resourceId); if (sequence === this.sequence) this.versions = response.data || [] }
      catch (error) { if (sequence === this.sequence) this.error = error.message || '目标版本读取失败' }
      finally { if (sequence === this.sequence) this.loading = false }
    },
    selectVersion(id) {
      const version = this.versions.find(row => String(row.versionId) === id)
      if (!version) return
      this.$emit('update:modelValue', { resourceId: this.modelValue.resourceId, versionId: id, expectedSnapshotId: version.snapshotId, fieldId: null })
    },
    selectField(id) { this.$emit('update:modelValue', { ...this.modelValue, fieldId: id }) },
  },
}
</script>
<style scoped>
.version-binding-row { border: 1px solid var(--el-border-color); border-radius: 8px; padding: 16px; margin: 12px 0; }
.version-binding-source { display: flex; flex-wrap: wrap; gap: 8px 16px; margin-bottom: 12px; }
.version-binding-source span, .version-binding-source code { color: var(--tianshu-text-secondary); overflow-wrap: anywhere; }
.version-binding-target { display: flex; flex-wrap: wrap; gap: 12px; }
.version-binding-target :deep(.el-select) { width: 260px; max-width: 100%; }
.version-binding-message { color: var(--el-color-warning); font-size: 13px; margin: 8px 0 0; }
</style>
