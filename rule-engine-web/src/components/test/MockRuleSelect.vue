<template>
  <el-select :model-value="modelValue" clearable filterable placeholder="选择生成方法" aria-label="Mock 生成规则"
    :loading="loading" @visible-change="open" @update:model-value="$emit('update:modelValue', $event || null)">
    <el-option v-for="rule in options" :key="rule.id" :label="rule.optionLabel" :value="rule.id" />
  </el-select>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { listMockRules } from '@/api/testData'
import { fakerMethod } from '@/utils/fakerMethods'
import { mockType } from '@/utils/mockTypes'
const props = defineProps({ projectId: { type: [String,Number], default: null },  modelValue: { type: [Number,String], default: null }, fieldType: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])
const rules = ref([]), loading = ref(false)
const options = computed(() => rules.value.filter(rule => rule.valueType === mockType(props.fieldType) && (!rule.projectId || String(rule.projectId) === String(props.projectId))))
async function open(visible) {
  if (!visible || loading.value) return
  loading.value = true
  try { rules.value = (await listMockRules()).data.map(rule => {
    const method = fakerMethod(rule)
    return { ...rule, optionLabel: method ? rule.name + ' (' + method.key + ')' : rule.name }
  }) } finally { loading.value = false }
}
watch(() => props.modelValue, value => { if (value && !rules.value.length) open(true) }, { immediate: true })
watch(() => mockType(props.fieldType), (current, previous) => { if (current !== previous) emit('update:modelValue', null) })
</script>
