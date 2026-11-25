<template>
  <div class="example-editor">
    <el-select :model-value="selected" aria-label="示例方式" @update:model-value="change">
      <el-option label="自动匹配" value="AUTO" />
      <el-option label="不生成 Mock" value="NONE" />
      <el-option label="固定值" value="FIXED" />
      <el-option label="绑定测试用例" value="CASE" />
      <el-option label="绑定 MOCK 规则" value="RULE" />
    </el-select>
    <el-input v-if="selected==='FIXED'" :model-value="value" :type="structured ? 'textarea' : 'text'"
      :placeholder="structured ? '输入 JSON，可留空' : '输入固定示例，可留空'" aria-label="固定示例值" @update:model-value="$emit('update:value',$event)" />
    <case-example-picker v-if="selected === 'CASE'" :binding="caseBinding" :project-id="projectId" :field-type="fieldType"
      @change="$emit('update:caseBinding', $event)" />
    <mock-rule-select v-if="['AUTO', 'FIXED', 'RULE'].includes(selected)" :key="selected === 'RULE' ? 'bind' : 'generate'"
      :bind-rule="selected === 'RULE'" :model-value="ruleId" :field-type="fieldType" :project-id="projectId"
      @update:model-value="$emit('update:ruleId', $event)" @generated="applyGenerated" />
  </div>
</template>
<script setup>
import { computed } from 'vue'
import CaseExamplePicker from './CaseExamplePicker.vue'
import { mockType } from '@/utils/mockTypes'
import MockRuleSelect from './MockRuleSelect.vue'
const props = defineProps({ caseBinding: { type: String, default: null }, projectId: { type: [String,Number], default: null },
  mode: { type: String, default: '' }, value: { type: String, default: '' },
  ruleId: { type: [String, Number], default: null }, fieldType: { type: String, required: true }
})
const emit = defineEmits(['update:mode','update:value','update:ruleId','update:caseBinding'])
const selected = computed(() => props.mode || (props.ruleId ? 'RULE' : props.value ? 'FIXED' : 'AUTO'))
const structured = computed(() => ['OBJECT','LIST'].includes(mockType(props.fieldType)))
function change(mode) {
  emit('update:mode',mode)
  if (mode !== 'CASE') emit('update:caseBinding', null)
  if (mode !== 'RULE') emit('update:ruleId',null)
  if (mode !== 'FIXED') emit('update:value','')
}
function applyGenerated(value) {
  change('FIXED')
  emit('update:value', value)
}
</script>
<style scoped>
.example-editor { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.example-editor > :first-child { flex: 0 1 145px; }
.example-editor > .el-input, .example-editor > .el-textarea, .example-editor > .case-example-picker { flex: 1 1 180px; min-width: 0; }
</style>
