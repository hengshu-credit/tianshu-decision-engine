<template>
  <test-directory-table v-model:filters="filters" :columns="columns" :rows="pageRows" :loading="loading" :error="error"
    :suggestions="suggestions" storage-key="tianshu:table-columns:test-mock" placeholder="规则名称、方法或匹配字段"
    empty-text="暂无匹配规则" :action-width="190" :create-label="canCreate ? '新增规则' : ''"
    @search="search" @reset="reset" @refresh="load" @create="edit()">
    <template #filters>
      <el-form-item label="生成类别"><el-select v-model="filters.category" clearable filterable placeholder="全部" style="width: 160px">
        <el-option label="基础规则" value="base" />
        <el-option v-for="group in fakerGroups" :key="group.module" :label="group.label + ' · ' + group.module" :value="group.module" />
      </el-select></el-form-item>
    </template>
    <el-pagination v-if="filtered.length > pageSize" v-model:current-page="page" :page-size="pageSize"
      :total="filtered.length" layout="prev, pager, next, total" />
    <template #actions="{ row }">
      <el-button link type="primary" @click="edit(row)">{{ canEdit(row) ? '编辑' : '查看' }}</el-button>
      <el-button v-if="canEdit(row)" link type="primary" @click="bindingRule=row">关联字段</el-button>
      <el-button v-if="canEdit(row) && !row.builtin" link type="danger" @click="remove(row)">删除</el-button>
    </template>
    <mock-field-bindings v-if="bindingRule" :rule="bindingRule" @close="bindingRule=null" />
    <el-dialog v-model="visible" :title="draft.id ? 'Mock 规则详情' : '新增 Mock 规则'" width="min(520px, 95vw)" append-to-body>
      <el-form label-position="top" :disabled="!editable || saving">
        <scope-project-fields edit-permission="field:edit" v-model:scope="draft.scope" v-model:project-id="draft.projectId" :disabled="!!draft.id" @scope-change="draft.projectId=null" />
        <el-form-item label="规则名称"><el-input v-model="draft.name" maxlength="128" /></el-form-item>
        <el-form-item label="字段类型"><el-select v-model="draft.valueType" :disabled="!!draft.id" @change="setGenerator('FIXED')">
          <el-option v-for="(name,key) in mockTypes" :key="key" :label="name" :value="key" />
        </el-select></el-form-item>
        <el-form-item label="生成方式"><el-select :model-value="draft.generator" @update:model-value="setGenerator">
          <el-option v-for="key in methods" :key="key" :label="generatorNames[key]" :value="key" />
        </el-select></el-form-item>
        <el-form-item v-if="draft.generator === 'FAKER'" label="Faker 方法">
          <el-cascader :model-value="selectedMethod" :options="methodGroups" :props="{ emitPath: false }" filterable
            placeholder="选择类别和方法" @update:model-value="chooseMethod" />
        </el-form-item>
        <el-form-item v-if="configLabel" :label="configLabel"><el-input v-model="draft.configJson" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="自动匹配字段"><el-input v-model="draft.matchNames" placeholder="如 applicant.mobile 或手机号，逗号分隔；留空仅显式引用" maxlength="512" /></el-form-item>
      </el-form>
      <pre v-if="preview" class="method-preview" aria-label="生成预览">{{ preview }}</pre>
      <template #footer><el-button :loading="previewing" :disabled="methodMissing" @click="tryRule">试生成</el-button><el-button @click="visible=false">关闭</el-button><el-button v-if="editable" type="primary" :disabled="methodMissing" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </test-directory-table>
</template>
<script setup>
import { computed, ref, watch, onMounted, onActivated, onDeactivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TestDirectoryTable from './TestDirectoryTable.vue'
import ScopeProjectFields from '@/components/common/ScopeProjectFields.vue'
import MockFieldBindings from './MockFieldBindings.vue'
import { previewMockRule, listMockRules, saveMockRule, deleteMockRule } from '@/api/testData'
import { hasPermission } from '@/security/permissionState'
import { fakerGroups, fakerMethod } from '@/utils/fakerMethods'
import { generatorNames, mockTypes } from '@/utils/mockTypes'
const canCreate = computed(() => hasPermission('field:edit'))
const canEdit = row => hasPermission('field:edit', row)
const editable = computed(() => draft.value.id ? canEdit(draft.value) : canCreate.value)
const bindingRule = ref(null), preview = ref(''), previewing = ref(false)
const rules = ref([]), loading = ref(false), error = ref(''), draft = ref({}), visible = ref(false), saving = ref(false)
const page = ref(1), pageSize = 20
const baseCategory = { module: 'base', category: '基础规则' }
const initialFilters = () => ({ category: '', keyword: '', scope: '', projectId: null })
const filters = ref(initialFilters()), applied = ref(initialFilters())
const columns = [
  { key: 'scope', label: '作用范围', minWidth: 95, required: true },
  { key: 'projectName', label: '项目名称', minWidth: 130 },
  { key: 'name', label: '规则名称', minWidth: 160, required: true },
  { key: 'category', label: '生成类别', minWidth: 110, format: row => row.method.category },
  { key: 'valueType', label: '字段类型', minWidth: 100, format: row => mockTypes[row.valueType] },
  { key: 'generator', label: '生成方式', minWidth: 165, format: row => row.method.key || generatorNames[row.generator] },
  { key: 'matchNames', label: '自动匹配字段', minWidth: 180, format: row => row.matchNames === '*' ? '同类型字段' : row.matchNames || '仅手动绑定' }
]
function matches(row, filter) {
  const keywords = [row.name, row.matchNames, row.method.category, row.method.key, generatorNames[row.generator]].join(' ').toLowerCase()
  return (!filter.category || filter.category === row.method.module)
    && (!filter.scope || filter.scope === row.scope) && (!filter.projectId || String(row.projectId) === String(filter.projectId))
    && keywords.includes(filter.keyword.trim().toLowerCase())
}
const filtered = computed(() => rules.value.filter(row => matches(row, applied.value)))
const pageRows = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch(() => filtered.value.length, size => { page.value = Math.min(page.value, Math.max(1, Math.ceil(size / pageSize))) })
function search() { applied.value = { ...filters.value }; page.value = 1 }
function reset() { filters.value = initialFilters(); search() }
function suggestions({ query, pageNum, pageSize: limit }) {
  const records = rules.value.filter(row => matches(row, { ...filters.value, keyword: query })).map(row => ({ label: row.name, value: row.name }))
  return { data: { records: records.slice((pageNum - 1) * limit, pageNum * limit), total: records.length } }
}
const methodGroups = computed(() => fakerGroups.map(group => ({
  label: group.label + ' · ' + group.module, value: group.module,
  children: group.methods.filter(method => method.type === draft.value.valueType).map(method => ({
    label: method.label + ' · ' + method.name, value: group.module + '.' + method.name,
  })),
})).filter(group => group.children.length))
const selectedMethod = computed(() => fakerMethod(draft.value)?.key)
const methodMissing = computed(() => draft.value.generator === 'FAKER' && !selectedMethod.value)
function chooseMethod(key) { draft.value.configJson = JSON.stringify(key) }

const methods = computed(() => ['FIXED','CHOICE',...(methodGroups.value.length ? ['FAKER'] : []),...({ STRING: ['TEXT','UUID','PHONE','EMAIL','TEMPLATE'], NUMBER: ['INTEGER','DECIMAL'], DATE: ['DATE'], DATETIME: ['DATETIME'], TIMESTAMP: ['TIMESTAMP'], BOOLEAN: ['BOOLEAN'] }[draft.value.valueType] || [])])
const configLabel = computed(() => ({ FIXED: '固定值（JSON）', CHOICE: '候选值（JSON 数组）', INTEGER: '整数范围 [最小值, 最大值]', DECIMAL: '小数范围 [最小值, 最大值, 小数位数]', TEMPLATE: '文本模板（JSON 字符串，支持 {uuid}、{phone}、{date}、{digits}）' })[draft.value.generator])
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try { rules.value = (await listMockRules()).data.map(row => ({ ...row, method: fakerMethod(row) || baseCategory, scope: row.projectId ? 'PROJECT' : 'GLOBAL' })) } catch (e) { error.value = e.message || '规则加载失败' } finally { loading.value = false }
}
let previewRequest = 0
watch(() => [draft.value, draft.value.generator, draft.value.configJson, draft.value.valueType], () => {
  previewRequest++
  preview.value = ''
  previewing.value = false
}, { flush: 'sync' })
async function tryRule() {
  const request = ++previewRequest
  previewing.value = true
  try {
    const { data } = await previewMockRule({ ...draft.value, name: draft.value.name || '预览' })
    if (request === previewRequest) preview.value = data.valueJson
  } finally { if (request === previewRequest) previewing.value = false }
}
function edit(row) {
  preview.value = ''
  draft.value = row ? { ...row, method: undefined } : { scope: canEdit({ scope: 'GLOBAL' }) ? 'GLOBAL' : 'PROJECT', projectId: null, name: '', matchNames: '', valueType: 'STRING', generator: 'FIXED', configJson: '""' }
  visible.value = true
}
function setGenerator(method) {
  const base = { NUMBER: '0', TIMESTAMP: '0', OBJECT: '{}', LIST: '[]', BOOLEAN: 'false' }[draft.value.valueType] || '""'
  draft.value.generator = method
  draft.value.configJson = { FIXED: base, CHOICE: '[' + base + ']', INTEGER: '[0,100]', DECIMAL: '[0,100,2]', TEMPLATE: '"REQ-{date}-{digits}"' }[method] || 'null'
}
async function save() {
  if (draft.value.scope === 'PROJECT' && !draft.value.projectId) return ElMessage.warning('请选择所属项目')
  if (!canEdit(draft.value)) return ElMessage.warning('当前范围没有编辑权限')
  if (!draft.value.name.trim()) return ElMessage.warning('请填写规则名称')
  try { JSON.parse(draft.value.configJson) } catch { return ElMessage.warning('请填写有效的 JSON 配置') }
  saving.value = true
  try { await saveMockRule(draft.value); visible.value = false; await load() } finally { saving.value = false }
}
async function remove(row) {
  if (!await ElMessageBox.confirm('删除生成规则「' + row.name + '」？','删除确认').then(() => true, () => false)) return
  await deleteMockRule(row.id)
  await load()
}
let suspended = false
onDeactivated(() => { suspended = true })
onActivated(() => { if (suspended) { suspended = false; load() } })
onMounted(load)
</script>
<style scoped>
.method-preview { overflow: auto; padding: 8px; background: var(--tianshu-bg-page); color: var(--tianshu-text-primary); }
</style>
