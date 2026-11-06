<template>
  <div class="resource-version-control">
    <el-select class="resource-version-select" :model-value="selected" :loading="loading" :disabled="disabled || !resourceId" :title="selectedLabel" aria-label="资源执行版本" @visible-change="load" @change="select">
      <el-option value="LATEST" label="最新版（当前生产）" />
      <el-option v-for="row in versions" :key="row.versionId" :value="String(row.versionId)" :label="versionLabel(row)" />
      <el-option v-if="modelValue.versionMode === 'FIXED' && !selectedVersion" :value="selected" :label="selectionNotice" disabled />
    </el-select>
    <el-button link type="primary" size="small" :disabled="disabled || !resourceId" @click.stop="openPreview">预览</el-button>
    <el-dialog v-model="previewVisible" title="所选版本预览" width="min(960px, 96vw)" append-to-body destroy-on-close>
      <async-state v-if="loading || error" compact :loading="loading" :error="error ? '版本读取失败' : ''" loading-text="正在读取版本…" @retry="load(true)" />
      <version-snapshot-panel v-else-if="previewRow?.snapshotJson" :resource-type="resourceType"
        :snapshot="previewRow.snapshotJson" :modified-label="`v${previewRow.versionNo} · 修订 ${previewRow.revisionNo}`" />
      <el-empty v-else description="所选版本没有可查看的快照" />
    </el-dialog>
  </div>
</template>
<script>
import AsyncState from './AsyncState.vue'
import VersionSnapshotPanel from './VersionSnapshotPanel.vue'
import { getResourceVersions } from '@/api/resourceVersions'
export default {
  name: 'ResourceVersionSelect',
  components: { VersionSnapshotPanel, AsyncState },
  props: { resourceType: { type: String, required: true }, resourceId: { type: [String, Number], default: null }, modelValue: { type: Object, default: () => ({ versionMode: 'LATEST', versionId: null }) }, disabled: Boolean },
  emits: ['update:modelValue', 'change'], data: () => ({ versions: [], history: [], latestId: null, previewVisible: false, loading: false, loaded: false, error: false, sequence: 0 }),
  computed: {
    previewRow() {
      const id = this.modelValue.versionMode === 'FIXED' ? this.selectedVersion?.versionId : this.latestId
      return id == null ? null : this.history.find(row => String(row.id) === String(id))
    },
    selectedVersion() {
      if (this.modelValue.versionMode !== 'FIXED') return null
      const direct = this.versions.find(row => String(row.versionId) === String(this.modelValue.versionId))
      const anchor = this.history.find(row => String(row.id) === String(this.modelValue.versionId))
      return direct || (anchor && this.versions.find(row => String(row.version) === String(anchor.versionNo)))
    },
    selected() { return this.modelValue.versionMode === 'FIXED' ? String(this.selectedVersion?.versionId ?? this.modelValue.versionId) : 'LATEST' },
    selectionNotice() { return this.error ? '所选版本暂时无法核验' : this.loaded ? '所选版本已删除或不可用' : '指定版本（待核验）' },
    selectedLabel() { return this.modelValue.versionMode === 'FIXED' ? this.selectedVersion ? this.versionLabel(this.selectedVersion) : this.selectionNotice : '最新版（当前生产）' },
    identity() { return `${this.resourceType}:${this.resourceId}` },
  },
  watch: { identity() { this.previewVisible = false; this.latestId = null; this.sequence++; this.versions = []; this.history = []; this.loading = false; this.loaded = false; this.error = false; if (this.modelValue.versionMode === 'FIXED') this.load(true) } },
  mounted() { if (this.modelValue.versionMode === 'FIXED') this.load(true) },
  beforeUnmount() { this.sequence++ },
  methods: {
    async openPreview() { this.previewVisible = true; await this.load(true) },
    async load(open) {
      if (!open || !this.resourceId || this.loading) return
      const sequence = ++this.sequence; this.loading = true; this.error = false
      try {
        const response = await getResourceVersions(this.resourceType, this.resourceId)
        if (sequence !== this.sequence) return
        this.versions = (response.data?.versions || []).filter(row => !row.deleted && ['ACTIVE', 'PUBLISHED', 'ENABLED', '1'].includes(String(row.status)))
        this.latestId = response.data?.latestVersionId
        this.history = response.data?.history || []; this.loaded = true
      } catch (error) { if (sequence === this.sequence) { this.versions = []; this.history = []; this.error = true; this.$message.error(error.message || '版本读取失败') } }
      finally { if (sequence === this.sequence) this.loading = false }
    },
    versionLabel(row) { return `指定 v${row.version}${row.revision ? ` · 修订 ${row.revision}` : ''}` },
    select(value) {
      if (this.disabled || !this.resourceId || value !== 'LATEST' && !this.versions.some(row => String(row.versionId) === value)) return
      const selectedVersion = this.versions.find(row => String(row.versionId) === String(value))
      const selection = { versionMode: value === 'LATEST' ? 'LATEST' : 'FIXED', versionId: value === 'LATEST' ? null : value }
      this.$emit('update:modelValue', selection)
      this.$emit('change', { ...selection, version: selectedVersion?.version ?? null, revision: selectedVersion?.revision ?? null, label: value === 'LATEST' ? '最新版（当前生产）' : selectedVersion ? this.versionLabel(selectedVersion) : this.selectionNotice })
    },
  },
}
</script>

<style scoped>
.resource-version-control { display: inline-flex; align-items: center; gap: 6px; width: 250px; max-width: 100%; min-width: 0; }
.resource-version-select { flex: 1; min-width: 0; }
.resource-version-select :deep(.el-select__selected-item) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
