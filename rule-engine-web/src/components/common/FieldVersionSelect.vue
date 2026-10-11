<template>
  <el-select v-if="identity" class="fp-version-select" :model-value="selected" size="small" :loading="loading" :disabled="disabled" :title="title" aria-label="选择字段版本" @visible-change="load" @change="change">
    <el-option value="LATEST" :label="latestLabel" />
    <el-option v-for="entry in versions" :key="versionKey(entry)" :value="versionKey(entry)" :label="versionLabel(entry)" :disabled="entry.disabled" :title="entry.reason || (entry.versionSource === 'BUSINESS_VERSION' ? `使用业务 v${entry.displayVersion}，跟随该版本的覆盖发布` : `${entry.versionSource === 'FUNCTION_HISTORY' ? '接入治理前的历史版本，' : ''}固定使用快照 v${entry.version}`)" />
    <el-option v-if="selected !== 'LATEST' && !versions.some(entry => versionKey(entry) === selected)" :value="selected" :label="versionLabel(selection)" />
  </el-select>
  <span v-else>{{ fallback }}</span>
</template>
<script>
import { listFieldVersions, getFieldVersion, getFieldVersionFields } from '@/api/fieldPickerVersion'
import { resourceVersionIdentity } from '@/utils/resourceVersion'
import { versionDisplayLabel } from '@/utils/versionRestore'
export default {
  name: 'FieldVersionSelect',
  props: { item: { type: Object, default: null }, selection: { type: Object, default: () => ({}) }, disabled: Boolean },
  emits: ['change', 'loading'],
  data: () => ({ versions: [], loading: false, loaded: false, requestNumber: 0 }),
  computed: {
    identity() { return resourceVersionIdentity(this.item) },
    identityKey() { return this.identity ? `${this.identity.refType}:${this.identity.refId}:${this.identity.ownerId}` : '' },
    selected() { return this.selection.versionMode === 'FIXED' ? this.versionKey(this.selection) : 'LATEST' },
    latest() { return this.versions[0]?.version ?? this.item?._ref?.modelVersion ?? this.item?.function?.version ?? this.item?.varObj?.version },
    latestLabel() { const display = this.versions[0]?.displayVersion ?? this.latest; return `最新${display != null ? '·v' + display : ''}` },
    fallback() { return this.latest != null ? `v${this.latest}` : '—' },
    title() { return this.selected === 'LATEST' ? `跟随${this.latestLabel}` : `固定使用 ${this.versionLabel(this.selection)}` },
  },
  watch: { identityKey() { this.requestNumber++; this.versions = []; this.loaded = false; this.loading = false } },
  beforeUnmount() { this.requestNumber++; this.$emit('loading', false) },
  methods: {
    versionKey(entry) { return (entry.versionSource ? entry.versionSource + ':' : '') + String(entry.versionId) },
    versionLabel(entry) { return `${entry.versionSource === 'BUSINESS_VERSION' ? '业务 ' : entry.versionSource === 'FUNCTION_HISTORY' ? '旧' : ''}${versionDisplayLabel(entry)}` },
    async load(visible) {
      if (!visible) { this.loaded = false; return }
      if (!this.identity || this.loaded || this.loading) return
      const request = ++this.requestNumber
      this.loading = true
      try {
        const response = await listFieldVersions(this.identity)
        if (request !== this.requestNumber) return
        this.versions = response.data || []; this.loaded = true
      } catch (error) { if (request === this.requestNumber) this.$message?.error(error.message || '版本加载失败') }
      finally { if (request === this.requestNumber) this.loading = false }
    },
    async change(value) {
      if (this.disabled) return
      const request = ++this.requestNumber
      if (value === 'LATEST') { this.loading = false; this.$emit('loading', false); this.$emit('change', { versionMode: 'LATEST', versionId: null, version: this.latest, versionOwnerId: this.identity.ownerId }); return }
      this.loading = true
      this.$emit('loading', true)
      try {
        const selected = this.versions.find(entry => this.versionKey(entry) === value) || this.selection
        const versionId = selected.versionId || value
        const response = await (this.item._versionGroup ? getFieldVersionFields(this.identity, versionId, selected.versionSource) : getFieldVersion(this.identity, versionId, selected.versionSource))
        if (request !== this.requestNumber) return
        this.$emit('change', { ...response.data, versionMode: 'FIXED' })
      } catch (error) { if (request === this.requestNumber) this.$message?.error(error.message || '该版本中没有此字段，请选择其他版本') }
      finally { if (request === this.requestNumber) { this.loading = false; this.$emit('loading', false) } }
    },
  },
}
</script>
<style scoped>
.fp-version-select { width:100%; }
.fp-version-select :deep(.el-select__wrapper) { border-radius:0; min-height:26px; padding:2px 6px; font-size:11px; }
</style>
