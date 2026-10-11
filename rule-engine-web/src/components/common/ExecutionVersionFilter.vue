<template>
  <el-form-item label="版本范围">
    <div class="execution-version-filter">
      <remote-filter-select :value="modelValue.ruleId || ''" :fetch-options="fetchRules" option-label-key="ruleName" option-value-key="id" placeholder="选择规则" aria-label="版本筛选规则" @change="selectRule" />
      <el-select :model-value="selected" :disabled="!modelValue.ruleId" :loading="loading" aria-label="执行版本范围" @visible-change="load" @change="selectVersion">
        <el-option value="ALL" label="全部版本" /><el-option value="LATEST" label="最新版（当前生产）" />
        <el-option v-for="row in versions" :key="row.bindingId" :value="String(row.version)" :label="`指定 v${row.version}`" />
      </el-select>
    </div>
  </el-form-item>
</template>
<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listDefinitions, listVersions } from '@/api/definition'
export default {
  name: 'ExecutionVersionFilter', components: { RemoteFilterSelect },
  props: { modelValue: { type: Object, required: true } }, emits: ['update:modelValue'],
  data: () => ({ versions: [], loading: false, sequence: 0 }),
  computed: { selected() { return this.modelValue.versionMode === 'FIXED' ? String(this.modelValue.ruleVersion) : this.modelValue.versionMode || 'ALL' } },
  watch: { 'modelValue.ruleId'() { this.sequence++; this.versions = []; this.loading = false } },
  beforeUnmount() { this.sequence++ },
  methods: {
    async fetchRules(params) { const response = await listDefinitions({ pageNum: params.pageNum, pageSize: params.pageSize, ruleName: params.query }); return response.data },
    selectRule(ruleId) { this.$emit('update:modelValue', { ...this.modelValue, ruleId: ruleId || null, versionMode: 'ALL', ruleVersion: null }) },
    selectVersion(value) { this.$emit('update:modelValue', { ...this.modelValue, versionMode: ['ALL', 'LATEST'].includes(value) ? value : 'FIXED', ruleVersion: ['ALL', 'LATEST'].includes(value) ? null : Number(value) }) },
    async load(open) {
      if (!open || !this.modelValue.ruleId || this.loading) return
      const sequence = ++this.sequence; this.loading = true
      try {
        const response = await listVersions(this.modelValue.ruleId)
        if (sequence === this.sequence) this.versions = [...new Set((response.data || []).map(row => row.businessVersion ?? row.version))]
          .filter(version => Number(version) > 0).sort((a, b) => Number(b) - Number(a)).map(version => ({ bindingId: String(version), version }))
      }
      catch (error) { if (sequence === this.sequence) this.$message.error(error.message || '版本读取失败') }
      finally { if (sequence === this.sequence) this.loading = false }
    },
  },
}
</script>
<style scoped>
.execution-version-filter { display: flex; flex-wrap: wrap; gap: 8px; }
.execution-version-filter :deep(.el-select) { width: 190px; max-width: 100%; }
</style>
