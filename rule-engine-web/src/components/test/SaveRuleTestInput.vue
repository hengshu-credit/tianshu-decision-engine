<template>
  <el-button v-if="definitionId" v-permission="{ code: 'rule:edit', projectId }" size="small"
    :disabled="disabled" :loading="saving" @click="save">保存规则默认输入</el-button>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveDefaultInput } from '@/api/testData'
import { parseExactJson, isExactNumber } from '@/utils/exactJson'

const props = defineProps({
  definitionId: { type: [String, Number], default: null },
  projectId: { type: [String, Number], default: null },
  readInput: { type: Function, required: true },
  disabled: Boolean
})
const emit = defineEmits(['saved'])
const saving = ref(false)
async function save() {
  if (saving.value) return
  saving.value = true
  try {
    const id = props.definitionId
    const text = props.readInput()
    const value = parseExactJson(text)
    if (!value || typeof value !== 'object' || Array.isArray(value) || isExactNumber(value)) throw new Error('输入参数必须是 JSON 对象')
    await saveDefaultInput('RULE', id, text)
    if (id === props.definitionId) emit('saved', text)
    ElMessage.success('规则默认测试输入已保存')
  } catch (error) { ElMessage.error(error.message || '保存默认测试输入失败') }
  finally { saving.value = false }
}
</script>
