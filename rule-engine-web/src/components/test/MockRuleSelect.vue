<template>
  <el-select :model-value="modelValue" clearable filterable placeholder="自动匹配" aria-label="Mock 生成规则"
    :loading="loading" @visible-change="open" @update:model-value="$emit('update:modelValue', $event || null)">
    <el-option v-for="rule in options" :key="rule.id" :label="rule.name" :value="rule.id" />
  </el-select>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { listMockRules } from '@/api/testData'
import { mockType } from '@/utils/mockTypes'
const props = defineProps({ modelValue: { type: [Number,String], default: null }, fieldType: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])
const rules = ref([]), loading = ref(false)
const options = computed(() => rules.value.filter(rule => rule.valueType === mockType(props.fieldType)))
async function open(visible) {
  if (!visible || loading.value) return
  loading.value = true
  try { rules.value = (await listMockRules()).data } finally { loading.value = false }
}
watch(() => props.modelValue, value => { if (value && !rules.value.length) open(true) }, { immediate: true })
watch(() => mockType(props.fieldType), (current, previous) => { if (current !== previous) emit('update:modelValue', null) })
</script>
