<template>
  <div class="case-example-picker">
    <remote-filter-select :key="projectId" :value="caseKey" :fetch-options="options" :selected-option="selectedOption"
      aria-label="固定测试用例" placeholder="选择固定测试用例" @update:value="chooseCase" />
    <el-select v-if="caseKey" :model-value="pointerKey" :loading="loading" filterable aria-label="用例输入字段"
      placeholder="选择用例输入字段" @change="choosePath">
      <el-option v-for="item in paths" :key="item.value" :value="item.value" :label="item.label" />
    </el-select>
    <span v-if="error" role="alert">{{ error }}</span>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { listExampleCases, getExampleCase } from '@/api/testData'
import { isExactNumber, parseExactJson } from '@/utils/exactJson'
import { mockType } from '@/utils/mockTypes'

const props = defineProps({
  binding: { type: String, default: null },
  fieldType: { type: String, required: true },
  projectId: { type: [Number, String], default: null },
})
const emit = defineEmits(['change'])
const selectedOption = ref(null)
const paths = ref([])
const loading = ref(false)
const error = ref('')
const binding = computed(() => {
  try { return JSON.parse(props.binding) || {} } catch { return {} }
})
const caseKey = computed(() => binding.value.id ? binding.value.type + ':' + binding.value.id : '')
const pointerKey = computed(() => typeof binding.value.pointer === 'string' ? '#' + binding.value.pointer : null)
const scope = computed(() => props.projectId || 0)
const kinds = { RULE: '规则', VARIABLE: '变量', MODEL: '模型', API: '外数' }
const label = (type, row) => kinds[type] + ' · ' + row.resourceName + ' / ' + row.name
async function options({ query, pageNum }) {
  const { data } = await listExampleCases({ projectId: scope.value, keyword: query, page: pageNum })
  return { records: data.records.map(row => ({ value: row.type + ':' + row.id, label: label(row.type, row) })), total: data.total }
}
function chooseCase(value) {
  if (!value) return emit('change', null)
  const [type, id] = value.split(':')
  emit('change', JSON.stringify({ type, id }))
}
function choosePath(value) {
  emit('change', JSON.stringify({ ...binding.value, pointer: value.slice(1) }))
}
function inputPaths(root, type) {
  const entries = []
  function visit(value, path) {
    const group = typeof value === 'number' || isExactNumber(value) ? 'NUMBER'
      : value === null ? null : Array.isArray(value) ? 'LIST'
      : ({ object: 'OBJECT', boolean: 'BOOLEAN', string: 'STRING' })[typeof value]
    const target = mockType(type)
    if (group === target || (target === 'TIMESTAMP' && group === 'NUMBER')
        || (['DATE','DATETIME'].includes(target) && group === 'STRING')) {
      entries.push({ value: '#' + path, label: path || '整个输入对象' })
    }
    if (group === 'LIST' || group === 'OBJECT') {
      for (const [key, child] of Object.entries(value)) visit(child, path + '/' + key.replaceAll('~','~0').replaceAll('/','~1'))
    }
  }
  visit(root,'')
  return entries
}
watch([caseKey, scope, () => props.fieldType], async ([key, project, type], previous, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  paths.value = []
  error.value = ''
  selectedOption.value = null
  loading.value = !!key
  if (!key) return
  const [kind, id] = key.split(':')
  try {
    const { data } = await getExampleCase(kind, id, project)
    if (!active) return
    selectedOption.value = { value: key, label: label(kind, data) }
    paths.value = inputPaths(parseExactJson(data.paramsJson), type)
    if (!paths.value.length) error.value = '用例中没有匹配此类型的输入字段'
  } catch (e) {
    if (active) error.value = e.message || '读取测试用例失败'
  } finally {
    if (active) loading.value = false
  }
}, { immediate: true })
</script>
<style scoped>
.case-example-picker { display: flex; flex-wrap: wrap; gap: 8px; min-width: 0; }
.case-example-picker > * { flex: 1 1 150px; min-width: 0; }
.case-example-picker [role="alert"] { color: var(--el-color-danger); }
</style>
