<template>
  <div class="rule-call-version" :class="{ 'rule-call-version--compact': compact }">
    <span v-if="!compact">调用版本</span>
    <el-select v-if="catalogMode === 'LIVE'" :model-value="versionMode === 'FIXED' ? String(versionBindingId || '') : 'LATEST'" :loading="loading" :disabled="!ruleId"
      size="small" aria-label="调用规则版本" @change="select">
      <el-option value="LATEST" :label="latestLabel" />
      <el-option v-for="version in versions" :key="String(version.bindingId)" :value="String(version.bindingId)"
        :label="`v${version.version}`" :disabled="version.available === false" />
    </el-select>
    <span v-else data-testid="frozen-rule-call-version">{{ versionMode === 'FIXED' ? `指定版本${selectedFrozenVersion ? ' v' + selectedFrozenVersion.version : '（绑定标识未在冻结目录中核验）'}` : '最新版（原策略：随发布更新）' }}</span>
    <small v-if="catalogMode === 'FROZEN'">此处字段来自所选规则制品保存的目录，不查询当前环境同号规则。</small>
    <span v-if="error" class="version-error">版本读取失败 <el-button link size="small" @click="load">重试</el-button></span>
    <small v-else-if="!compact && catalogMode === 'LIVE' && versions.some(version => version.available === false)">部分历史版本缺少可验证制品，覆盖发布补全后才可指定调用。</small>
  </div>
</template>
<script>
import { listPublishedVersions } from '@/api/definition'
export default {
  name: 'RuleCallVersionSelect',
  props: { ruleId: { type: [String, Number], default: null }, versionMode: { type: String, default: 'LATEST' }, versionBindingId: { type: [String, Number], default: null },
    catalogMode: { type: String, default: 'LIVE' }, catalogKey: { type: String, default: '' }, frozenVersions: { type: Array, default: () => [] }, compact: Boolean },
  emits: ['change', 'fields'],
  data: () => ({ versions: [], loading: false, error: false, requestSequence: 0 }),
  computed: {
    latestLabel() {
      const latest = this.versions.reduce((result, item) => Number(item.version) > Number(result?.version || 0) ? item : result, null)
      return latest ? `最新 · v${latest.version}` : '最新版'
    },
    contextKey() { return `${this.catalogMode}:${this.catalogKey}:${this.ruleId || ''}` },
    selectedFrozenVersion() { return this.versions.find(version => String(version.bindingId) === String(this.versionBindingId)) }
  },
  watch: { contextKey: { immediate: true, handler() { this.load() } }, frozenVersions() { if (this.catalogMode === 'FROZEN') this.load() } },
  beforeUnmount() { this.requestSequence++ },
  methods: {
    async load() {
      const sequence = ++this.requestSequence
      const context = this.contextKey
      this.versions = []; this.error = false
      this.$emit('fields', { ruleId: String(this.ruleId || ''), versions: null, ...(this.catalogKey ? { catalogKey: this.catalogKey } : {}) })
      if (this.catalogMode !== 'LIVE') {
        this.loading = false
        this.versions = this.catalogMode === 'FROZEN' ? this.frozenVersions : []
        this.error = this.catalogMode === 'UNAVAILABLE'
        this.$emit('fields', { ruleId: String(this.ruleId || ''), versions: this.versions, ...(this.catalogKey ? { catalogKey: this.catalogKey } : {}) })
        return
      }
      if (!this.ruleId) { this.loading = false; return }
      this.loading = true
      try {
        const response = await listPublishedVersions(String(this.ruleId))
        if (sequence !== this.requestSequence || context !== this.contextKey) return
        this.versions = Array.isArray(response?.data) ? response.data : []
        this.$emit('fields', { ruleId: String(this.ruleId), versions: this.versions, ...(this.catalogKey ? { catalogKey: this.catalogKey } : {}) })
      } catch { if (sequence === this.requestSequence && context === this.contextKey) this.error = true }
      finally { if (sequence === this.requestSequence && context === this.contextKey) this.loading = false }
    },
    select(value) {
      if (this.catalogMode !== 'LIVE') return
      if (value !== 'LATEST' && !this.versions.some(version => String(version.bindingId) === value && version.available !== false)) return
      this.$emit('change', { versionMode: value === 'LATEST' ? 'LATEST' : 'FIXED', versionBindingId: value === 'LATEST' ? null : value })
    },
  },
}
</script>
<style scoped>
.rule-call-version { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 8px 0; color: var(--tianshu-text-secondary); }
.rule-call-version .el-select { width: 240px; }
.rule-call-version--compact { margin:0; gap:0; }
.rule-call-version--compact .el-select { width:100%; }
.rule-call-version--compact :deep(.el-select__wrapper) { border-radius:0; min-height:26px; padding:2px 6px; font-size:11px; }
.version-error { color: var(--tianshu-warning-text); }
</style>
