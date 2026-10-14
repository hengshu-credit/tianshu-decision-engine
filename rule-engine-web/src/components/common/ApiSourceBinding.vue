<template>
  <section v-if="apiId" class="api-source-binding" aria-label="外数入参覆盖">
    <div class="api-binding-toolbar"><strong>{{ apiName || 'API 取值配置' }}</strong><span v-if="!loading">{{ fields.filter(field => field.overridable).length }} 项可覆盖</span><a :href="apiLink" target="_blank" rel="noopener">配置 API 入参 ↗</a><el-button link size="small" :loading="loading" @click="loadContract">刷新</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p>{{ bindingMode === 'INPUT' ? '覆盖本次调用的输入值，保留 API 原有请求映射；未覆盖的输入保持原值。' : '仅已开放的请求字段允许覆盖；未勾选时沿用 API 默认配置。' }}相同 API 与相同入参在本次规则内共用结果。</p>
    <div v-if="fields.length || loading" v-loading="loading" class="api-binding-table-wrap">
    <el-table :data="fields" border size="small" row-key="id" class="api-binding-table">
      <el-table-column label="覆盖位置" width="100"><template #default="{ row }"><span :title="requestFieldPosition(row).hint">{{ requestFieldPosition(row).label }}</span></template></el-table-column>
      <el-table-column label="数据格式" min-width="210"><template #default="{ row }"><span :title="requestFieldFormat(row).hint">{{ requestFieldFormat(row).label }}</span></template></el-table-column>
      <el-table-column v-if="fields.some(field => field.file)" label="字段内容" width="160"><template #default="{ row }"><span :title="requestFieldContent(row).hint">{{ requestFieldContent(row).label }}</span></template></el-table-column>
      <el-table-column :label="bindingMode === 'INPUT' ? '调用入参' : '请求字段'" min-width="160"><template #default="{ row }"><code :title="row.path">{{ row.path }}</code></template></el-table-column>
      <el-table-column label="默认绑定" min-width="160"><template #default="{ row }"><span :title="describe(row.value)">{{ bindingMode === 'INPUT' ? '原调用入参' : row.location === 'HEADER' ? '由 API 统一维护' : describe(row.value) }}</span></template></el-table-column>
      <el-table-column label="覆盖" width="80"><template #default="{ row }"><el-checkbox v-if="row.overridable || Object.hasOwn(activeValue, row.id)" :model-value="Object.hasOwn(activeValue, row.id)" :aria-label="'覆盖 ' + row.path" @update:model-value="toggle(row, $event)" /><span v-else title="请在 API 请求字段中开启允许覆盖">未开放</span></template></el-table-column>
      <el-table-column label="覆盖取值" min-width="280"><template #default="{ row }"><api-value-editor v-if="row.overridable && Object.hasOwn(activeValue, row.id)" :value="activeValue[row.id]" :vars="vars" :functions="functions" :modules="[]" :expected-type="row.type || ''" @update:value="set(row.id, $event)" /><span v-else>{{ row.overridable ? '沿用默认' : '在 API 配置中维护' }}</span></template></el-table-column>
    </el-table>
    </div>
    <p v-else-if="!error">{{ bindingMode === 'INPUT' ? '尚未识别到调用入参，请在 API 请求映射中引用输入字段，或配置请求字段。' : '尚未配置请求字段，请先到 API 中添加请求字段并开启“允许覆盖”，生效后点击刷新。' }}</p>
    <div v-if="invalidKeys.length || hasWrongBinding" class="api-binding-invalid"><span>部分覆盖字段已删除、调整或不再开放，请重新配置。</span><el-button link type="warning" @click="clearInvalid">清除失效覆盖</el-button></div>
    <div class="api-binding-result-picker">
      <span class="api-binding-result-picker__label">结果路径</span>
      <protocol-path-picker :value="resultPath" :paths="modules" :api-id="apiId" :context-label="apiName || '当前外数 API'" placeholder="选择模块、子字段或输入路径" @update:value="$emit('select-path', $event)" />
    </div>
  </section>
</template>
<script>
import request from '@/api/request'
import ApiValueEditor from './ApiValueEditor.vue'
import ProtocolPathPicker from './ProtocolPathPicker.vue'
import { apiStagePaths, uniqueApiPaths } from '@/utils/apiContext'
import { operandDisplay } from '@/utils/operand'
import { metadataResponse } from '@/utils/metadataResponse'
import { requestFieldPosition, requestFieldFormat, requestFieldContent } from '@/utils/apiRequestFields'
export default {
  components: { ApiValueEditor, ProtocolPathPicker },
  props: { apiId: { type: [String, Number], default: '' }, value: { type: Object, default: () => ({}) }, inputOverrides: { type: Object, default: () => ({}) }, resultPath: { type: String, default: '' }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] } },
  emits: ['update:value', 'update:inputOverrides', 'select-path'],
  data: () => ({ fields: [], error: '', modules: [], apiName: '', bindingMode: 'FIELDS', loading: false, requestNumber: 0 }),
  computed: {
    activeValue() { return (this.bindingMode === 'INPUT' ? this.inputOverrides : this.value) || {} },
    invalidKeys() { return this.loading || this.error ? [] : Object.keys(this.activeValue).filter(id => !this.fields.some(field => String(field.id) === id && field.overridable)) },
    hasWrongBinding() { return !this.loading && !this.error && Object.keys((this.bindingMode === 'INPUT' ? this.value : this.inputOverrides) || {}).length > 0 },
    apiLink() { const router = '$router' in this ? this.$router : null; return router?.resolve?.({ name: 'ApiDetail', params: { id: this.apiId } })?.href || `#/datasource/api/${this.apiId}` },
  },
  watch: { apiId: { immediate: true, handler() { this.loadContract() } } },
  beforeUnmount() { this.requestNumber++ },
  methods: {
    requestFieldPosition, requestFieldFormat, requestFieldContent,
    async loadContract() {
      const sequence = ++this.requestNumber, id = this.apiId
      this.fields = []; this.modules = []; this.error = ''; this.apiName = ''; this.loading = !!id
      if (!id) return
      try {
        const response = await request.get(`/rule/datasource/api-config/${id}/binding-contract`, metadataResponse)
        if (sequence !== this.requestNumber) return
        const contract = response.data || {}
        this.bindingMode = contract.bindingMode || 'FIELDS'; this.apiName = contract.apiName || contract.apiCode || ''
        const fields = this.bindingMode === 'INPUT' ? contract.inputFields : contract.requestFields
        this.fields = [...new Map((fields || []).map(field => [String(field.id), { ...field, overridable: field.location !== 'HEADER' && field.overridable === true }])).values()]
        this.modules = uniqueApiPaths([...apiStagePaths(contract, 'RESULT'), ...(contract.resultFields || [])])
      } catch (error) { if (sequence === this.requestNumber) this.error = error.message || 'API 入参定义加载失败' }
      finally { if (sequence === this.requestNumber) this.loading = false }
    },
    describe(operand) { if (operand == null) return '未设置'; return operand.kind ? operandDisplay(operand) : typeof operand === 'object' ? JSON.stringify(operand) : String(operand) },
    emitValue(value) { this.$emit(this.bindingMode === 'INPUT' ? 'update:inputOverrides' : 'update:value', value) },
    set(id, operand) { this.emitValue({ ...this.activeValue, [id]: operand }) },
    toggle(field, enabled) { if (enabled && !field.overridable) return; const next = { ...this.activeValue }; if (enabled) next[field.id] = field.value !== null && field.value !== undefined ? JSON.parse(JSON.stringify(field.value)) : null; else delete next[field.id]; this.emitValue(next) },
    clearInvalid() {
      this.emitValue(Object.fromEntries(Object.entries(this.activeValue).filter(([key]) => !this.invalidKeys.includes(key))))
      if (this.hasWrongBinding) this.$emit(this.bindingMode === 'INPUT' ? 'update:value' : 'update:inputOverrides', {})
    },
  },
}
</script>
<style scoped>
.api-source-binding { container-type: inline-size; display: grid; gap: 12px; width: 100%; min-width: 0; flex: 1 1 100%; box-sizing: border-box; padding: 12px; background: var(--el-fill-color-light); border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.api-source-binding p { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }
.api-binding-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:12px; font-size:12px; }
.api-binding-toolbar > a { margin-left:auto; color:var(--el-color-primary); text-decoration:none; }
.api-binding-toolbar > span { color:var(--el-text-color-secondary); }
.api-binding-invalid { display:flex; align-items:center; gap:8px; color:var(--el-color-warning); font-size:12px; }
.api-binding-table-wrap { width: 100%; min-width: 0; max-height: min(46vh, 520px); overflow: auto; }
.api-binding-table { width: 100%; min-width: 720px; }
.api-binding-table :deep(.el-table__cell) { padding: 8px 10px; }
.api-binding-table :deep(.cell) { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.api-binding-table code { display: block; overflow: hidden; color: var(--el-text-color-primary); text-overflow: ellipsis; white-space: nowrap; }
.api-binding-result-picker { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 8px; align-items: center; min-width: 0; }
.api-binding-result-picker__label { color: var(--el-text-color-secondary); font-size: 12px; }
.api-binding-result-picker :deep(.el-select) { width: 100%; min-width: 0; }
@container (max-width: 760px) { .api-binding-result-picker { grid-template-columns: 1fr; gap: 5px; } .api-binding-table { min-width: 640px; } }
</style>
