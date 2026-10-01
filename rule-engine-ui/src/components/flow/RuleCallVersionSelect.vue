<template>
  <div class="rule-call-version">
    <span>调用版本</span>
    <el-select :model-value="versionMode === 'FIXED' ? String(versionBindingId || '') : 'LATEST'" :loading="loading" :disabled="!ruleId"
      size="small" aria-label="调用规则版本" @change="select">
      <el-option value="LATEST" label="最新版（默认，随发布更新）" />
      <el-option v-for="version in versions" :key="String(version.bindingId)" :value="String(version.bindingId)"
        :label="`指定版本 v${version.version}`" :disabled="version.available === false" />
    </el-select>
    <span v-if="error" class="version-error">版本读取失败 <el-button link size="small" @click="load">重试</el-button></span>
    <small v-else-if="versions.some(version => version.available === false)">部分历史版本缺少可验证制品，覆盖发布补全后才可指定调用。</small>
  </div>
</template>
<script>
import { listPublishedVersions } from '@/api/definition'
export default {
  name: 'RuleCallVersionSelect',
  props: { ruleId: { type: [String, Number], default: null }, versionMode: { type: String, default: 'LATEST' }, versionBindingId: { type: [String, Number], default: null } },
  emits: ['change', 'fields'],
  data: () => ({ versions: [], loading: false, error: false, requestSequence: 0 }),
  watch: { ruleId: { immediate: true, handler() { this.load() } }, versionBindingId() { this.emitFields() } },
  beforeUnmount() { this.requestSequence++ },
  methods: {
    async load() {
      const sequence = ++this.requestSequence
      this.versions = []; this.error = false
      if (!this.ruleId) { this.loading = false; return }
      this.loading = true
      try {
        const response = await listPublishedVersions(String(this.ruleId))
        if (sequence !== this.requestSequence) return
        this.versions = Array.isArray(response?.data) ? response.data : []
        this.emitFields()
      } catch { if (sequence === this.requestSequence) this.error = true }
      finally { if (sequence === this.requestSequence) this.loading = false }
    },
    select(value) {
      if (value !== 'LATEST' && !this.versions.some(version => String(version.bindingId) === value && version.available !== false)) return
      this.$emit('change', { versionMode: value === 'LATEST' ? 'LATEST' : 'FIXED', versionBindingId: value === 'LATEST' ? null : value })
    },
    emitFields() {
      this.versions.forEach(version => this.$emit('fields', { bindingId: String(version.bindingId), outputFields: version.outputFields || [] }))
    },
  },
}
</script>
<style scoped>
.rule-call-version { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 8px 0; color: var(--tianshu-text-secondary); }
.rule-call-version .el-select { width: 240px; }
.version-error { color: var(--tianshu-warning-text); }
</style>
