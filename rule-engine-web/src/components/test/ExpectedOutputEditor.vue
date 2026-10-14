<template>
  <div class="expected-output-editor">
    <div class="expectation-heading">
      <strong>预期结果</strong>
      <el-radio-group :model-value="mode" :disabled="Boolean(configurationError)" size="small" data-testid="expectation-mode" @update:model-value="setMode">
        <el-radio-button value="NONE">不校验</el-radio-button>
        <el-radio-button value="FIELDS">按输出字段校验</el-radio-button>
        <el-radio-button value="SNAPSHOT">JSON 快照</el-radio-button>
      </el-radio-group>
    </div>
    <el-alert v-if="configurationError" type="error" :closable="false" show-icon>
      {{ configurationError }}；原配置已保留。
      <el-button link type="primary" data-testid="expectation-reset" @click="resetConfiguration">重新配置预期</el-button>
    </el-alert>
    <template v-else-if="mode === 'FIELDS'">
      <p class="expectation-help">所有字段校验均满足时才符合预期。字段通过稳定引用关联，修改名称不影响关联。</p>
      <el-alert v-if="selectableOutputs.length === 0" type="info" :closable="false" show-icon>
        暂无可选择的稳定输出字段。请到规则设计中绑定输出字段，并检查重复引用；也可以使用 JSON 快照。
      </el-alert>
      <div v-for="(check, index) in checks" :key="index" class="expectation-check" :data-testid="`expectation-check-${index}`">
        <div class="expectation-check-heading">
          <span>校验 {{ index + 1 }}</span>
          <el-button link type="danger" :aria-label="`删除校验 ${index + 1}`" @click="removeCheck(index)">删除</el-button>
        </div>
        <div class="expectation-field-row">
          <el-select :model-value="fieldKey(check)" filterable placeholder="选择输出字段" :data-testid="`expectation-field-${index}`" :aria-label="`校验 ${index + 1} 输出字段`" @update:model-value="selectField(index, $event)">
            <el-option v-for="field in selectableOutputs" :key="fieldKey(field)" :label="fieldLabel(field)" :value="fieldKey(field)" />
            <el-option v-if="fieldKey(check) && !selectableOutputs.some(field => fieldKey(field) === fieldKey(check))" :label="`原引用：${check.refType}:${check.refId}`" :value="fieldKey(check)" disabled />
          </el-select>
          <el-select :model-value="check.operator" :data-testid="`expectation-operator-${index}`" :aria-label="`校验 ${index + 1} 比较方式`" @update:model-value="updateCheck(index, { operator: $event })">
            <el-option v-for="operator in operators(check.valueType)" :key="operator.value" :label="operator.label" :value="operator.value" />
          </el-select>
        </div>
        <el-button v-if="currentField(check) && currentField(check).valueType !== check.valueType" link type="primary" @click="selectField(index, fieldKey(check))">重新确认此字段（保留预期值）</el-button>
        <template v-if="!valueFree(check.operator)">
          <div v-if="complex(check.valueType)" class="expectation-help">复杂对象的完整内容请使用 JSON 快照；这里可以校验字段是否存在或为空。</div>
          <template v-else-if="collection(check)">
            <condition-collection-input v-if="!check.expected || editableCollection(check.expected)" :value="collectionValue(check.expected)" :value-type="check.valueType" help-text="每行是一个预期成员，逗号和首尾空格原样保留，空字符串也按原值比较。数字与文本按各自类型比较。" @input="updateCheck(index, { expected: $event })" />
            <div v-else class="expectation-help">原预期不适合逐项编辑，内容已保留。<el-button link type="primary" @click="replaceExpected(index)">替换为逐项集合（清除原值）</el-button></div>
          </template>
          <template v-else-if="range(check.operator)">
            <condition-range-input v-if="!check.expected || check.expected.kind === 'LITERAL'" :value="check.expected" :value-type="check.valueType" @input="updateCheck(index, { expected: $event })" />
            <div v-else class="expectation-help">原预期不是固定区间，内容已保留。<el-button link type="primary" @click="replaceExpected(index)">替换为区间（清除原值）</el-button></div>
          </template>
          <div v-else-if="check.expected?.kind === 'LITERAL' && check.expected.value === null" class="expectation-help">原预期值为 null，内容已保留。可选择“为空值”比较，或<el-button link type="primary" @click="replaceExpected(index)">替换为固定值（清除 null）</el-button></div>
          <template v-else-if="!check.expected || scalarEditable(check.expected)">
            <el-select v-if="list(check.valueType)" :model-value="scalarType(check)" :aria-label="`校验 ${index + 1} 成员类型`" @update:model-value="setScalarType(index, $event)">
              <el-option label="文本" value="STRING" /><el-option label="数字" value="NUMBER" /><el-option label="布尔值" value="BOOLEAN" />
            </el-select>
            <el-select v-if="family(scalarType(check)) === 'BOOLEAN'" :model-value="scalarText(check) === 'true'" :data-testid="`expectation-value-${index}`" :aria-label="`校验 ${index + 1} 预期值`" @update:model-value="setScalar(index, $event)">
              <el-option label="是（true）" :value="true" /><el-option label="否（false）" :value="false" />
            </el-select>
            <el-input v-else :model-value="scalarText(check)" :data-testid="`expectation-value-${index}`" :aria-label="`校验 ${index + 1} 预期值`" :placeholder="family(check.valueType) === 'NUMBER' ? '填写数字，保留完整精度' : family(check.valueType) === 'DATE' ? '例如 2026-01-01' : '填写预期文本；留空表示空字符串'" @update:model-value="setScalar(index, $event)" />
          </template>
          <div v-else class="expectation-help">原预期不是可编辑的固定值，内容已保留。<el-button link type="primary" @click="replaceExpected(index)">替换为固定值（清除原值）</el-button></div>
        </template>
        <div v-if="checkError(check)" class="expectation-error" role="alert">{{ checkError(check) }}</div>
      </div>
      <el-button size="small" data-testid="expectation-add-check" :disabled="!selectableOutputs.length || checks.length >= 100" @click="addCheck">添加字段校验</el-button>
      <p v-if="!checks.length" class="expectation-help">尚未添加校验项，不判断用例是否符合预期。</p>
    </template>
    <template v-else-if="mode === 'SNAPSHOT'">
      <p class="expectation-help">{{ modelValue == null ? '已有 JSON 快照继续保留，不会自动转换为字段预期。' : '比较执行是否成功、业务输出和错误信息；不比较外层接口响应码及耗时。' }}</p>
      <MonacoEditor
        :value="snapshotJson"
        language="json"
        height="190px"
        data-testid="expectation-snapshot"
        aria-label="预期 JSON 快照"
        :options="snapshotEditorOptions"
        @update:value="$emit('update:snapshotJson', $event)"
      />
      <div v-if="snapshotError" class="expectation-error" role="alert">{{ snapshotError }}</div>
      <el-button size="small" data-testid="expectation-use-actual" :disabled="!actualResponseJson" @click="$emit('update:snapshotJson', actualResponseJson)">采用最近一次结果</el-button>
      <p class="expectation-help">采用结果会替换当前预期快照，请先确认业务结果正确；执行成功本身不代表符合预期。</p>
    </template>
    <p v-else class="expectation-help">只记录执行结果，不判断用例是否通过。</p>
  </div>
</template>

<script>
import ConditionRangeInput from '@/components/common/ConditionRangeInput.vue'
import ConditionCollectionInput from '@/components/common/ConditionCollectionInput.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { parseExactJson, parseExecutionSnapshotJson, stringifyExactJson, cloneExactJson, isExactNumber, exactNumberText } from '@/utils/exactJson'
import { isValidDecimalText, requireRangeBounds } from '@/utils/conditionRange'
import { requireCollectionItems } from '@/utils/conditionCollection'

const NUMERIC = ['NUMBER', 'BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY']
const DATES = ['DATE', 'DATETIME', 'TIMESTAMP', 'LOCALDATE', 'LOCALDATETIME']
const FREE = ['is_null', 'not_null', 'is_missing', 'exists']
const LABELS = { '==': '等于', '!=': '不等于', '>': '大于', '>=': '大于或等于', '<': '小于', '<=': '小于或等于', between: '介于（含边界）', not_between: '不介于（含边界）', in: '属于集合', not_in: '不属于集合', contains: '包含', not_contains: '不包含', contains_any: '包含任一成员', contains_all: '包含全部成员', is_null: '为空值（null）', not_null: '非空值', is_missing: '未输出此字段', exists: '已输出此字段' }
const upper = value => String(value || '').toUpperCase()
function stableId(value) {
  try {
    const raw = typeof value === 'number' || isExactNumber(value) ? exactNumberText(value) : String(value || '')
    return /^\d+$/.test(raw) && BigInt(raw) > 0n && BigInt(raw) <= 9223372036854775807n ? BigInt(raw).toString() : ''
  } catch { return '' }
}
function family(type) {
  const normalized = upper(type)
  return NUMERIC.includes(normalized) ? 'NUMBER' : DATES.includes(normalized) ? 'DATE' : ['BOOL', 'BOOLEAN'].includes(normalized) ? 'BOOLEAN' : ['ENUM', 'STRING'].includes(normalized) ? 'STRING' : normalized
}
const literal = (valueType, value) => ({ kind: 'LITERAL', valueType, value })

export default {
  name: 'ExpectedOutputEditor',
  components: { ConditionRangeInput, ConditionCollectionInput, MonacoEditor },
  props: {
    modelValue: { type: String, default: null },
    outputs: { type: Array, default: () => [] },
    snapshotJson: { type: String, default: '' },
    actualResponseJson: { type: String, default: '' },
  },
  emits: ['update:modelValue', 'update:snapshotJson'],
  computed: {
    snapshotEditorOptions() {
      return {
        minimap: { enabled: false },
        formatOnType: false,
        formatOnPaste: false,
        wordWrap: 'on',
      }
    },
    parsed() {
      if (this.modelValue == null) return { config: { version: 1, mode: 'SNAPSHOT', checks: [] }, error: '' }
      try {
        const config = parseExactJson(this.modelValue)
        if (!config || Array.isArray(config) || !isExactNumber(config.version) || Number(exactNumberText(config.version)) !== 1 || !['NONE', 'FIELDS', 'SNAPSHOT'].includes(config.mode)
          || (config.mode === 'FIELDS' && (!Array.isArray(config.checks) || config.checks.some(check => !check || typeof check !== 'object' || Array.isArray(check) || isExactNumber(check))))) throw new Error()
        return { config, error: '' }
      } catch { return { config: null, error: '预期配置格式无效，请核对原配置' } }
    },
    configurationError() { return this.parsed.error },
    mode() { return this.parsed.config?.mode || 'NONE' },
    checks() { return this.parsed.config?.checks || [] },
    selectableOutputs() {
      return this.outputs.filter(field => this.fieldKey(field) && field.valueType && (field.scriptName || field.code) && this.outputs.filter(other => this.fieldKey(other) === this.fieldKey(field)).length === 1)
    },
    snapshotError() {
      if (!this.snapshotJson?.trim()) return ''
      try {
        const value = parseExecutionSnapshotJson(this.snapshotJson)
        if (!value || typeof value !== 'object' || Array.isArray(value) || isExactNumber(value)) throw new Error()
        return ''
      } catch { return '预期快照必须是有效 JSON 对象，原文已保留' }
    },
  },
  methods: {
    family,
    fieldKey(field) { const id = stableId(field?.refId); return id && field?.refType ? `${upper(field.refType)}:${id}` : '' },
    fieldLabel(field) { return `${field.label || field.code || field.scriptName} (${field.scriptName || field.code})` },
    currentField(check) { return this.selectableOutputs.find(field => this.fieldKey(field) === this.fieldKey(check)) },
    valueFree(operator) { return FREE.includes(operator) },
    range(operator) { return ['between', 'not_between'].includes(operator) },
    list(type) { return ['LIST', 'ARRAY', 'VECTOR'].includes(upper(type)) },
    complex(type) { return ['MAP', 'OBJECT'].includes(upper(type)) },
    collection(check) { return ['in', 'not_in', 'contains_any', 'contains_all'].includes(check.operator) || (this.list(check.valueType) && ['==', '!='].includes(check.operator)) },
    operators(type) {
      let extra = []
      if (family(type) === 'NUMBER') extra = ['>', '>=', '<', '<=', 'between', 'not_between', 'in', 'not_in']
      else if (family(type) === 'DATE') extra = ['>', '>=', '<', '<=', 'between', 'not_between']
      else if (this.list(type)) extra = ['contains', 'not_contains', 'contains_any', 'contains_all']
      else if (family(type) === 'STRING') extra = ['contains', 'not_contains', 'in', 'not_in']
      else if (family(type) === 'BOOLEAN') extra = ['in', 'not_in']
      return ['==', '!=', ...extra, ...FREE].map(value => ({ value, label: LABELS[value] }))
    },
    emitConfig(config) { this.$emit('update:modelValue', stringifyExactJson(config)) },
    setMode(mode) { if (!this.configurationError) this.emitConfig({ ...this.parsed.config, version: 1, mode, checks: this.checks }) },
    resetConfiguration() { this.emitConfig({ version: 1, mode: 'NONE', checks: [] }) },
    updateCheck(index, changes) {
      const config = cloneExactJson(this.parsed.config)
      config.checks[index] = { ...config.checks[index], ...changes }
      this.emitConfig(config)
    },
    addCheck() {
      if (!this.selectableOutputs.length || this.checks.length >= 100) return
      this.emitConfig({ ...this.parsed.config, checks: [...this.checks, { refType: '', refId: '', valueType: '', operator: '==', expected: null }] })
    },
    removeCheck(index) { this.emitConfig({ ...this.parsed.config, checks: this.checks.filter((_, item) => item !== index) }) },
    selectField(index, key) {
      const field = this.selectableOutputs.find(item => this.fieldKey(item) === key)
      if (!field) return
      const check = { ...this.checks[index], refType: field.refType, refId: stableId(field.refId), valueType: field.valueType }
      if (check.expected == null && !this.valueFree(check.operator)) check.expected = this.emptyExpected(check)
      this.updateCheck(index, check)
    },
    emptyExpected(check) {
      if (this.collection(check)) return { kind: 'ARRAY', valueType: 'LIST', items: [] }
      if (this.range(check.operator)) return literal(check.valueType, ',')
      const type = this.list(check.valueType) ? 'STRING' : family(check.valueType) === 'NUMBER' ? 'NUMBER' : check.valueType
      return literal(type, family(type) === 'BOOLEAN' ? false : '')
    },
    replaceExpected(index) { this.updateCheck(index, { expected: this.emptyExpected(this.checks[index]) }) },
    scalarEditable(operand) {
      const raw = operand?.kind === 'CAST' && operand.targetType === 'NUMBER' && operand.operand?.kind === 'LITERAL' && operand.operand.valueType === 'STRING' ? operand.operand : operand
      return raw?.kind === 'LITERAL' && Object.hasOwn(raw, 'value') && (raw.value === null || ['string', 'boolean', 'number'].includes(typeof raw.value) || isExactNumber(raw.value))
    },
    scalarType(check) { return this.list(check.valueType) ? check.expected?.kind === 'CAST' ? 'NUMBER' : check.expected?.valueType || 'STRING' : check.valueType },
    scalarText(check) { const value = check.expected?.kind === 'CAST' ? check.expected.operand?.value : check.expected?.value; return value == null ? '' : String(value) },
    setScalar(index, value) { const type = this.scalarType(this.checks[index]); this.updateCheck(index, { expected: literal(family(type) === 'NUMBER' ? 'NUMBER' : type, family(type) === 'BOOLEAN' ? value : String(value)) }) },
    setScalarType(index, type) { this.updateCheck(index, { expected: literal(type, this.checks[index].expected?.value ?? '') }) },
    editableCollection(operand) { return operand?.kind === 'ARRAY' && Array.isArray(operand.items) && operand.items.every(item => this.scalarEditable(item) && !this.complex(item.valueType) && !this.list(item.valueType)) },
    collectionValue(operand) {
      if (!operand) return { kind: 'ARRAY', valueType: 'LIST', items: [] }
      // 集合控件使用普通 Operand；数值常量转换为原数字文本后再交给它，绝不经过 JS Number。
      const value = cloneExactJson(operand)
      value.items.forEach(item => {
        const raw = item.kind === 'CAST' ? item.operand : item
        if (isExactNumber(raw.value)) raw.value = exactNumberText(raw.value)
      })
      return value
    },
    literalError(operand, type, allowMixed = false) {
      if (!this.scalarEditable(operand)) return '预期仅支持固定值，原配置已保留，请明确替换后重新填写'
      const operandType = operand.kind === 'CAST' ? 'NUMBER' : operand.valueType
      const value = operand.kind === 'CAST' ? operand.operand.value : operand.value
      if (!allowMixed && family(type) !== family(operandType)) return '预期值类型与输出字段不一致，请重新填写或明确替换'
      if (value === null) return ''
      if (family(operandType) === 'NUMBER' && !isValidDecimalText(value)) return '请填写有效且可精确比较的数字'
      if (family(operandType) === 'BOOLEAN' && ![true, false, 'true', 'false'].includes(value)) return '布尔预期请选择是或否'
      if (family(operandType) === 'DATE') {
        try { requireRangeBounds(`${value},${value}`, operandType) } catch { return '请填写有效日期或时间' }
      }
      return ''
    },
    checkError(check) {
      if (!check || typeof check !== 'object') return '字段校验项格式无效，原配置已保留'
      const field = this.currentField(check)
      if (!field) return '输出字段引用已失效或尚未选择，请重新选择具有稳定 ID 的输出字段'
      if (upper(field.valueType) !== upper(check.valueType)) return '输出字段类型已变化，请重新确认字段并核对预期值'
      if (!this.operators(check.valueType).some(operator => operator.value === check.operator)) return '该字段类型不支持当前比较方式，请重新选择'
      if (this.valueFree(check.operator)) return ''
      if (this.range(check.operator)) {
        try { requireRangeBounds(check.expected?.value, check.valueType, check.expected?.kind === 'LITERAL' ? 'CONST' : 'VAR'); return '' } catch (error) { return error.message }
      }
      if (this.collection(check)) {
        if (!this.editableCollection(check.expected)) return '集合预期仅支持逐项固定值，原配置已保留，请明确替换'
        if (check.expected.items.length > 1000) return '集合最多配置 1000 个成员'
        try { requireCollectionItems(check.expected) } catch (error) { return error.message }
        return check.expected.items.map(item => this.literalError(item, check.valueType, true)).find(Boolean) || ''
      }
      if (this.complex(check.valueType)) return '复杂对象的内容预期请使用 JSON 快照，或选择是否存在/为空的比较方式'
      return this.literalError(check.expected, check.valueType, this.list(check.valueType))
    },
    validate() {
      const errors = this.configurationError ? [this.configurationError] : this.mode === 'FIELDS'
        ? [...(this.checks.length > 100 ? ['每个用例最多配置 100 项校验'] : []), ...this.checks.map(check => this.checkError(check)).filter(Boolean)]
        : this.mode === 'SNAPSHOT' && this.snapshotError ? [this.snapshotError] : []
      return { valid: errors.length === 0, errors }
    },
  },
}
</script>

<style scoped>
.expected-output-editor { min-width: 0; color: var(--tianshu-text-primary); }
.expectation-heading, .expectation-check-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.expectation-help { color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.6; margin: 8px 0; overflow-wrap: anywhere; }
.expectation-check { padding: 12px; margin: 10px 0; border: 1px solid var(--tianshu-border); border-radius: 6px; background: var(--tianshu-bg-surface); min-width: 0; }
.expectation-field-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.expectation-field-row > .el-select { flex: 1 1 180px; min-width: 0; }
.expectation-error { color: var(--tianshu-danger-text); font-size: 12px; line-height: 1.6; margin-top: 6px; overflow-wrap: anywhere; }
[data-testid="expectation-use-actual"] { margin-top: 8px; }
</style>
