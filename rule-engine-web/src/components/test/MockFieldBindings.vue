<template>
  <el-dialog title="关联字段" :model-value="true" width="min(600px, 95vw)" append-to-body @close="$emit('close')">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="项目范围"><project-resource-select v-model="project" clearable placeholder="全局字段" /></el-form-item>
      <el-form-item label="选择字段">
        <var-picker value-key="reference" :value="selected" :vars="options" :allow-custom="false" :loading="loading"
          @update:value="selected=$event" />
      </el-form-item>
      <el-button :disabled="!selected || loading" :loading="saving" @click="bind">关联到此方法</el-button>
    </el-form>
    <el-table :data="rows" size="small" border class="binding-table" empty-text="暂无主动关联的字段">
      <el-table-column label="资源类型" width="110"><template #default="{row}">{{ kinds[row.refType] }}</template></el-table-column>
      <el-table-column label="字段编码" prop="code" />
      <el-table-column label="操作" width="80"><template #default="{row}"><el-button link :disabled="saving" @click="change(row.refType,row.refId,true)">解除</el-button></template></el-table-column>
    </el-table>
  </el-dialog>
</template>
<script setup>
import { ref, watch, onMounted } from 'vue'
import VarPicker from '@/components/common/VarPicker.vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import { listVariablesByProject } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { buildReferenceCatalog, buildPickerOptions } from '@/utils/referenceCatalog'
import { listMockFields, setMockField } from '@/api/testData'
import { mockType } from '@/utils/mockTypes'
const props = defineProps({ rule: { type: Object, required: true } })
defineEmits(['close'])
const kinds = { VARIABLE: '变量', DATA_OBJECT: '对象子字段', DATA_OBJECT_ROOT: '整体对象' }
const project = ref(null), options = ref([]), rows = ref([]), selected = ref(''), loading = ref(false), saving = ref(false)
watch(project, async (id, old, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  selected.value = ''; options.value = []; loading.value = true
  try {
    const [vars, tree] = await Promise.all([listVariablesByProject(id || 0),getVariableTree(id || 0)])
    const catalog = buildReferenceCatalog((vars.data || []).filter(v => v.varSource === 'INPUT'),tree.data?.tree || [])
    if (current) options.value = buildPickerOptions(catalog).filter(o => kinds[o.refType] && mockType(o.varType) === props.rule.valueType)
  } finally { if (current) loading.value = false }
}, { immediate: true })
async function load() { rows.value = (await listMockFields(props.rule.id)).data }
async function change(type,id,remove=false) {
  saving.value = true
  try { await setMockField(props.rule.id,type,id,remove); selected.value=''; await load() } finally { saving.value = false }
}
function bind() { const [type,id] = selected.value.split(':'); return change(type,id) }
onMounted(load)
</script>
<style scoped>
.binding-table { margin-block-start: 12px; }
</style>
