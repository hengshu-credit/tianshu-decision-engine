import { analyzeSqlQuery } from './sqlQuery'

export function parseSqlParameters(text) {
  const values = JSON.parse(text || '[]')
  if (!Array.isArray(values)) throw new Error('SQL 参数必须是 JSON 数组')
  return values
}

export function sqlParameterType(value) {
  if (value && value.kind === 'REFERENCE') return 'REFERENCE'
  if (value && value.kind === 'LITERAL') return value.valueType
  if (value === null) return 'NULL'
  if (typeof value === 'number') return 'NUMBER'
  if (typeof value === 'boolean') return 'BOOLEAN'
  return 'LEGACY'
}

export function validateSqlParameters(sql, values) {
  const count = analyzeSqlQuery(sql).placeholderCount
  if (values.length !== count) return `SQL 需要 ${count} 个参数，当前配置了 ${values.length} 个，请核对顺序和数量`
  for (let i = 0; i < values.length; i++) {
    const value = values[i]
    if (value && value.kind === 'REFERENCE' && (!value.refId || !['VARIABLE', 'CONSTANT'].includes(value.refType))) {
      return `参数 ${i + 1} 请选择有效的业务字段`
    }
    if (value && value.kind === 'LITERAL' && value.valueType === 'NUMBER' &&
      (value.value === '' || value.value == null || !Number.isFinite(Number(value.value)))) {
      return `参数 ${i + 1} 请填写有效数值`
    }
  }
  return ''
}
