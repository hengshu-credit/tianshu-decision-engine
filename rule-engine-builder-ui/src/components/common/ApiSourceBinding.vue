<template>
  <section v-if="apiId" class="api-source-binding" aria-label="外数入参覆盖">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p>相同 API 与相同请求入参在本次规则及子规则内共用结果；覆盖为不同入参会形成独立调用。</p>
    <div class="api-binding-table-wrap">
    <el-table :data="fields" border size="small" row-key="id" class="api-binding-table">
      <el-table-column label="请求字段" min-width="160"><template #default="{ row }"><code>{{ row.location }} · {{ row.path }}</code></template></el-table-column>
      <el-table-column label="默认绑定" min-width="160"><template #default="{ row }">{{ describe(row.value) }}</template></el-table-column>
      <el-table-column label="覆盖" width="70"><template #default="{ row }"><el-checkbox :model-value="Object.hasOwn(value, row.id)" @update:model-value="toggle(row, $event)" /></template></el-table-column>
      <el-table-column label="覆盖取值" min-width="280"><template #default="{ row }"><api-value-editor v-if="Object.hasOwn(value, row.id)" :value="value[row.id]" :vars="vars" :functions="functions" :modules="[]" @update:value="set(row.id, $event)" /><span v-else>使用 API 默认配置</span></template></el-table-column>
    </el-table>
    </div>
    <p v-if="!fields.length">此 API 未开放可覆盖参数，请在 API 请求字段上勾选“允许变量/对象覆盖”。</p>
    <div class="api-binding-result-picker">
      <span class="api-binding-result-picker__label">结果模块</span>
      <el-select v-model="selectedPath" filterable :filter-method="query => moduleQuery = query" placeholder="选择外数结果模块" @change="$emit('select-path', $event)"><el-option v-for="item in visibleModules" :key="item.value" :value="item.value" :label="item.label + ' · ' + item.value" /></el-select>
    </div>
    <p v-if="modules.length > 100">已载入 {{ modules.length }} 个路径，可输入字段名搜索，每次显示最多 100 项。</p>
  </section>
</template>
<script>
import request from '@/api/request'
import ApiValueEditor from './ApiValueEditor.vue'
import { API_MODULE_FIELDS, filterApiPaths } from '@/utils/apiExecution'
export default {
  components: { ApiValueEditor },
  props: { apiId: { type: [String, Number], default: '' }, value: { type: Object, default: () => ({}) }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] } },
  emits: ['update:value', 'select-path'],
  data: () => ({ fields: [], error: '', modules: API_MODULE_FIELDS, selectedPath: '', moduleQuery: '' }),
  computed: { visibleModules() { return filterApiPaths(this.modules, this.moduleQuery, this.selectedPath) } },
  watch: { apiId: { immediate: true, async handler(id) {
    this.fields = []; this.error = ''; if (!id) return
    try { const response = await request.get(`/rule/datasource/api-config/${id}/binding-contract`); if (String(id) !== String(this.apiId)) return; this.fields = (response.data?.requestFields || []).filter(field => field.overridable); this.modules = [...new Map([...API_MODULE_FIELDS, ...(response.data?.resultFields || [])].map(field => [field.value, field])).values()] }
    catch (error) { this.error = error.message || 'API 入参定义加载失败' }
  } } },
  methods: {
    describe(operand) { if (!operand) return '未设置'; return operand.label || operand.code || operand.value || operand.kind },
    set(id, operand) { this.$emit('update:value', { ...this.value, [id]: operand }) },
    toggle(field, enabled) { const next = { ...this.value }; if (enabled) next[field.id] = field.value ? JSON.parse(JSON.stringify(field.value)) : null; else delete next[field.id]; this.$emit('update:value', next) },
  },
}
</script>
<style scoped>
.api-source-binding { container-type: inline-size; display: grid; gap: 12px; width: 100%; min-width: 0; flex: 1 1 100%; box-sizing: border-box; padding: 12px; background: var(--el-fill-color-light); border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.api-source-binding p { margin: 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }
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
