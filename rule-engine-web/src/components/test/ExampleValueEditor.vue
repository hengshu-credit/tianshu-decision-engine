<template>
  <div class="example-editor">
    <el-select :model-value="selected" aria-label="示例方式" @update:model-value="change">
      <el-option label="MOCK 自动生成" value="AUTO" />
      <el-option label="固定值" value="FIXED" />
      <el-option label="不生成 Mock" value="NONE" />
      <el-option label="绑定测试用例" value="CASE" />
      <el-option label="绑定 MOCK 规则" value="RULE" />
    </el-select>
    <el-input v-if="selected === 'AUTO' || selected === 'FIXED'" :model-value="selected === 'FIXED' ? value : ''"
      :type="structured ? 'textarea' : 'text'" :placeholder="structured ? '输入固定 JSON 示例' : '输入固定示例值'"
      aria-label="固定示例值" @update:model-value="setFixedValue" />
    <case-example-picker v-if="selected === 'CASE'" :binding="caseBinding" :project-id="projectId" :field-type="fieldType"
      @change="$emit('update:caseBinding', $event)" />
    <mock-rule-select v-if="['AUTO', 'FIXED', 'RULE'].includes(selected)" :key="selected === 'RULE' ? 'bind' : 'generate'"
      :bind-rule="selected === 'RULE'" :model-value="ruleId" :field-type="fieldType" :project-id="projectId"
      @update:model-value="$emit('update:ruleId', $event)" @generated="setFixedValue" />
    <span v-if="selected === 'AUTO'" class="example-help">生成测试入参时自动匹配 MOCK 规则。直接填写或从 MOCK 填入可保存为固定值。</span>
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
function setFixedValue(value) {
  if (selected.value !== 'FIXED') change('FIXED')
  emit('update:value', value)
}
</script>
<style scoped>
.example-editor { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.example-editor > :first-child { flex: 0 1 145px; }
.example-editor > .el-input, .example-editor > .el-textarea, .example-editor > .case-example-picker { flex: 1 1 180px; min-width: 0; }
.example-help { flex-basis: 100%; color: var(--tianshu-text-secondary); font-size: 12px; }
</style>
