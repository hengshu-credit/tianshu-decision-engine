const COMMON_OPERATORS = [
  { label: '等于', value: '==' },
  { label: '不等于', value: '!=' },
  { label: '为空', value: 'is_null', noValue: true },
  { label: '不为空', value: 'not_null', noValue: true }
]

const ORDER_OPERATORS = [
  { label: '大于', value: '>' },
  { label: '大于等于', value: '>=' },
  { label: '小于', value: '<' },
  { label: '小于等于', value: '<=' },
  { label: '介于', value: 'between', valuePlaceholder: '下限,上限', noVarValue: true },
  { label: '不介于', value: 'not_between', valuePlaceholder: '下限,上限', noVarValue: true }
]

const STRING_OPERATORS = [
  ...COMMON_OPERATORS,
  { label: '正则匹配', value: 'regex_match', rightContext: 'READ_EXPRESSION' },
  { label: '正则不匹配', value: 'not_regex_match', rightContext: 'READ_EXPRESSION' },
  { label: '包含', value: 'contains' },
  { label: '不包含', value: 'not_contains' },
  { label: '以...开头', value: 'starts_with' },
  { label: '不以...开头', value: 'not_starts_with' },
  { label: '以...结尾', value: 'ends_with' },
  { label: '不以...结尾', value: 'not_ends_with' },
  { label: '在列表中', value: 'in', valuePlaceholder: '逗号分隔多个值', noVarValue: true },
  { label: '不在列表中', value: 'not_in', valuePlaceholder: '逗号分隔多个值', noVarValue: true },
  { label: '在数组内', value: 'in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '不在数组内', value: 'not_in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '在名单内', value: 'in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '不在名单内', value: 'not_in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '为空字符串', value: 'is_empty', noValue: true },
  { label: '非空字符串', value: 'not_empty', noValue: true },
  { label: '任意', value: '*', noValue: true }
]

const NUMBER_OPERATORS = [
  ...COMMON_OPERATORS,
  ...ORDER_OPERATORS,
  { label: '在列表中', value: 'in', valuePlaceholder: '逗号分隔多个数值', noVarValue: true },
  { label: '不在列表中', value: 'not_in', valuePlaceholder: '逗号分隔多个数值', noVarValue: true },
  { label: '在数组内', value: 'in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '不在数组内', value: 'not_in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '在名单内', value: 'in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '不在名单内', value: 'not_in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '任意', value: '*', noValue: true }
]

const DATE_OPERATORS = [
  ...COMMON_OPERATORS,
  ...ORDER_OPERATORS,
  { label: '任意', value: '*', noValue: true }
]

const BOOLEAN_OPERATORS = [
  { label: '等于', value: '==' },
  { label: '不等于', value: '!=' },
  { label: '为 true', value: 'is_true', noValue: true },
  { label: '为 false', value: 'is_false', noValue: true },
  { label: '为空', value: 'is_null', noValue: true },
  { label: '不为空', value: 'not_null', noValue: true },
  { label: '在数组内', value: 'in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '不在数组内', value: 'not_in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '在名单内', value: 'in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '不在名单内', value: 'not_in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '任意', value: '*', noValue: true }
]

const ENUM_OPERATORS = [
  ...COMMON_OPERATORS,
  { label: '在枚举中', value: 'in', valuePlaceholder: '逗号分隔多个枚举值', noVarValue: true },
  { label: '不在枚举中', value: 'not_in', valuePlaceholder: '逗号分隔多个枚举值', noVarValue: true },
  { label: '在数组内', value: 'in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '不在数组内', value: 'not_in_array', rightContext: 'READ_EXPRESSION', rightValueType: 'LIST' },
  { label: '在名单内', value: 'in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '不在名单内', value: 'not_in_list', rightContext: 'LIST_QUERY_CONFIG' },
  { label: '任意', value: '*', noValue: true }
]

const LIST_OPERATORS = [
  { label: '包含元素', value: 'contains' },
  { label: '不包含元素', value: 'not_contains' },
  { label: '包含任一元素', value: 'contains_any', valuePlaceholder: '逗号分隔多个元素', noVarValue: true },
  { label: '包含全部元素', value: 'contains_all', valuePlaceholder: '逗号分隔多个元素', noVarValue: true },
  { label: '包含在数组元素内', value: 'array_element_contains', rightContext: 'READ_EXPRESSION' },
  { label: '不包含在数组元素内', value: 'array_element_not_contains', rightContext: 'READ_EXPRESSION' },
  { label: '包含元素以...开头', value: 'array_element_starts_with', rightContext: 'READ_EXPRESSION' },
  { label: '包含元素以...结尾', value: 'array_element_ends_with', rightContext: 'READ_EXPRESSION' },
  { label: '元素数量等于', value: 'size_eq', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '元素数量大于', value: 'size_gt', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '元素数量大于等于', value: 'size_gte', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '元素数量小于', value: 'size_lt', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '元素数量小于等于', value: 'size_lte', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '为空列表', value: 'is_empty', noValue: true },
  { label: '非空列表', value: 'not_empty', noValue: true },
  { label: '为空', value: 'is_null', noValue: true },
  { label: '不为空', value: 'not_null', noValue: true },
  { label: '任意', value: '*', noValue: true }
]

const MAP_OPERATORS = [
  { label: '包含键', value: 'has_key' },
  { label: '不包含键', value: 'not_has_key' },
  { label: '包含值', value: 'has_value', rightContext: 'READ_EXPRESSION' },
  { label: '不包含值', value: 'not_has_value', rightContext: 'READ_EXPRESSION' },
  { label: '键数量等于', value: 'size_eq', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '键数量大于', value: 'size_gt', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '键数量大于等于', value: 'size_gte', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '键数量小于', value: 'size_lt', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '键数量小于等于', value: 'size_lte', rightContext: 'READ_EXPRESSION', rightValueType: 'NUMBER' },
  { label: '为空映射', value: 'is_empty', noValue: true },
  { label: '非空映射', value: 'not_empty', noValue: true },
  { label: '为空', value: 'is_null', noValue: true },
  { label: '不为空', value: 'not_null', noValue: true },
  { label: '任意', value: '*', noValue: true }
]

const OBJECT_OPERATORS = [
  { label: '为空', value: 'is_null', noValue: true },
  { label: '不为空', value: 'not_null', noValue: true },
  { label: '任意', value: '*', noValue: true }
]

const SOURCE_OUTCOME_OPERATORS = [
  { label: '调用成功', value: 'source_success', noValue: true, dimension: 'OUTCOME', expected: 'SUCCESS' },
  { label: '调用异常', value: 'source_error', noValue: true, dimension: 'OUTCOME', expected: 'ERROR' },
  { label: '调用超时', value: 'source_timeout', noValue: true, dimension: 'OUTCOME', expected: 'TIMEOUT' }
]

const API_STATUS_OPERATORS = [
  ...SOURCE_OUTCOME_OPERATORS,
  { label: '使用了兜底值', value: 'source_fallback', noValue: true, dimension: 'FALLBACK_USED', expected: 'TRUE' },
  { label: '已启用缓存', value: 'source_cache_enabled', noValue: true, dimension: 'CACHE_CONFIGURED', expected: 'TRUE' },
  { label: '未启用缓存', value: 'source_cache_disabled', noValue: true, dimension: 'CACHE_CONFIGURED', expected: 'FALSE' },
  { label: '缓存命中', value: 'source_cache_hit', noValue: true, dimension: 'CACHE_STATE', expected: 'HIT' },
  { label: '缓存未命中', value: 'source_cache_miss', noValue: true, dimension: 'CACHE_STATE', expected: 'MISS' },
  { label: '缓存不可用', value: 'source_cache_unavailable', noValue: true, dimension: 'CACHE_STATE', expected: 'UNAVAILABLE' },
  { label: '来自实时调用', value: 'source_origin_live', noValue: true, dimension: 'DATA_ORIGIN', expected: 'LIVE' },
  { label: '来自有效缓存', value: 'source_origin_cache', noValue: true, dimension: 'DATA_ORIGIN', expected: 'CACHE' },
  { label: '来自过期缓存', value: 'source_origin_stale_cache', noValue: true, dimension: 'DATA_ORIGIN', expected: 'STALE_CACHE' }
]

const DB_STATUS_OPERATORS = [
  ...SOURCE_OUTCOME_OPERATORS,
  { label: '使用了兜底值', value: 'source_fallback', noValue: true, dimension: 'FALLBACK_USED', expected: 'TRUE' },
  { label: '查询有数据', value: 'source_has_data', noValue: true, dimension: 'DATA_STATE', expected: 'HAS_DATA' },
  { label: '查询无数据', value: 'source_no_data', noValue: true, dimension: 'DATA_STATE', expected: 'NO_DATA' }
]

const LIST_STATUS_OPERATORS = [
  ...SOURCE_OUTCOME_OPERATORS,
  { label: '名单命中', value: 'source_match_hit', noValue: true, dimension: 'MATCH_STATE', expected: 'HIT' },
  { label: '名单未命中', value: 'source_match_miss', noValue: true, dimension: 'MATCH_STATE', expected: 'MISS' }
]

const MODEL_STATUS_OPERATORS = [
  ...SOURCE_OUTCOME_OPERATORS,
  { label: '输出存在', value: 'source_output_present', noValue: true, dimension: 'PRESENCE', expected: 'PRESENT' },
  { label: '输出缺失', value: 'source_output_missing', noValue: true, dimension: 'PRESENCE', expected: 'MISSING' }
]

const DATA_OBJECT_STATUS_OPERATORS = [
  { label: '字段存在', value: 'source_field_present', noValue: true, dimension: 'PRESENCE', expected: 'PRESENT' },
  { label: '字段缺失', value: 'source_field_missing', noValue: true, dimension: 'PRESENCE', expected: 'MISSING' },
  { label: '类型转换异常', value: 'source_field_invalid', noValue: true, dimension: 'PRESENCE', expected: 'INVALID' }
]

const SOURCE_STATUS_OPERATORS = [
  ...API_STATUS_OPERATORS,
  ...DB_STATUS_OPERATORS,
  ...LIST_STATUS_OPERATORS,
  ...MODEL_STATUS_OPERATORS,
  ...DATA_OBJECT_STATUS_OPERATORS
]
const SOURCE_STATUS_OPERATOR_VALUES = new Set(SOURCE_STATUS_OPERATORS.map(item => item.value))

export function normalizeVarType(varType) {
  const type = String(varType || 'STRING').toUpperCase()
  if (['BYTE', 'SHORT', 'INT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'BIGDECIMAL', 'PROBABILITY'].includes(type)) return 'NUMBER'
  if (['DATE', 'DATETIME', 'TIMESTAMP', 'LOCALDATE', 'LOCALDATETIME'].includes(type)) return 'DATE'
  if (['BOOL'].includes(type)) return 'BOOLEAN'
  if (['ARRAY', 'SET', 'COLLECTION', 'VECTOR'].includes(type)) return 'LIST'
  if (type === 'MODEL') return 'OBJECT'
  return type
}

function valueOperatorOptions(varType) {
  const type = normalizeVarType(varType)
  if (type === 'NUMBER') return NUMBER_OPERATORS
  if (type === 'DATE') return DATE_OPERATORS
  if (type === 'BOOLEAN') return BOOLEAN_OPERATORS
  if (type === 'ENUM') return ENUM_OPERATORS
  if (type === 'OBJECT') return OBJECT_OPERATORS
  if (type === 'LIST') return LIST_OPERATORS
  if (type === 'MAP') return MAP_OPERATORS
  return STRING_OPERATORS
}

function sourceStatusOptions(sourceContext) {
  const context = sourceContext || {}
  const ref = context._ref || {}
  const refType = String(context.refType || context._refType || ref.refType || '').toUpperCase()
  const varObj = context.varObj || ref.varObj || {}
  const varSource = String(context.varSource || context.sourceType || ref.varSource || ref.sourceType || varObj.varSource || '').toUpperCase()
  if (refType === 'DATA_OBJECT' || refType === 'DATA_FIELD') return DATA_OBJECT_STATUS_OPERATORS
  if (refType === 'MODEL_OUTPUT' || refType === 'MODEL') return MODEL_STATUS_OPERATORS
  if (refType !== 'VARIABLE') return []
  if (varSource === 'API') return API_STATUS_OPERATORS
  if (varSource === 'DB') return DB_STATUS_OPERATORS
  if (varSource === 'LIST') return LIST_STATUS_OPERATORS
  return []
}

export function getConditionOperatorGroups(varType, sourceContext) {
  const groups = [{ label: '值判断', options: valueOperatorOptions(varType) }]
  const statusOptions = sourceStatusOptions(sourceContext)
  if (statusOptions.length) groups.push({ label: '来源状态', options: statusOptions })
  return groups
}

export function getConditionOperatorOptions(varType, sourceContext) {
  return getConditionOperatorGroups(varType, sourceContext).reduce((result, group) => result.concat(group.options), [])
}

export function isSourceStatusOperator(operator) {
  return SOURCE_STATUS_OPERATOR_VALUES.has(operator)
}

export function compileSourceStatusExpression(operand, operator) {
  const option = SOURCE_STATUS_OPERATORS.find(item => item.value === operator)
  if (!option || !operand || operand.refId == null || !operand.refType) return 'true'
  return 'sourceStatus(' + escapeString(operand.refType) + ', ' + escapeString(operand.refId) + ', ' +
    escapeString(option.dimension) + ', ' + escapeString(option.expected) + ')'
}

export function findConditionOperator(operator, varType, sourceContext) {
  return getConditionOperatorOptions(varType, sourceContext).find(item => item.value === operator) || null
}

export function conditionOperatorRequiresValue(operator, varType, sourceContext) {
  const option = findConditionOperator(operator, varType, sourceContext)
  return !(option && option.noValue)
}

export function conditionOperatorAllowsVarValue(operator, varType, sourceContext) {
  const option = findConditionOperator(operator, varType, sourceContext)
  return !!option && !option.noValue && !option.noVarValue
}

export function normalizeConditionOperator(operator, varType, sourceContext) {
  const options = getConditionOperatorOptions(varType, sourceContext)
  if (options.some(item => item.value === operator)) return operator
  return options.length ? options[0].value : '=='
}

export function conditionValuePlaceholder(operator, varType, sourceContext) {
  const option = findConditionOperator(operator, varType, sourceContext)
  if (option && option.valuePlaceholder) return option.valuePlaceholder
  const type = normalizeVarType(varType)
  if (type === 'NUMBER') return '数值'
  if (type === 'BOOLEAN') return '布尔值'
  return '值'
}

function escapeString(value) {
  return '"' + String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

function formatSmartConstant(value) {
  const text = String(value).trim()
  if (text === 'true' || text === 'false' || text === 'null') return text
  if (text !== '' && !isNaN(text)) return text
  if (text.startsWith('"') || text.startsWith("'")) return text
  return escapeString(text)
}

function formatConditionConstant(varType, value) {
  const text = String(value)
  if (!varType) return formatSmartConstant(text)
  const type = normalizeVarType(varType)
  if (type === 'NUMBER') return text
  if (type === 'BOOLEAN') return text === true || text === 'true' ? 'true' : 'false'
  if (type === 'LIST' || type === 'MAP') return formatSmartConstant(text)
  return escapeString(text)
}

function splitValues(value) {
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function formatList(varType, value) {
  const values = splitValues(value)
  const type = normalizeVarType(varType)
  return '[' + values.map(item => type === 'NUMBER' ? item : formatSmartConstant(item)).join(', ') + ']'
}

function formatBetweenParts(varType, value) {
  const values = splitValues(value)
  if (values.length < 2) return null
  return [
    formatConditionConstant(varType, values[0]),
    formatConditionConstant(varType, values[1])
  ]
}

export function compileConditionExpression(left, varType, operator, value, valueKind) {
  if (!left) return 'true'
  const op = operator || '=='
  if (op === '*') return 'true'
  if (op === 'is_null') return left + ' == null'
  if (op === 'not_null') return left + ' != null'
  if (op === 'is_empty') return 'isBlank(' + left + ')'
  if (op === 'not_empty') return 'isNotBlank(' + left + ')'
  if (op === 'is_true') return left + ' == true'
  if (op === 'is_false') return left + ' == false'

  if (value === undefined || value === null || String(value) === '') return 'true'

  const rhs = valueKind === 'VAR' ? value : formatConditionConstant(varType, value)
  if (['==', '!=', '>', '>=', '<', '<='].includes(op)) return left + ' ' + op + ' ' + rhs
  if (op === 'contains') return 'containsValue(' + left + ', ' + rhs + ')'
  if (op === 'not_contains') return '!containsValue(' + left + ', ' + rhs + ')'
  if (op === 'starts_with') return 'startsWithValue(' + left + ', ' + rhs + ')'
  if (op === 'not_starts_with') return '!startsWithValue(' + left + ', ' + rhs + ')'
  if (op === 'ends_with') return 'endsWithValue(' + left + ', ' + rhs + ')'
  if (op === 'not_ends_with') return '!endsWithValue(' + left + ', ' + rhs + ')'
  if (op === 'regex_match') return 'regexMatchValue(' + left + ', ' + rhs + ')'
  if (op === 'not_regex_match') return '!regexMatchValue(' + left + ', ' + rhs + ')'
  if (op === 'in_array') return 'containsValue(' + rhs + ', ' + left + ')'
  if (op === 'not_in_array') return '!containsValue(' + rhs + ', ' + left + ')'
  if (op === 'in_list') return 'isInLists(' + left + ', ' + rhs + ')'
  if (op === 'not_in_list') return '!isInLists(' + left + ', ' + rhs + ')'
  if (op === 'in') return left + ' in ' + formatList(varType, value)
  if (op === 'not_in') return '!' + '(' + left + ' in ' + formatList(varType, value) + ')'
  if (op === 'between' || op === 'not_between') {
    const parts = formatBetweenParts(varType, value)
    if (!parts) return 'true'
    const expr = '(' + left + ' >= ' + parts[0] + ' && ' + left + ' <= ' + parts[1] + ')'
    return op === 'between' ? expr : '!' + expr
  }
  if (op === 'contains_any' || op === 'contains_all') {
    if (!splitValues(value).length) return 'true'
    return (op === 'contains_any' ? 'containsAnyValue(' : 'containsAllValues(') + left + ', ' + formatList(null, value) + ')'
  }
  if (op === 'has_key') return 'hasKey(' + left + ', ' + rhs + ')'
  if (op === 'not_has_key') return '!hasKey(' + left + ', ' + rhs + ')'
  if (op === 'has_value') return 'hasMapValue(' + left + ', ' + rhs + ')'
  if (op === 'not_has_value') return '!hasMapValue(' + left + ', ' + rhs + ')'
  if (op === 'array_element_contains') return 'containsElementValue(' + left + ', ' + rhs + ')'
  if (op === 'array_element_not_contains') return '!containsElementValue(' + left + ', ' + rhs + ')'
  if (op === 'array_element_starts_with') return 'elementStartsWithValue(' + left + ', ' + rhs + ')'
  if (op === 'array_element_ends_with') return 'elementEndsWithValue(' + left + ', ' + rhs + ')'
  const sizeOperators = { size_eq: '==', size_gt: '>', size_gte: '>=', size_lt: '<', size_lte: '<=' }
  if (sizeOperators[op]) return 'sizeOfValue(' + left + ') ' + sizeOperators[op] + ' ' + rhs
  return left + ' ' + op + ' ' + rhs
}
