<template>
  <span v-if="label">{{ label }}</span>
  <span v-else-if="loading" aria-busy="true">读取中</span>
  <el-button v-else link size="small" aria-label="重新读取字段数" @click.stop="attempt++">重试</el-button>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getModel } from '@/api/model'

const props = defineProps({ model: { type: Object, required: true } })
const label = ref('')
const loading = ref(false)
const attempt = ref(0)

function count(summary, fields) {
  const value = typeof summary === 'string' && /^\d+$/.test(summary) ? Number(summary) : summary
  if (Number.isSafeInteger(value) && value >= 0) return value
  return Array.isArray(fields) ? fields.length : null
}

watch([() => props.model, attempt], async ([model], _, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  let inputs = count(model.inputFieldCount, model.inputFields)
  let outputs = count(model.outputFieldCount, model.outputFields)
  label.value = ''
  loading.value = inputs === null || outputs === null
  if (loading.value) {
    try {
      const { data } = await getModel(model.id)
      if (!current || String(data?.id) !== String(model.id)) return
      inputs ??= Array.isArray(data.inputFields) ? data.inputFields.length : count(data.inputFieldCount)
      outputs ??= Array.isArray(data.outputFields) ? data.outputFields.length : count(data.outputFieldCount)
    } catch {
      return
    } finally {
      if (current) loading.value = false
    }
  }
  if (inputs !== null && outputs !== null) label.value = `${inputs}进 / ${outputs}出`
}, { immediate: true })
</script>
