<template>
  <section class="uiue-card">
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-input v-model="query" clearable placeholder="搜索规则名称或匹配字段" aria-label="搜索 Mock 规则" />
      <div class="mock-actions">
        <el-button @click="load">刷新</el-button>
        <el-button v-if="editable" type="primary" @click="edit()">新增规则</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="filtered" :aria-busy="loading" border size="small" show-overflow-tooltip empty-text="暂无匹配规则">
      <el-table-column prop="name" label="规则名称" min-width="140" />
      <el-table-column label="字段类型" width="100"><template #default="{ row }">{{ mockTypes[row.valueType] }}</template></el-table-column>
      <el-table-column label="生成方式" min-width="130"><template #default="{ row }">{{ generatorNames[row.generator] }}</template></el-table-column>
      <el-table-column label="自动匹配字段" min-width="180"><template #default="{ row }">{{ row.matchNames === '*' ? '同类型字段' : row.matchNames || '仅手动绑定' }}</template></el-table-column>
      <el-table-column label="操作" width="185"><template #default="{ row }">
        <el-button link @click="edit(row)">{{ editable ? '编辑' : '查看' }}</el-button>
        <el-button v-if="editable" link @click="bindingRule=row">关联字段</el-button>
        <el-button v-if="editable && !row.builtin" link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <mock-field-bindings v-if="bindingRule" :rule="bindingRule" @close="bindingRule=null" />
    <el-dialog v-model="visible" :title="draft.id ? 'Mock 规则详情' : '新增 Mock 规则'" width="min(520px, 95vw)" append-to-body>
      <el-form label-position="top" :disabled="!editable || saving">
        <el-form-item label="规则名称"><el-input v-model="draft.name" maxlength="128" /></el-form-item>
        <el-form-item label="字段类型"><el-select v-model="draft.valueType" :disabled="!!draft.id" @change="setGenerator('FIXED')">
          <el-option v-for="(name,key) in mockTypes" :key="key" :label="name" :value="key" />
        </el-select></el-form-item>
        <el-form-item label="生成方式"><el-select :model-value="draft.generator" @update:model-value="setGenerator">
          <el-option v-for="key in methods" :key="key" :label="generatorNames[key]" :value="key" />
        </el-select></el-form-item>
        <el-form-item v-if="configLabel" :label="configLabel"><el-input v-model="draft.configJson" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="自动匹配字段"><el-input v-model="draft.matchNames" placeholder="如 applicant.mobile 或手机号，逗号分隔；留空仅显式引用" maxlength="512" /></el-form-item>
      </el-form>
      <pre v-if="preview" class="method-preview" aria-label="生成预览">{{ preview }}</pre>
      <template #footer><el-button @click="tryRule">试生成</el-button><el-button @click="visible=false">关闭</el-button><el-button v-if="editable" type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MockFieldBindings from './MockFieldBindings.vue'
import { previewMockRule, listMockRules, saveMockRule, deleteMockRule } from '@/api/testData'
import { hasPermission } from '@/security/permissionState'
import { generatorNames, mockTypes } from '@/utils/mockTypes'
const editable = computed(() => hasPermission('field:edit', { scope: 'GLOBAL' }))
const bindingRule = ref(null), preview = ref('')
const rules = ref([]), query = ref(''), loading = ref(false), error = ref(''), draft = ref({}), visible = ref(false), saving = ref(false)
const filtered = computed(() => rules.value.filter(r => (r.name + ' ' + r.matchNames).toLowerCase().includes(query.value.trim().toLowerCase())))
const methods = computed(() => ['FIXED','CHOICE',...({ STRING: ['TEXT','UUID','PHONE','EMAIL','TEMPLATE'], NUMBER: ['INTEGER','DECIMAL'], DATE: ['DATE'], DATETIME: ['DATETIME'], TIMESTAMP: ['TIMESTAMP'], BOOLEAN: ['BOOLEAN'] }[draft.value.valueType] || [])])
const configLabel = computed(() => ({ FIXED: '固定值（JSON）', CHOICE: '候选值（JSON 数组）', INTEGER: '整数范围 [最小值, 最大值]', DECIMAL: '小数范围 [最小值, 最大值, 小数位数]', TEMPLATE: '文本模板（JSON 字符串，支持 {uuid}、{phone}、{date}、{digits}）' })[draft.value.generator])
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try { rules.value = (await listMockRules()).data } catch (e) { error.value = e.message || '规则加载失败' } finally { loading.value = false }
}
async function tryRule() {
  const rule = draft.value
  const { data } = await previewMockRule({ ...rule, name: rule.name || '预览' })
  if (rule === draft.value) preview.value = data.valueJson
}
function edit(row) {
  preview.value = ''
  draft.value = row ? { ...row } : { name: '', matchNames: '', valueType: 'STRING', generator: 'FIXED', configJson: '""' }
  visible.value = true
}
function setGenerator(method) {
  const base = { NUMBER: '0', TIMESTAMP: '0', OBJECT: '{}', LIST: '[]', BOOLEAN: 'false' }[draft.value.valueType] || '""'
  draft.value.generator = method
  draft.value.configJson = { FIXED: base, CHOICE: '[' + base + ']', INTEGER: '[0,100]', DECIMAL: '[0,100,2]', TEMPLATE: '"REQ-{date}-{digits}"' }[method] || 'null'
}
async function save() {
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
onMounted(load)
</script>
<style scoped>
.uiue-filter-toolbar > .el-input { max-width: 320px; }
.method-preview { overflow: auto; padding: 8px; background: var(--tianshu-bg-soft); color: var(--tianshu-text-primary); }
.mock-actions { display: flex; gap: 8px; margin-inline-start: auto; }
</style>
