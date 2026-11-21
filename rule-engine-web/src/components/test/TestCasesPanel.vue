<template>
  <section class="uiue-card">
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-select v-model="type" aria-label="用例类型" class="case-type" @change="search">
        <el-option v-for="(label,key) in kinds" :key="key" :value="key" :label="label" />
      </el-select>
      <el-input v-model="keyword" clearable class="case-search" placeholder="搜索用例、资源名称或编码" @clear="search" @keyup.enter="search" />
      <el-button @click="search">查询</el-button>
      <div class="case-actions"><el-button @click="load">刷新</el-button><el-button v-if="canEdit" type="primary" @click="create">新增用例</el-button></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :aria-busy="loading" :data="rows" border size="small" show-overflow-tooltip empty-text="暂无测试用例">
      <el-table-column prop="name" label="用例名称" min-width="150" />
      <el-table-column prop="resourceName" :label="'所属' + kinds[type]" min-width="160" />
      <el-table-column prop="code" label="资源编码" min-width="130" />
      <el-table-column label="操作" min-width="150"><template #default="{ row }">
        <el-button v-if="type==='RULE'" link @click="$emit('apply',row)">载入测试</el-button>
        <el-button link @click="open(row)">{{ type==='RULE' ? '管理场景' : '详情' }}</el-button>
        <el-button v-if="type!=='RULE' && canEdit" link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next, total" @current-change="load" />
    <el-dialog v-model="visible" :title="dialogTitle" width="min(920px, 95vw)" append-to-body @closed="load">
      <el-form v-if="!selected || type!=='RULE'" label-position="top">
        <el-form-item v-if="!editing" :label="'所属' + kinds[type]">
          <remote-filter-select v-model:value="ownerId" :fetch-options="owners" :selected-option="selected"
            option-value-key="ownerId" option-label-key="displayName" placeholder="按资源名称或编码搜索" @change="choose" />
        </el-form-item>
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
  </section>
</template>
<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { testCatalog, getResourceCase, saveResourceCase, deleteResourceCase } from '@/api/testData'
import { hasPermission } from '@/security/permissionState'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ApiScenarioPanel from '@/components/rule/ApiScenarioPanel.vue'
defineEmits(['apply'])
const kinds = { RULE: '规则', VARIABLE: '变量', MODEL: '模型', API: '外数接口' }
const type = ref('RULE'), keyword = ref(''), rows = ref([]), page = ref(1), total = ref(0), loading = ref(false), error = ref('')
const visible = ref(false), editing = ref(null), selected = ref(null), ownerId = ref(null), draft = ref({}), saving = ref(false)
const canEdit = computed(() => hasPermission(({ RULE: 'rule', VARIABLE: 'field', MODEL: 'model', API: 'datasource' })[type.value]+':edit'))
const dialogTitle = computed(() => selected.value ? selected.value.resourceName + ' · 测试用例' : '新增测试用例')
let revision = 0
const ownerRows = new Map()
async function load() {
  const ticket = ++revision
  loading.value = true; error.value = ''; rows.value = []
  try {
    const { data } = await testCatalog(type.value,{ keyword: keyword.value.trim(), page: page.value })
    if (ticket === revision) { rows.value = data.records; total.value = data.total }
  } catch (e) { if (ticket === revision) error.value = e.message || '用例加载失败' }
  finally { if (ticket === revision) loading.value = false }
}
function search() { page.value = 1; load() }
async function choices(kind,{ query,pageNum }) {
  const { data } = await testCatalog(kind,{ cases: false, keyword: query, page: pageNum })
  return { data: { ...data, records: data.records.map(r => ({ ...r, displayName: r.resourceName + ' (' + r.code + ')' })) } }
}
async function owners(params) {
  const response = await choices(type.value,params)
  for (const row of response.data.records) ownerRows.set(String(row.ownerId), row)
  return response
}
const contextRules = params => choices('RULE',params)
function choose(id) { selected.value = ownerRows.get(String(id)) }
function create() {
  ownerRows.clear()
  editing.value = null; selected.value = null; ownerId.value = null
  draft.value = { name: '', code: '', paramsJson: '{}', expectedJson: '[{"path":["result"],"op":"eq","value":true}]', contextDefinitionId: null }
  visible.value = true
}
async function open(row) {
  const ticket = revision
  const details = type.value === 'RULE' ? {} : (await getResourceCase(type.value,row.ownerId,row.id)).data
  if (ticket !== revision) return
  editing.value = row.id; ownerId.value = row.ownerId; selected.value = row; draft.value = details; visible.value = true
}
async function save() {
  saving.value = true
  try { await saveResourceCase(type.value,ownerId.value,editing.value,draft.value); visible.value = false } finally { saving.value = false }
}
async function remove(row) {
  if (!await ElMessageBox.confirm('删除用例「' + row.name + '」？','删除确认').then(() => true, () => false)) return
  await deleteResourceCase(type.value,row.ownerId,row.id); await load()
}
onMounted(load)
</script>
<style scoped>
.case-type { width: 140px; }
.case-search { max-width: 300px; }
.case-actions { display: flex; gap: 8px; margin-inline-start: auto; }
.el-pagination { margin-block-start: 12px; }
</style>
