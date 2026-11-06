<template>
  <div class="historical-browser">
    <async-state v-if="loading && !selected || error" compact :loading="loading" :error="error" loading-text="正在读取历史版本…" @retry="load()" />
    <template v-else-if="selected">
      <div class="historical-selectors">
        <el-select v-model="selection" aria-label="查看历史版本" :disabled="loading" @change="baseline = null">
          <el-option v-for="row in versions" :key="row.version" :value="row.version" :label="versionDisplayLabel(row)" />
        </el-select>
        <el-select v-model="baseline" aria-label="历史对照基准" :disabled="loading" clearable placeholder="选择对照基准">
          <el-option v-for="row in versions" :key="row.version" :value="row.version" :label="versionDisplayLabel(row)" :disabled="row.version === selection" />
        </el-select>
      </div>
      <version-snapshot-panel historical :resource-type="resourceType" :model-type="modelType"
        :snapshot="selected" :original="original || ''" :comparing="!!original"
        :modified-label="versionDisplayLabel(selected)" :original-label="original && versionDisplayLabel(original)" />
    </template>
    <el-empty v-else description="暂无早期历史快照" />
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { listVersions as ruleVersions } from '@/api/definition'
import { listVersions as modelVersions } from '@/api/model'
import { listVersions as experimentVersions } from '@/api/experiment'
import { versionDisplayLabel } from '@/utils/versionRestore'
import AsyncState from './AsyncState.vue'
import VersionSnapshotPanel from './VersionSnapshotPanel.vue'

const loaders = { RULE: ruleVersions, MODEL: modelVersions, EXPERIMENT: experimentVersions }
const props = defineProps({ resourceType: { type: String, required: true }, resourceId: { type: [Number, String], required: true }, modelType: { type: String, default: '' } })
const versions = ref([])
const selection = ref(null)
const baseline = ref(null)
const error = ref('')
const loading = ref(false)
const selected = computed(() => versions.value.find(row => row.version === selection.value))
const original = computed(() => versions.value.find(row => row.version === baseline.value))
let sequence = 0
async function load(preserve = false) {
  const request = ++sequence
  loading.value = true
  error.value = ''
  if (!preserve) {
    versions.value = []
    selection.value = baseline.value = null
  }
  try {
    const response = await loaders[props.resourceType](props.resourceId)
    if (request !== sequence) return
    versions.value = response.data || []
    if (!selected.value) selection.value = versions.value[0]?.version
    if (!original.value || baseline.value === selection.value) baseline.value = null
  } catch (cause) {
    if (request === sequence) error.value = cause.message || '版本读取失败'
  } finally {
    if (request === sequence) loading.value = false
  }
}
defineExpose({ load })
watch(() => [props.resourceType, props.resourceId], () => load(), { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<style scoped>
.historical-selectors { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.historical-selectors .el-select { width: 210px; max-width: 100%; }
</style>
