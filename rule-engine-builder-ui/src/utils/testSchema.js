import { setParamPath } from '@/utils/testSampleParams'

export function normalizeTestSchema(response) {
  const schema = response && response.data !== undefined ? response.data : (response || {})
  return {
    inputs: Array.isArray(schema.inputs) ? schema.inputs : [],
    outputs: Array.isArray(schema.outputs) ? schema.outputs : [],
    sampleParams: schema.sampleParams && typeof schema.sampleParams === 'object' ? schema.sampleParams : {},
    diagnostics: Array.isArray(schema.diagnostics) ? schema.diagnostics : []
  }
}

export function schemaFieldsToTestFields(inputs) {
  return (inputs || []).map(field => {
    const path = field.scriptName || field.code || ''
    return {
      ...field,
      id: field.id || field.refId,
      varId: field.refId,
      refType: field.refType,
      fieldName: path,
      scriptName: path,
      fieldLabel: field.label || field.code || path,
      fieldType: field.valueType || 'STRING',
      defaultValue: field.defaultValue,
      validValues: parseValidValues(field.validValues),
      status: 1
    }
  }).filter(field => field.fieldName)
}

export function flattenSchemaSample(fields, sampleParams) {
  const flat = {}
  ;(fields || []).forEach(field => {
    const path = field.scriptName || field.fieldName
    const value = readParamPath(sampleParams, path)
    flat[path] = value === undefined ? sampleValue(field) : value
  })
  return flat
}

export function buildNestedSchemaParams(fields, flatParams) {
  const nested = {}
  ;(fields || []).forEach(field => {
    const path = field.scriptName || field.fieldName
    if (path) setParamPath(nested, path, flatParams[path])
  })
  return nested
}

export function readParamPath(target, path) {
  const parts = String(path || '').split('.').filter(Boolean)
  let current = target
  for (let i = 0; i < parts.length; i++) {
    if (!current || typeof current !== 'object' || !(parts[i] in current)) return undefined
    current = current[parts[i]]
  }
  return current
}

function parseValidValues(value) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function sampleValue(field) {
  if (field.defaultValue !== undefined && field.defaultValue !== null && field.defaultValue !== '') {
    return field.defaultValue
  }
  const type = String(field.fieldType || field.valueType || 'STRING').toUpperCase()
  if (['INTEGER', 'INT', 'LONG', 'NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].indexOf(type) >= 0) return 0
  if (['BOOLEAN', 'BOOL'].indexOf(type) >= 0) return false
  if (['ARRAY', 'LIST', 'VECTOR'].indexOf(type) >= 0) return []
  if (['OBJECT', 'MAP'].indexOf(type) >= 0) return {}
  return ''
}
