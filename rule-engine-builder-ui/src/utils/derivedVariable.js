import { collectOperandReferences, validateOperand } from '@/utils/operand'

export const DERIVED_OPERAND_KINDS = ['REFERENCE', 'LITERAL', 'FUNCTION', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY']
const NUMERIC_TYPES = ['NUMBER', 'DOUBLE', 'INTEGER', 'LONG', 'FLOAT', 'DECIMAL', 'PROBABILITY', 'INT']
export const HISTORY_AGGREGATES = [
  { value: 'COUNT', label: '进件数（记录计数）', types: null },
  { value: 'DISTINCT_COUNT', label: '主体去重数（人数 / 唯一值数）', types: null },
  { value: 'SUM', label: '求和', types: NUMERIC_TYPES },
  { value: 'MAX', label: '最大值', types: [...NUMERIC_TYPES, 'STRING', 'ENUM', 'DATE', 'DATETIME'] },
  { value: 'MIN', label: '最小值', types: [...NUMERIC_TYPES, 'STRING', 'ENUM', 'DATE', 'DATETIME'] },
  { value: 'AVG', label: '平均值', types: NUMERIC_TYPES },
  { value: 'STDDEV', label: '总体标准差', types: NUMERIC_TYPES },
  { value: 'VARIANCE', label: '总体方差', types: NUMERIC_TYPES },
  { value: 'SAMPLE_STDDEV', label: '样本标准差', types: NUMERIC_TYPES },
  { value: 'SAMPLE_VARIANCE', label: '样本方差', types: NUMERIC_TYPES },
  { value: 'STRING_LENGTH', label: '字符串长度合计', types: ['STRING', 'ENUM'] },
  { value: 'CUSTOM', label: '自定义函数（接收值列表）', types: null },
]

export function createDerivedConfig() {
  return { mode: 'EXPRESSION', expression: null, scope: 'PROJECT', window: 30, windowUnit: 'DAY', steps: [], filters: [],
    subjectFields: [], recordMode: 'ALL', aggregate: 'COUNT', valueField: null, functionId: null }
}

export function derivedCurrentInputs(config) {
  if (config.mode === 'EXPRESSION') return config.expression ? [config.expression] : []
  const steps = config.steps || []
  const inputs = [...(steps[0]?.inputs || [])]
  for (const filter of [...(config.filters || []), ...steps.flatMap(step => step.filters || [])]) {
    if (filter.value) inputs.push(filter.value)
  }
  if (config.geo) inputs.push(config.geo.longitude, config.geo.latitude, config.geo.radiusMeters)
  return inputs.filter(Boolean)
}

export function derivedInputReferences(config) {
  return derivedCurrentInputs(config).flatMap(collectOperandReferences)
}

export function historyAggregateOptions(type) {
  return HISTORY_AGGREGATES.filter(option => !option.types || option.types.includes(type))
}

export function historyFieldEligible(field) {
  const type = field._refType || field.refType
  const source = field.varSource || field.varObj?.varSource || (type === 'MODEL_OUTPUT' ? 'MODEL' : type === 'CONSTANT' ? 'CONSTANT' : 'INPUT')
  return ['INPUT', 'API'].includes(source) || field.recordResult === true || field.varObj?.recordResult === true
}

export function validateDerivedConfig(config) {
  if (!config || !['EXPRESSION', 'HISTORY'].includes(config.mode)) return '请选择衍生方式'
  if (config.mode === 'EXPRESSION' && !config.expression) return '请配置衍生表达式'
  for (const operand of derivedCurrentInputs(config)) {
    const errors = validateOperand(operand, { allowedKinds: DERIVED_OPERAND_KINDS })
    if (errors.length) return errors[0].message
  }
  if (config.mode === 'EXPRESSION') return ''
  if (!Number.isInteger(config.window) || config.window < 1 || config.window > 36500) return '时间窗口必须是 1 至 36500 的整数'
  if (!['RULE', 'PROJECT', 'GLOBAL'].includes(config.scope)) return '请选择历史统计范围'
  if (!['MINUTE', 'HOUR', 'DAY', 'CALENDAR_DAY'].includes(config.windowUnit)) return '请选择时间窗口单位'
  const fieldError = field => !field || field.kind !== 'REFERENCE' || !field.refId || !field.refType
  if (config.aggregate === 'DISTINCT_COUNT' || config.recordMode === 'LATEST_PER_SUBJECT') {
    if (!config.subjectFields?.length || config.subjectFields.some(fieldError)) return '请选择主体 key 字段'
  }
  if (!['COUNT', 'DISTINCT_COUNT'].includes(config.aggregate) && fieldError(config.valueField)) return '请选择统计属性字段'
  if (config.aggregate === 'CUSTOM' && !config.functionId) return '请选择自定义聚合函数'
  const steps = config.steps || []
  if (steps.length > 8) return '关联步骤不能超过 8 层'
  for (const [index, step] of steps.entries()) {
    const sources = index === 0 ? step.inputs : step.fromFields
    if (!step.fields?.length || sources?.length !== step.fields.length || step.fields.some(fieldError) || sources.some(item => !item)) return `请完整配置第 ${index + 1} 层关联`
    if (index > 0 && sources.some(fieldError)) return '后续关联的来源必须选择历史字段'
  }
  for (const filter of [...(config.filters || []), ...steps.flatMap(step => step.filters || [])]) {
    if (fieldError(filter.field)) return '请选择历史筛选字段'
    if (!['IS_NULL', 'NOT_NULL'].includes(filter.operator) && !filter.value) return '请配置筛选比较值'
  }
  if (config.geo && (fieldError(config.geo.longitudeField) || fieldError(config.geo.latitudeField)
    || !config.geo.longitude || !config.geo.latitude || !config.geo.radiusMeters)) return '请完整配置中心点、历史经纬度和半径'
  return ''
}
