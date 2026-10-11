<template>
  <div class="api-parameter-fields">
    <el-input v-if="fields.length > 20" v-model="query" clearable placeholder="搜索配置字段" @input="page = 1" />
    <div class="api-parameter-table-wrap">
    <el-table :data="visibleFields" border size="small" row-key="id" class="api-parameter-table">
      <el-table-column v-if="!output" label="字段位置" width="144"><template #default="{ row }">
        <el-select :model-value="requestFieldPosition(row).value" aria-label="字段位置" :title="requestFieldPosition(row).hint" @update:model-value="setPosition(row, $event)"><el-option v-for="position in positions" :key="position.value" :label="position.label" :value="position.value" :disabled="!!row.file && position.value !== 'BODY'" :title="row.file && position.value !== 'BODY' ? '文件转换仅支持请求体字段' : position.hint" /></el-select>
      </template></el-table-column>
      <el-table-column v-if="!output" label="数据格式" width="248"><template #default="{ row }">
        <el-select v-if="requestFieldPosition(row).value === 'BODY'" :model-value="row.location" aria-label="请求体格式" :title="requestFieldFormat(row).hint" @update:model-value="setBodyFormat($event)"><el-option v-for="format in bodyFormats" :key="format.value" :label="format.label" :value="format.value" :title="format.hint" /></el-select>
        <span v-else class="request-field-meta" :title="requestFieldFormat(row).hint">{{ requestFieldFormat(row).label }}</span>
      </template></el-table-column>
      <el-table-column v-if="!output" label="字段内容" width="180"><template #default="{ row }">
        <el-select v-if="requestFieldPosition(row).value === 'BODY'" :model-value="row.file?.mode || 'NONE'" aria-label="字段内容" :title="requestFieldContent(row).hint" @update:model-value="setFileMode(row, $event)"><el-option label="普通字段" value="NONE" /><el-option label="文件转 Base64" value="BASE64" /><el-option label="压缩后转 Base64" value="ZIP_BASE64" /></el-select>
        <span v-else class="request-field-meta">普通字段</span>
      </template></el-table-column>
      <el-table-column :label="output ? '字段路径' : '请求字段'" min-width="180"><template #default="{ row }"><el-input v-model="row.path" :title="row.path" :placeholder="output || row.location === 'JSON' ? '如 mobile 或 customer.mobile' : row.location === 'HEADER' ? '如 Authorization' : '如 mobile'" /></template></el-table-column>
      <el-table-column label="取值 / 表达式" min-width="300"><template #default="{ row }"><api-value-editor v-model:value="row.value" :vars="vars" :functions="functions" :modules="modules" /></template></el-table-column>
      <el-table-column v-if="!output && allowOverrides" label="允许覆盖" width="100"><template #default="{ row }"><el-checkbox v-model="row.overridable" :disabled="row.location === 'HEADER'" :aria-label="'允许覆盖 ' + (row.path || '请求字段')" :title="row.location === 'HEADER' ? '请求头由 API 统一维护' : '允许变量和数据对象为本次调用配置不同的取值'" /></template></el-table-column>
      <el-table-column v-if="!output" type="expand"><template #default="{ row }">
        <div class="api-field-settings">
          <el-checkbox v-model="row.required">必填</el-checkbox>
          <el-select v-model="row.nullPolicy" aria-label="传空策略"><el-option label="跟随默认" value="DEFAULT" /><el-option label="不传空值" value="OMIT" /><el-option label="传 null" value="NULL" /><el-option label="传空字符串" value="EMPTY" /></el-select>
          <span>缺失时默认值</span><api-value-editor v-model:value="row.defaultValue" :vars="vars" :functions="functions" :modules="modules" />
          <template v-if="row.file">
            <el-select v-model="row.file.kind" aria-label="文件内容类型"><el-option label="PDF 文件" value="PDF" /><el-option label="图片" value="IMAGE" /><el-option label="任意文件" value="ANY" /></el-select>
            <span>最大 KB</span><el-input-number :model-value="row.file.maxBytes / 1024" :min="1" :max="10240" @update:model-value="row.file.maxBytes = $event * 1024" />
            <template v-if="row.file.kind === 'IMAGE'"><span>最大宽高像素</span><el-input-number v-model="row.file.maxDimension" :min="1" :max="65535" /></template>
            <el-input v-if="row.file.mode === 'ZIP_BASE64'" v-model="row.file.name" placeholder="ZIP 内文件名，例如 application.pdf" />
            <span class="file-help">仅在真实调用时下载文件；超出大小或尺寸会停止请求，预览不下载。</span>
          </template>
        </div>
      </template></el-table-column>
      <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="danger" @click="remove(row.id)">删除</el-button></template></el-table-column>
    </el-table>
    </div>
    <p v-if="!output" class="request-fields-help">字段名按对方接口要求填写，不加格式前缀。同一请求体共用数据格式，切换时同步本表中的请求体字段；文件大小和类型在行内展开设置。</p>
    <el-pagination v-if="filteredFields.length > 50" v-model:current-page="page" :page-size="50" :total="filteredFields.length" layout="total, prev, pager, next" />
    <el-button class="add-field" @click="add">添加字段</el-button>
  </div>
</template>
<script>
import ApiValueEditor from './ApiValueEditor.vue'
import { newApiField, API_MODULE_FIELDS } from '@/utils/apiExecution'
import { API_REQUEST_POSITIONS, API_BODY_FORMATS, requestFieldPosition, requestFieldFormat, requestFieldContent } from '@/utils/apiRequestFields'
export default {
  components: { ApiValueEditor },
  props: { fields: { type: Array, required: true }, output: Boolean, allowOverrides: { type: Boolean, default: true }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, modules: { type: Array, default: () => API_MODULE_FIELDS } },
  emits: ['update:fields'],
  data: () => ({ positions: API_REQUEST_POSITIONS, bodyFormats: API_BODY_FORMATS, query: '', page: 1 }),
  computed: {
    filteredFields() { return this.fields.filter(field => (field.path || '').toLowerCase().includes(this.query.trim().toLowerCase())) },
    visibleFields() { return this.filteredFields.slice((this.page - 1) * 50, this.page * 50) },
  },
  methods: {
    requestFieldPosition, requestFieldFormat, requestFieldContent,
    setPosition(field, position) {
      if (field.file && position !== 'BODY') return
      field.location = position === 'BODY' ? this.fields.find(item => requestFieldPosition(item).value === 'BODY')?.location || 'JSON' : position
      if (position === 'HEADER') field.overridable = false
    },
    setBodyFormat(format) { this.fields.filter(field => requestFieldPosition(field).value === 'BODY').forEach(field => { field.location = format }) },
    add() { this.query = ''; this.page = Math.ceil((this.fields.length + 1) / 50); const field = newApiField(); if (!this.output) field.location = this.fields.find(item => requestFieldPosition(item).value === 'BODY')?.location || 'JSON'; this.$emit('update:fields', [...this.fields, field]) },
    remove(id) { this.$emit('update:fields', this.fields.filter(field => field.id !== id)); if ((this.page - 1) * 50 >= this.filteredFields.length - 1) this.page = Math.max(1, this.page - 1) },
    setFileMode(field, mode) { if (mode === 'NONE') delete field.file; else field.file = { kind: 'PDF', maxBytes: 307200, maxDimension: 1080, name: 'document.pdf', ...field.file, mode } },
  },
}
</script>
<style scoped>
.api-parameter-fields { width: 100%; min-width: 0; }
.api-field-settings { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 16px; background: var(--el-fill-color-light); }
.api-parameter-table-wrap { min-width: 0; max-width: 100%; overflow-x: auto; }
.api-parameter-table { width: 100%; min-width: 680px; }
.api-parameter-table :deep(.el-table__cell) { padding: 8px 10px; }
.api-parameter-table :deep(.cell) { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.api-parameter-table :deep(.el-input), .api-parameter-table :deep(.api-value-editor) { width: 100%; min-width: 0; }
.api-parameter-table :deep(.el-select) { width: 100%; }
.request-field-meta { white-space: nowrap; color: var(--el-text-color-secondary); }
.request-fields-help { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.api-field-settings > .el-select { width: 160px; }
.add-field { margin-top: 10px; }
.file-help { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
