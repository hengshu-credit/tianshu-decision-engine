<template>
  <section class="derived-editor" data-testid="derived-variable-editor">
    <el-form-item label="衍生方式">
      <el-radio-group :model-value="config.mode" @update:model-value="setMode">
        <el-radio-button value="EXPRESSION">上游字段计算</el-radio-button>
        <el-radio-button value="HISTORY">内部进件统计</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <template v-if="config.mode === 'EXPRESSION'">
      <el-form-item label="计算逻辑">
        <operand-picker :value="config.expression" :vars="vars" :functions="functions" :allowed-kinds="operandKinds" :expected-type="resultType" editor-title="衍生变量计算逻辑" placeholder="选择上游字段、函数或配置组合表达式" @input="patch({ expression: $event })" />
      </el-form-item>
      <p class="derived-help">支持多级衍生。字段按 ID 关联；测试和 API 入参会展开到最上游原始字段。身份证年龄可选内置 idCardAge；登记地址码不等同于当前户籍地。</p>
    </template>
    <template v-else>
      <el-alert :closable="false" type="info" title="仅统计已完成的正式进件，不含测试和本次请求；子规则统一归属最外层规则。新快照启用前的旧日志不会自动回填。" />
      <el-form-item label="统计范围">
        <el-select :model-value="config.scope" @update:model-value="patch({ scope: $event })">
          <el-option label="当前最外层规则" value="RULE" />
          <el-option label="当前执行项目" value="PROJECT" />
          <el-option label="全局进件" value="GLOBAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="最近时间窗口">
        <el-input-number :model-value="config.window" :min="1" :max="36500" :precision="0" @update:model-value="patch({ window: $event })" />
        <el-select class="unit-select" :model-value="config.windowUnit" @update:model-value="patch({ windowUnit: $event })">
          <el-option label="分钟" value="MINUTE" /><el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" />
        </el-select>
      </el-form-item>
      <div class="history-steps">
        <h4>关联路径</h4>
        <p class="derived-help">不配置关联时统计范围内全部进件。例如：本次联系人 → 历史联系人 → 该进件申请人 → 此申请人的其他历史进件。每层命中记录只保留一次。</p>
        <div v-for="(step, index) in config.steps" :key="index" class="history-step">
          <div class="step-title"><strong>第 {{ index + 1 }} 层：{{ index === 0 ? '本次请求关联历史' : '上一层命中进件关联历史' }}</strong><el-button text type="danger" @click="removeStep(index)">移除此层及后续</el-button></div>
          <div v-for="(field, pairIndex) in step.fields" :key="pairIndex" class="join-pair">
            <operand-picker :value="index === 0 ? step.inputs[pairIndex] : step.fromFields[pairIndex]" :vars="vars" :functions="functions" :allowed-kinds="index === 0 ? operandKinds : ['REFERENCE']" :placeholder="index === 0 ? '本次字段 / 表达式' : '上一层的历史字段'" @input="setPair(index, pairIndex, index === 0 ? 'inputs' : 'fromFields', $event)" />
            <span>匹配</span>
            <operand-picker :value="field" :vars="vars" :allowed-kinds="['REFERENCE']" placeholder="历史匹配字段" @input="setPair(index, pairIndex, 'fields', $event)" />
            <el-button v-if="step.fields.length > 1" text type="danger" @click="removePair(index, pairIndex)">移除</el-button>
          </div>
          <el-button text @click="addPair(index)">添加联合关联字段</el-button>
          <history-filters :model-value="step.filters || []" :vars="vars" :functions="functions" @update:model-value="patchStep(index, { filters: $event })" />
        </div>
        <el-button :disabled="config.steps.length >= 8" @click="addStep">添加关联层</el-button>
      </div>
      <el-form-item label="最终属性筛选"><history-filters :model-value="config.filters" :vars="vars" :functions="functions" @update:model-value="patch({ filters: $event })" /></el-form-item>
      <el-form-item label="地理范围筛选"><el-switch :model-value="!!config.geo" @update:model-value="toggleGeo" /></el-form-item>
      <template v-if="config.geo">
        <el-form-item v-for="item in geoItems" :key="item.key" :label="item.label">
          <operand-picker :value="config.geo[item.key]" :vars="vars" :functions="functions" :allowed-kinds="item.history ? ['REFERENCE'] : operandKinds" expected-type="NUMBER" @input="patch({ geo: { ...config.geo, [item.key]: $event } })" />
        </el-form-item>
      </template>
      <el-form-item label="统计属性字段">
        <operand-picker :value="config.valueField" :vars="vars" :allowed-kinds="['REFERENCE']" placeholder="计数可不选；其他统计请选择历史属性" @input="setValueField" />
      </el-form-item>
      <el-form-item label="统计方法">
        <el-select :model-value="config.aggregate" @update:model-value="setAggregate">
          <el-option v-for="option in aggregateOptions" :key="option.value" :value="option.value" :label="option.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="主体 key">
        <el-select multiple filterable :model-value="subjectKeys" placeholder="可选单字段或联合字段，如身份证 + 手机号" @update:model-value="setSubjects">
          <el-option v-for="option in vars" :key="fieldKey(option)" :value="fieldKey(option)" :label="option.varLabel || option.varCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="重复主体取值">
        <el-select :model-value="config.recordMode" @update:model-value="patch({ recordMode: $event })">
          <el-option label="保留每条进件记录" value="ALL" /><el-option label="每个主体只取最新一条" value="LATEST_PER_SUBJECT" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="config.aggregate === 'CUSTOM'" label="聚合函数">
        <el-select filterable :model-value="config.functionId" placeholder="函数接收一个统计值列表" @update:model-value="setFunction">
          <el-option v-for="fn in functions" :key="fn.id" :value="fn.id" :label="fn.funcName || fn.funcCode" />
        </el-select>
      </el-form-item>
      <p class="derived-help">空主体不参与去重；空属性不参与聚合。无样本时计数与求和返回 0，均值/方差等返回空；样本统计不足两条返回空。字符串长度按 Unicode 字符数合计。单次窗口超过 10 万条会明确报错，不返回截断结果。</p>
      <p v-if="config.scope === 'RULE'" class="derived-help">当前规则范围请在规则测试中验证，单个变量预览没有所属根规则上下文。</p>
    </template>
  </section>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'
import HistoryFilters from './HistoryFilters.vue'
import { createDerivedConfig, DERIVED_OPERAND_KINDS, historyAggregateOptions } from '@/utils/derivedVariable'

export default {
  name: 'DerivedVariableEditor',
  components: { OperandPicker, HistoryFilters },
  props: { modelValue: { type: Object, default: createDerivedConfig }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, resultType: { type: String, default: '' } },
  emits: ['update:modelValue', 'type-change'],
  data() {
    return { operandKinds: DERIVED_OPERAND_KINDS, geoItems: [
      { key: 'longitude', label: '本次中心经度' }, { key: 'latitude', label: '本次中心纬度' }, { key: 'radiusMeters', label: '范围半径（米）' },
      { key: 'longitudeField', label: '历史经度字段', history: true }, { key: 'latitudeField', label: '历史纬度字段', history: true },
    ] }
  },
  computed: {
    config() { return { ...createDerivedConfig(), ...this.modelValue } },
    valueType() {
      const field = this.config.valueField
      return this.vars.find(item => this.fieldKey(item) === `${field?.refType}:${field?.refId}`)?.varType || field?.valueType || ''
    },
    aggregateOptions() { return historyAggregateOptions(this.valueType) },
    subjectKeys() { return this.config.subjectFields.map(field => `${field.refType}:${field.refId}`) },
  },
  methods: {
    patch(value) { this.$emit('update:modelValue', { ...this.config, ...value }) },
    setMode(mode) { this.patch({ mode }); if (mode === 'HISTORY') this.$emit('type-change', 'NUMBER') },
    setAggregate(aggregate) { this.patch({ aggregate }); if (aggregate !== 'CUSTOM') this.$emit('type-change', ['MIN', 'MAX'].includes(aggregate) ? this.valueType : 'NUMBER') },
    setValueField(valueField) {
      const type = this.vars.find(item => this.fieldKey(item) === `${valueField?.refType}:${valueField?.refId}`)?.varType || valueField?.valueType
      const available = historyAggregateOptions(type)
      const aggregate = available.some(option => option.value === this.config.aggregate) ? this.config.aggregate : 'COUNT'
      this.patch({ valueField, aggregate })
      if (['MIN', 'MAX'].includes(aggregate)) this.$emit('type-change', type)
    },
    setFunction(functionId) { this.patch({ functionId }); const fn = this.functions.find(item => item.id === functionId); if (fn?.returnType) this.$emit('type-change', fn.returnType) },
    fieldKey(field) { return `${field._refType || field.refType}:${field._varId || field.id}` },
    setSubjects(keys) { this.patch({ subjectFields: keys.map(key => { const field = this.vars.find(item => this.fieldKey(item) === key); return { kind: 'REFERENCE', refType: field._refType || field.refType, refId: field._varId || field.id, code: field.varCode, valueType: field.varType, resolved: true } }) }) },
    addStep() { this.patch({ steps: [...this.config.steps, { inputs: [null], fromFields: [null], fields: [null], filters: [] }] }) },
    removeStep(index) { this.patch({ steps: this.config.steps.slice(0, index) }) },
    patchStep(index, value) { this.patch({ steps: this.config.steps.map((step, i) => i === index ? { ...step, ...value } : step) }) },
    setPair(index, pairIndex, key, value) { const items = [...this.config.steps[index][key]]; items[pairIndex] = value; this.patchStep(index, { [key]: items }) },
    addPair(index) { const step = this.config.steps[index]; this.patchStep(index, { fields: [...step.fields, null], inputs: [...step.inputs, null], fromFields: [...step.fromFields, null] }) },
    removePair(index, pairIndex) { const step = this.config.steps[index]; this.patchStep(index, Object.fromEntries(['fields', 'inputs', 'fromFields'].map(key => [key, step[key].filter((item, i) => i !== pairIndex)]))) },
    toggleGeo(enabled) { this.patch({ geo: enabled ? { longitude: null, latitude: null, radiusMeters: { kind: 'LITERAL', valueType: 'NUMBER', value: 1000 }, longitudeField: null, latitudeField: null } : null }) },
  },
}
</script>

<style scoped>
.derived-editor { width: 100%; min-width: 0; }
.derived-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.7; margin: 8px 0 16px; }
.history-steps { margin: 16px 0; }
.history-step { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 12px; margin: 12px 0; }
.step-title, .join-pair { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.step-title { justify-content: space-between; }
.join-pair > .operand-picker { flex: 1; min-width: 190px; }
.unit-select { width: 100px; margin-left: 10px; }
.el-alert { margin-bottom: 16px; }
</style>
