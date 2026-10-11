<template>
  <el-select :model-value="selected" :loading="loading" :disabled="disabled || !resourceId" aria-label="资源执行版本" @visible-change="load" @change="select">
    <el-option value="LATEST" label="最新版（当前生产）" />
    <el-option v-for="row in versions" :key="row.versionId" :value="String(row.versionId)" :label="`指定 v${row.version}`" />
    <el-option v-if="modelValue.versionMode === 'FIXED' && !selectedVersion" :value="selected" :label="selectionNotice" disabled />
  </el-select>
</template>
<script>
import { getResourceVersions } from '@/api/resourceVersions'
export default {
  name: 'ResourceVersionSelect',
  props: { resourceType: { type: String, required: true }, resourceId: { type: [String, Number], default: null }, modelValue: { type: Object, default: () => ({ versionMode: 'LATEST', versionId: null }) }, disabled: Boolean },
  emits: ['update:modelValue', 'change'], data: () => ({ versions: [], history: [], loading: false, loaded: false, error: false, sequence: 0 }),
  computed: {
    selectedVersion() {
      if (this.modelValue.versionMode !== 'FIXED') return null
      const direct = this.versions.find(row => String(row.versionId) === String(this.modelValue.versionId))
      const anchor = this.history.find(row => String(row.id) === String(this.modelValue.versionId))
      return direct || (anchor && this.versions.find(row => String(row.version) === String(anchor.versionNo)))
    },
    selected() { return this.modelValue.versionMode === 'FIXED' ? String(this.selectedVersion?.versionId ?? this.modelValue.versionId) : 'LATEST' },
    selectionNotice() { return this.error ? '所选版本暂时无法核验' : this.loaded ? '所选版本已删除或不可用' : '指定版本（待核验）' },
    identity() { return `${this.resourceType}:${this.resourceId}` },
  },
  watch: { identity() { this.sequence++; this.versions = []; this.history = []; this.loading = false; this.loaded = false; this.error = false; if (this.modelValue.versionMode === 'FIXED') this.load(true) } },
  mounted() { if (this.modelValue.versionMode === 'FIXED') this.load(true) },
  beforeUnmount() { this.sequence++ },
  methods: {
    async load(open) {
      if (!open || !this.resourceId || this.loading) return
      const sequence = ++this.sequence; this.loading = true; this.error = false
      try {
        const response = await getResourceVersions(this.resourceType, this.resourceId)
        if (sequence !== this.sequence) return
        this.versions = (response.data?.versions || []).filter(row => !row.deleted && ['ACTIVE', 'PUBLISHED', 'ENABLED', '1'].includes(row.status))
        this.history = response.data?.history || []; this.loaded = true
      } catch (error) { if (sequence === this.sequence) { this.versions = []; this.history = []; this.error = true; this.$message.error(error.message || '版本读取失败') } }
      finally { if (sequence === this.sequence) this.loading = false }
    },
    select(value) { if (this.disabled || !this.resourceId || value !== 'LATEST' && !this.versions.some(row => String(row.versionId) === value)) return; const selection = { versionMode: value === 'LATEST' ? 'LATEST' : 'FIXED', versionId: value === 'LATEST' ? null : value }; this.$emit('update:modelValue', selection); this.$emit('change', selection) },
  },
}
</script>
