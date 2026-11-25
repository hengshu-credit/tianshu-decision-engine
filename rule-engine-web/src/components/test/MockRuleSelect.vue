<template>
  <div class="mock-rule-select">
    <el-button plain :aria-label="bindRule ? '打开内容选择器选择 MOCK 规则' : undefined" @click="open">
      {{ bindRule ? selectedRule?.varLabelText || '选择 MOCK 规则' : '从 MOCK 生成' }}
    </el-button>
    <el-dialog v-model="visible" :title="bindRule ? '内容选择器 · 绑定 MOCK 规则' : '内容选择器 · 生成示例值'"
      width="min(800px, 96vw)" append-to-body :close-on-click-modal="false" @close="reset">
      <field-picker-panel v-if="visible" class="mock-picker-panel" embedded hide-header :visible="visible"
        :items="options" :column-labels="columnLabels" :selected-key="selectedKey" :loading="loading"
        :query="query" :category="category" @category="category = $event" @search="query = $event" @select="choose">
        <template #empty>{{ loadError || '当前字段类型和项目没有可用的 MOCK 规则' }}</template>
      </field-picker-panel>
      <div v-if="!bindRule" class="mock-composition">
        <div v-if="textComposition" class="mock-composition-tools">
          <span>按顺序选用规则，可在各段之间插入固定文本。</span>
          <el-button size="small" :disabled="generating" @click="addText">添加固定文本</el-button>
        </div>
        <div v-if="parts.length" class="mock-parts" aria-label="待生成内容">
          <template v-for="(part, index) in parts" :key="part.key">
            <el-tag v-if="part.rule" :closable="!generating" @close="parts.splice(index, 1)">{{ part.rule.varLabelText }}</el-tag>
            <div v-else class="mock-text-part">
              <el-input v-model="part.text" size="small" placeholder="例如 HS-" :aria-label="`固定文本 ${index + 1}`" :disabled="generating" />
              <el-button link size="small" :disabled="generating" @click="parts.splice(index, 1)">移除</el-button>
            </div>
          </template>
        </div>
        <el-alert v-if="generateError" type="error" :title="generateError" :closable="false" show-icon />
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="!bindRule" type="primary" :disabled="!parts.some(part => part.rule)" :loading="generating" @click="generate">
          生成并填入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import FieldPickerPanel from '@/components/common/FieldPickerPanel.vue'
import { listMockRules, previewMockRule } from '@/api/testData'
import { canViewPickerItem } from '@/security/pickerAccess'
import { fieldIdentity } from '@/utils/fieldPicker'
import { mockRuleDetails, mockType, mockTypes } from '@/utils/mockTypes'

const props = defineProps({
  projectId: { type: [String, Number], default: null },
  modelValue: { type: [Number, String], default: null },
  fieldType: { type: String, required: true },
  bindRule: Boolean,
})
const emit = defineEmits(['update:modelValue', 'generated'])
const columnLabels = { type: 'MOCK 分类', name: '规则名称', code: '实际规则', version: '字段类型' }
const rules = ref([]), visible = ref(false), loading = ref(false), loadError = ref('')
const query = ref(''), category = ref('mock')
const parts = ref([]), generating = ref(false), generateError = ref('')
const targetType = computed(() => mockType(props.fieldType))
const textComposition = computed(() => targetType.value === 'STRING')
const options = computed(() => rules.value.filter(item => {
  const type = item.rule.valueType
  const compatible = type === targetType.value || (!props.bindRule && textComposition.value && !['OBJECT', 'LIST'].includes(type))
  return compatible && (!item.projectId || String(item.projectId) === String(props.projectId)) && canViewPickerItem(item)
}))
const selectedRule = computed(() => options.value.find(item => String(item.id) === String(props.modelValue)))
const selectedKey = computed(() => selectedRule.value ? fieldIdentity(selectedRule.value) : '')
let partKey = 0
let generation = 0

async function load() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await listMockRules()
    rules.value = data.map(rule => {
      const { category, categoryCode, actualRule } = mockRuleDetails(rule)
      return { id: rule.id, rule, _refType: 'MOCK_RULE', category: 'mock', projectId: rule.projectId,
        varLabelText: rule.name, varCode: actualRule, varType: rule.valueType,
        pickerColumns: { type: category, version: mockTypes[rule.valueType] }, searchText: `${category} · ${categoryCode}` }
    })
  } catch (error) {
    rules.value = []
    loadError.value = error.message || '读取 MOCK 规则失败'
  } finally { loading.value = false }
}

function open() {
  reset()
  visible.value = true
  void load()
}

function reset() {
  generation++
  query.value = ''
  category.value = 'mock'
  parts.value = []
  generating.value = false
  generateError.value = ''
}

function choose(item) {
  if (generating.value || loading.value || !options.value.includes(item)) return
  if (props.bindRule) {
    emit('update:modelValue', item.id)
    visible.value = false
    return
  }
  generateError.value = ''
  const part = { key: ++partKey, rule: item }
  if (textComposition.value) parts.value.push(part)
  else parts.value = [part]
}

function addText() {
  parts.value.push({ key: ++partKey, text: '' })
}

async function generate() {
  if (generating.value || !parts.value.some(part => part.rule)) return
  const request = ++generation
  const text = textComposition.value
  generating.value = true
  generateError.value = ''
  try {
    const values = []
    for (const part of parts.value) {
      if (part.rule) {
        const { data } = await previewMockRule(part.rule.rule)
        if (!visible.value || request !== generation) return
        const textual = ['STRING', 'DATE', 'DATETIME'].includes(part.rule.rule.valueType)
        values.push(text && textual ? JSON.parse(data.valueJson) : data.valueJson)
      } else values.push(part.text)
    }
    const generated = text ? values.join('') : values[0]
    // 与服务端固定值解析保持一致，保留空字符串、边界空白和字面引号。
    const encoded = text && (!generated || generated.trim() !== generated || /^null$/i.test(generated)
      || /^".*"$|^'.*'$/s.test(generated) || /[\r\n]/.test(generated)) ? JSON.stringify(generated) : generated
    visible.value = false
    emit('generated', encoded)
  } catch (error) {
    if (visible.value && request === generation) generateError.value = error.message || '生成示例值失败'
  } finally {
    if (request === generation) generating.value = false
  }
}

watch(() => props.modelValue, value => { if (props.bindRule && value && !rules.value.length) void load() }, { immediate: true })
watch([targetType, () => props.projectId], ([type, projectId], [previousType]) => {
  if (visible.value) visible.value = false
  reset()
  const assigned = rules.value.find(item => String(item.id) === String(props.modelValue))
  if (props.bindRule && (type !== previousType || (assigned?.projectId && String(assigned.projectId) !== String(projectId)))) {
    emit('update:modelValue', null)
  }
})
onBeforeUnmount(reset)
</script>

<style scoped>
.mock-rule-select { min-width: 0; }
.mock-rule-select > .el-button { max-width: 100%; }
.mock-rule-select > .el-button :deep(span) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mock-picker-panel { height: clamp(232px, calc(42vh + var(--dialog-resize-height-delta, 0px)), 70vh); }
.mock-composition { display: grid; gap: 8px; margin-top: 12px; }
.mock-composition-tools, .mock-parts, .mock-text-part { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.mock-composition-tools { justify-content: space-between; color: var(--tianshu-text-secondary); }
.mock-text-part { flex-wrap: nowrap; }
.mock-text-part .el-input { width: 128px; }
</style>
