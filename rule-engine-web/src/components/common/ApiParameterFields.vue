<template>
  <div class="api-parameter-fields">
    <el-input v-if="fields.length > 20" v-model="query" clearable placeholder="搜索配置字段" @input="page = 1" />
    <div class="api-parameter-table-wrap">
      <el-table :data="visibleFields" border size="small" row-key="id" class="api-parameter-table">
        <el-table-column v-if="!output" label="字段位置" width="112"><template #default="{ row }">
          <el-select :model-value="requestFieldPosition(row).value" aria-label="字段位置" :title="requestFieldPosition(row).hint" @update:model-value="setPosition(row, $event)"><el-option v-for="position in positions" :key="position.value" :label="position.label" :value="position.value" :title="position.hint" /></el-select>
        </template></el-table-column>
        <el-table-column v-if="!output" label="数据格式" width="182"><template #default="{ row }">
          <el-select v-if="requestFieldPosition(row).value === 'BODY'" :model-value="row.location" aria-label="请求体格式" :title="requestFieldFormat(row).hint" @update:model-value="setBodyFormat($event)"><el-option v-for="format in bodyFormats" :key="format.value" :label="format.label" :value="format.value" :title="format.hint" /></el-select>
          <span v-else class="request-field-meta" :title="requestFieldFormat(row).hint">{{ requestFieldFormat(row).label }}</span>
        </template></el-table-column>
        <el-table-column :label="output ? '字段路径' : '请求字段'" min-width="220"><template #default="{ row }"><protocol-path-picker v-model:value="row.path" :paths="fieldPaths(row)" :context-label="output ? '响应输出结构' : '请求字段结构'" :placeholder="output || row.location === 'JSON' ? '如 mobile 或 customer.mobile' : row.location === 'HEADER' ? '如 Authorization' : row.location === 'COOKIE' ? '如 session_id' : '如 mobile'" /></template></el-table-column>
        <el-table-column label="取值 / 表达式" min-width="300"><template #default="{ row }"><api-value-editor v-model:value="row.value" :vars="vars" :functions="functions" :modules="modules" /></template></el-table-column>
        <el-table-column v-if="!output && allowOverrides" label="允许覆盖" width="100"><template #default="{ row }"><el-checkbox v-model="row.overridable" :disabled="row.location === 'HEADER'" :aria-label="'允许覆盖 ' + (row.path || '请求字段')" :title="row.location === 'HEADER' ? '请求头由 API 统一维护' : '允许变量和数据对象为本次调用配置不同的取值'" /></template></el-table-column>
        <el-table-column v-if="!output" label="必填" width="72"><template #default="{ row }"><el-checkbox v-model="row.required" :aria-label="'必填 ' + row.path" /></template></el-table-column>
        <el-table-column v-if="!output" label="缺失值处理" width="150"><template #default="{ row }"><el-select v-model="row.nullPolicy" aria-label="缺失值处理"><el-option label="跟随默认" value="DEFAULT" /><el-option label="不传空值" value="OMIT" /><el-option label="传 null" value="NULL" /><el-option label="传空字符串" value="EMPTY" /></el-select></template></el-table-column>
        <el-table-column v-if="!output" label="默认值" min-width="220"><template #default="{ row }"><el-input :model-value="defaultValueText(row)" :aria-label="'默认值 ' + row.path" placeholder="留空表示不转换；填写后按该内容处理" @update:model-value="setDefaultValue(row, $event)" /></template></el-table-column>
        <el-table-column v-if="!output && validationOptions.length" label="输入校验" min-width="220"><template #default="{ row }"><el-select v-model="row.validationRuleIds" multiple collapse-tags collapse-tags-tooltip clearable filterable :aria-label="'输入校验 ' + (row.path || '请求字段')" placeholder="可选"><el-option v-for="item in validationOptions" :key="item.id" :label="validationLabel(item)" :value="item.id" /></el-select></template></el-table-column>
        <el-table-column label="备注说明" min-width="160"><template #default="{ row }"><el-input v-model="row.remark" :aria-label="'备注说明 ' + row.path" placeholder="字段用途或取值说明" /></template></el-table-column>
        <el-table-column label="操作" :width="operationColumnWidth(['删除'])"><template #default="{ row }"><el-button link type="danger" @click="remove(row.id)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <p v-if="!output" class="request-fields-help">字段名按对方接口要求填写，不加格式前缀。同一请求体共用数据格式，切换时同步本表中的请求体字段；默认值留空表示不转换，填写后按输入内容处理。文件默认不预处理，需要时在取值表达式中组合 imageToBase64WithOptions。</p>
    <el-pagination v-if="filteredFields.length > 20" v-model:current-page="page" :page-size="20" :total="filteredFields.length" layout="total, prev, pager, next" />
    <el-button class="add-field" @click="add">添加字段</el-button>
  </div>
</template>
<script>
import ApiValueEditor from './ApiValueEditor.vue'
import ProtocolPathPicker from './ProtocolPathPicker.vue'
import { newApiField, API_MODULE_FIELDS } from '@/utils/apiExecution'
import { API_REQUEST_POSITIONS, API_BODY_FORMATS, requestFieldPosition, requestFieldFormat } from '@/utils/apiRequestFields'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
export default {
  components: { ApiValueEditor, ProtocolPathPicker },
  props: { fields: { type: Array, required: true }, paths: { type: Array, default: () => [] }, output: Boolean, allowOverrides: { type: Boolean, default: true }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, modules: { type: Array, default: () => API_MODULE_FIELDS }, validationOptions: { type: Array, default: () => [] } },
  emits: ['update:fields'],
  data: () => ({ positions: API_REQUEST_POSITIONS, bodyFormats: API_BODY_FORMATS, query: '', page: 1 }),
  computed: {
    filteredFields() { return this.fields.filter(field => (field.path || '').toLowerCase().includes(this.query.trim().toLowerCase())) },
    visibleFields() { return this.filteredFields.slice((this.page - 1) * 20, this.page * 20) },
  },
  methods: {
    fieldPaths(row) { return this.paths.filter(path => !path.location || path.location === row.location) },
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    requestFieldPosition, requestFieldFormat,
    validationLabel(item) { return `${item.builtIn ? '【系统内置】' : ''}${item.validationName}（${item.validationType}）` },
    setPosition(field, position) { if (field.file && position !== 'BODY') return; field.location = position === 'BODY' ? this.fields.find(item => requestFieldPosition(item).value === 'BODY')?.location || 'JSON' : position; if (position === 'HEADER') field.overridable = false },
    setBodyFormat(format) { this.fields.filter(field => requestFieldPosition(field).value === 'BODY').forEach(field => { field.location = format }) },
    defaultValueText(field) { const value = field?.defaultValue; if (value == null) return ''; if (value && typeof value === 'object' && value.kind === 'LITERAL') return value.value == null ? '' : String(value.value); return typeof value === 'object' ? JSON.stringify(value) : String(value) },
    setDefaultValue(field, value) { const text = String(value ?? ''); if (!text) delete field.defaultValue; else field.defaultValue = text },
    add() { this.query = ''; this.page = Math.ceil((this.fields.length + 1) / 20); const field = newApiField(); if (!this.output) field.location = this.fields.find(item => requestFieldPosition(item).value === 'BODY')?.location || 'JSON'; this.$emit('update:fields', [...this.fields, field]) },
    remove(id) { this.$emit('update:fields', this.fields.filter(field => field.id !== id)); if ((this.page - 1) * 20 >= this.filteredFields.length - 1) this.page = Math.max(1, this.page - 1) },
  },
}
</script>
<style scoped>
.api-parameter-fields { width: 100%; min-width: 0; }
.api-parameter-table-wrap { min-width: 0; max-width: 100%; overflow-x: auto; }
.api-parameter-table { width: 100%; min-width: 680px; }
.api-parameter-table :deep(.el-table__cell) { padding: 8px 10px; }
.api-parameter-table :deep(.cell) { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.api-parameter-table :deep(.el-input), .api-parameter-table :deep(.api-value-editor) { width: 100%; min-width: 0; }
.api-parameter-table :deep(.el-select) { width: 100%; }
.request-field-meta { white-space: nowrap; color: var(--el-text-color-secondary); }
.request-fields-help { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.add-field { margin-top: 10px; }
</style>
