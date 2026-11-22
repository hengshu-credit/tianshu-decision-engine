<template>
  <test-directory-table v-model:filters="filters" :columns="columns" :rows="rows" :loading="loading" :error="error" :suggestions="suggestions"
    storage-key="tianshu:table-columns:test-cases" placeholder="用例、资源名称或编码" empty-text="暂无测试用例" :action-width="170"
    :create-label="editableKinds.length ? '新增用例' : ''" @search="search" @reset="reset" @refresh="load" @create="create">
    <template #toolbar-actions><slot name="toolbar-actions" /></template>
    <template #filters>
      <el-form-item label="用例类型"><el-select v-model="filters.type" clearable placeholder="全部" style="width: 115px">
        <el-option v-for="(label,key) in kinds" :key="key" :value="key" :label="label" />
      </el-select></el-form-item>
    </template>
    <template #actions="{ row }">
      <el-button v-if="(row.source==='CASE' || row.source==='DEFAULT') && row.type==='RULE'" link type="primary" @click="$emit('apply',row)">载入测试</el-button>
      <el-button v-if="row.source!=='CASE'" link type="primary" @click="inspect(row)">{{ editableRow(row) && row.source !== 'MODEL_VERSION' ? '编辑样例' : '查看样例' }}</el-button>
      <el-button v-else link type="primary" @click="open(row)">{{ row.type==='RULE' ? '管理场景' : editableRow(row) ? '编辑' : '详情' }}</el-button>
      <el-button v-if="row.source==='CASE' && row.type!=='RULE' && editableRow(row)" link type="danger" @click="remove(row)">删除</el-button>
    </template>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next, total" @current-change="load" />
    <el-dialog v-model="inspecting" :title="sampleRow?.name || '测试样例'" width="min(680px, 95vw)" append-to-body>
      <template v-if="sampleRow">
        <p>{{ sampleRow.resourceName }} · {{ sampleRow.name }}</p>
        <el-input type="textarea" :rows="10" :readonly="!sampleEditable" v-model="sampleJson" aria-label="样例输入 JSON" />
      </template>
      <template #footer><el-button @click="inspecting=false">关闭</el-button><el-button v-if="sampleRow?.type !== 'VARIABLE' && !(sampleRow?.type === 'RULE' && sampleRow.source === 'DEFAULT')" @click="openSource(sampleRow)">打开原配置</el-button>
        <el-button v-if="sampleEditable" type="primary" :loading="saving" @click="saveSample">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="visible" :title="dialogTitle" width="min(920px, 95vw)" append-to-body @closed="load">
      <el-form v-if="!editing || type!=='RULE'" label-position="top" :disabled="saving || !!editing && !canEdit">
        <el-form-item v-if="!editing" label="用例类型"><el-select v-model="type" @change="resetOwner">
          <el-option v-for="key in editableKinds" :key="key" :value="key" :label="kinds[key]" />
        </el-select></el-form-item>
        <el-form-item v-if="!editing" :label="'所属' + kinds[type]">
          <remote-filter-select :key="type" v-model:value="ownerId" :fetch-options="owners" :selected-option="selected"
            option-value-key="ownerId" option-label-key="displayName" placeholder="按资源名称或编码搜索" @change="choose" />
        </el-form-item>
        <el-form-item v-if="selected" label="作用范围">{{ selected.scope === 'GLOBAL' ? '全局' : '项目级 · ' + (selected.projectName || selected.projectId) }}</el-form-item>
        <template v-if="type!=='RULE' && selected">
          <el-form-item label="用例名称"><el-input v-model="draft.name" maxlength="128" /></el-form-item>
          <el-form-item label="用例编码"><el-input v-model="draft.code" maxlength="64" /></el-form-item>
          <el-form-item v-if="type==='VARIABLE'" label="统计上下文规则（可选）">
            <remote-filter-select v-model:value="draft.contextDefinitionId" :fetch-options="contextRules"
              option-value-key="ownerId" option-label-key="displayName" :placeholder="String(draft.contextDefinitionId || '按规则名称或编码搜索')" />
          </el-form-item>
          <el-form-item label="输入参数（JSON 对象）"><el-input v-model="draft.paramsJson" type="textarea" :rows="5" /></el-form-item>
          <el-form-item label="预期断言（路径、操作与预期值）"><el-input v-model="draft.expectedJson" type="textarea" :rows="5" /></el-form-item>
        </template>
      </el-form>
      <api-scenario-panel v-if="type==='RULE' && selected" :key="ownerId" :rule="{ id: ownerId, ruleName: selected.resourceName, ruleCode: selected.code }" />
      <template #footer><el-button @click="visible=false">关闭</el-button><el-button v-if="canEdit && type!=='RULE' && selected" type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </test-directory-table>
</template>
<script setup>
import { computed, ref, onMounted, onActivated, onDeactivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { listTestCases, testCatalog, getResourceCase, saveResourceCase, deleteResourceCase } from '@/api/testData'
import { hasPermission } from '@/security/permissionState'
import TestDirectoryTable from './TestDirectoryTable.vue'
import { readCatalogSample, writeCatalogSample } from './catalogSample'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ApiScenarioPanel from '@/components/rule/ApiScenarioPanel.vue'
const emit = defineEmits(['apply','default-saved'])
const kinds = { RULE: '规则', VARIABLE: '变量', MODEL: '模型', API: '外数接口' }
const router = useRouter()
const inspecting = ref(false), sampleRow = ref(null), sampleJson = ref('')
const sources = { DEFAULT: '默认输入', CASE: '固定用例', DESIGN: '设计器', REVISION: '设计修订', MODEL_SAMPLE: '测试参数', MODEL_VERSION: '版本参数', API_SAMPLE: '接口样例', API_NAMED: '具名样例' }
const initialFilters = () => ({ scope: '', projectId: null, type: '', keyword: '' })
const filters = ref(initialFilters()), applied = ref(initialFilters())
const columns = [
  { key: 'name', label: '用例名称', required: true, minWidth: 155 },
  { key: 'scope', label: '作用范围', minWidth: 95 }, { key: 'projectName', label: '所属项目', minWidth: 135 },
  { key: 'source', label: '来源', minWidth: 150, format: row => kinds[row.type] + ' · ' + sources[row.source] },
  { key: 'resourceName', label: '所属资源', minWidth: 150 }, { key: 'code', label: '资源编码', minWidth: 135 }
]
const type = ref('RULE'), rows = ref([]), page = ref(1), total = ref(0), loading = ref(false), error = ref('')
const originalSample = ref(null)
const sampleEditable = computed(() => sampleRow.value && editableRow(sampleRow.value) && !originalSample.value?.readonly
  && (!originalSample.value?.revision || originalSample.value.revision.state === 'DRAFT'))
const modules = { RULE: 'rule', VARIABLE: 'field', MODEL: 'model', API: 'datasource' }
const editableRow = row => hasPermission(modules[row.type] + ':edit',row)
const visible = ref(false), editing = ref(null), selected = ref(null), ownerId = ref(null), draft = ref({}), saving = ref(false)
const editableKinds = computed(() => Object.keys(kinds).filter(key => hasPermission(modules[key]+':edit')))
const canEdit = computed(() => selected.value && editableRow({ ...selected.value, type: type.value }))
const dialogTitle = computed(() => selected.value ? selected.value.resourceName + ' · 测试用例' : '新增测试用例')
let revision = 0
const ownerRows = new Map()
async function load() {
  const ticket = ++revision
  loading.value = true; error.value = ''; rows.value = []
  try {
    const { data } = await listTestCases(requestFilters(applied.value, page.value))
    if (ticket === revision) { rows.value = data.records; total.value = data.total }
  } catch (e) { if (ticket === revision) error.value = e.message || '用例加载失败' }
  finally { if (ticket === revision) loading.value = false }
}
function requestFilters(filter, page) {
  return { type: filter.type || undefined, scope: filter.scope || undefined, projectId: filter.scope === 'GLOBAL' ? undefined : filter.projectId || undefined, keyword: filter.keyword.trim(), page }
}
function search() { applied.value = { ...filters.value }; page.value = 1; load() }
function reset() { filters.value = initialFilters(); search() }
async function suggestions({query,pageNum}) {
  const {data} = await listTestCases(requestFilters({ ...filters.value, keyword: query },pageNum))
  const records = data.records.flatMap(row => [row.name,row.resourceName,row.code])
    .filter(value => value?.toLowerCase().includes(query.toLowerCase())).map(value => ({ value, label: value }))
  return { data: { records, total: data.total } }
}
async function choices(kind,{ query,pageNum }) {
  const { data } = await testCatalog(kind,{ cases: false, keyword: query, page: pageNum })
  return { data: { ...data, records: data.records.map(r => ({ ...r, displayName: r.resourceName + ' (' + r.code + ')' })) } }
}
async function owners(params) {
  const kind = type.value
  const response = await choices(kind,params)
  if (type.value !== kind) return { data: { records: [], total: 0 } }
  for (const row of response.data.records) ownerRows.set(String(row.ownerId), row)
  return response
}
const contextRules = params => choices('RULE',params)
function choose(id) { selected.value = ownerRows.get(String(id)) }
async function inspect(row) {
  const ticket = ++detailRequest
  try {
    const result = await readCatalogSample(row,editableRow(row))
    if (ticket !== detailRequest) return
    originalSample.value = result
    sampleJson.value = result.text
    sampleRow.value = row
    inspecting.value = true
  } catch (e) { if (ticket === detailRequest) ElMessage.error(e.message || '读取样例失败') }
}
let detailRequest = 0
async function saveSample() {
  saving.value = true
  try {
    ElMessage.success(await writeCatalogSample(sampleRow.value,originalSample.value,sampleJson.value))
    if (sampleRow.value.source === 'DEFAULT') emit('default-saved',sampleRow.value,sampleJson.value)
    inspecting.value = false
    await load()
  } catch (e) { ElMessage.error(e.message || '保存样例失败') } finally { saving.value = false }
}
function openSource(row) {
  if (row.type === 'API') return router.push({ path: '/datasource/api/'+row.ownerId, query: { tab: 'test', sampleId: row.source === 'API_NAMED' ? row.id : undefined } })
  if (row.type === 'MODEL') return router.push({ path: '/model/'+row.ownerId, query: { testVersion: row.source === 'MODEL_VERSION' ? row.id : 'current' } })
  return router.push({ path: '/designer/score/'+row.ownerId, query: row.source === 'REVISION' ? { sourceType: 'REVISION', sourceId: row.id } : {} })
}
function resetOwner() { ownerRows.clear(); selected.value = null; ownerId.value = null }
function create() {
  type.value = editableKinds.value.includes(filters.value.type) ? filters.value.type : editableKinds.value[0]
  ownerRows.clear()
  editing.value = null; selected.value = null; ownerId.value = null
  draft.value = { name: '', code: '', paramsJson: '{}', expectedJson: '[{"path":["result"],"op":"eq","value":true}]', contextDefinitionId: null }
  visible.value = true
}
async function open(row) {
  const ticket = ++detailRequest
  const details = row.type === 'RULE' ? {} : (await getResourceCase(row.type,row.ownerId,row.id)).data
  if (ticket !== detailRequest) return
  type.value = row.type; editing.value = row.id; ownerId.value = row.ownerId; selected.value = row; draft.value = details; visible.value = true
}
async function save() {
  saving.value = true
  try { await saveResourceCase(type.value,ownerId.value,editing.value,draft.value); visible.value = false } finally { saving.value = false }
}
async function remove(row) {
  if (!await ElMessageBox.confirm('删除用例「' + row.name + '」？','删除确认').then(() => true, () => false)) return
  await deleteResourceCase(row.type,row.ownerId,row.id); await load()
}
let suspended = false
onDeactivated(() => { suspended = true; detailRequest++ })
onActivated(() => { if (suspended) { suspended = false; load() } })
defineExpose({ refresh: load })
onMounted(load)
</script>
<style scoped>
.el-pagination { margin-block-start: 12px; }
</style>
