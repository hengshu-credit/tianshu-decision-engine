function arrayOf(value) {
  return Array.isArray(value) ? value : []
}

function text(value) {
  return value == null ? '' : String(value)
}

function fieldName(field) {
  return text(field && (field.scriptName || field.varCode)).trim()
}

function sampleValue(field) {
  let configuredValue
  if (field && field.exampleValue !== undefined && field.exampleValue !== null && field.exampleValue !== '') {
    configuredValue = field.exampleValue
  } else if (field && field.defaultValue !== undefined && field.defaultValue !== null && field.defaultValue !== '') {
    configuredValue = field.defaultValue
  }
  if (configuredValue !== undefined) {
    if (typeof configuredValue !== 'string') return configuredValue
    const raw = configuredValue.trim()
    const type = text(field && field.varType).toUpperCase()
    if (raw.toLowerCase() === 'null') return null
    if (['INTEGER', 'INT', 'LONG', 'NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].includes(type)) {
      const value = Number(raw)
      return Number.isNaN(value) ? configuredValue : value
    }
    if (['BOOLEAN', 'BOOL'].includes(type)) return raw === '1' || raw.toLowerCase() === 'true'
    if (['ARRAY', 'LIST', 'VECTOR', 'OBJECT', 'MAP'].includes(type)) {
      try {
        return JSON.parse(raw)
      } catch (e) {
        return configuredValue
      }
    }
    if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) {
      return raw.slice(1, -1)
    }
    return configuredValue
  }
  const type = text(field && field.varType).toUpperCase()
  if (['INTEGER', 'INT', 'LONG', 'NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].includes(type)) return 0
  if (['BOOLEAN', 'BOOL'].includes(type)) return false
  if (['ARRAY', 'LIST', 'VECTOR'].includes(type)) return []
  if (['OBJECT', 'MAP'].includes(type)) return {}
  return ''
}

function toField(path, field) {
  return {
    path,
    type: text(field && field.varType) || 'STRING',
    required: Boolean(field && field.required),
    label: text(field && field.varLabel),
    description: text(field && field.description),
    exampleValue: sampleValue(field)
  }
}

function normalizeObjectFieldPath(objectInfo, field, fieldsByCode, resolving) {
  const root = text(objectInfo && (objectInfo.scriptName || objectInfo.objectCode)).trim()
  const ownName = fieldName(field)
  if (!ownName) return ''
  if (root && (ownName === root || ownName.startsWith(root + '.'))) return ownName
  if (ownName.includes('.')) return ownName

  const parentCode = text(field && field.parentVarCode).trim()
  if (parentCode && fieldsByCode[parentCode] && !resolving[parentCode]) {
    resolving[parentCode] = true
    const parentPath = normalizeObjectFieldPath(objectInfo, fieldsByCode[parentCode], fieldsByCode, resolving)
    delete resolving[parentCode]
    if (parentPath) return `${parentPath}.${ownName}`
  }
  return root ? `${root}.${ownName}` : ownName
}

function flattenObjectFields(objects, prefix) {
  const result = []
  arrayOf(objects).forEach(objectInfo => {
    const fields = arrayOf(objectInfo && objectInfo.fields)
    const fieldsByCode = {}
    fields.forEach(field => {
      const code = text(field && field.varCode).trim()
      const scriptName = text(field && field.scriptName).trim()
      if (code) fieldsByCode[code] = field
      if (scriptName) fieldsByCode[scriptName] = field
    })
    fields.forEach(field => {
      const path = normalizeObjectFieldPath(objectInfo, field, fieldsByCode, {})
      if (path) result.push(toField(`${prefix}.${path}`, field))
    })
  })
  return result
}

function flattenVariables(variables, prefix) {
  return arrayOf(variables).reduce((result, field) => {
    const name = fieldName(field)
    if (name) result.push(toField(`${prefix}.${name}`, field))
    return result
  }, [])
}

function uniqueFields(fields) {
  const paths = new Set()
  return fields.filter(field => {
    if (paths.has(field.path)) return false
    paths.add(field.path)
    return true
  })
}

export function flattenRequestFields(rule) {
  return uniqueFields([
    ...flattenVariables(rule && rule.inputVariables, 'params'),
    ...flattenObjectFields(rule && rule.inputDataObjects, 'params')
  ])
}

export function flattenResponseFields(rule) {
  return uniqueFields([
    ...flattenVariables(rule && rule.outputVariables, 'data.result'),
    ...flattenObjectFields(rule && rule.outputDataObjects, 'data.result')
  ])
}

export function documentedScenarios(rule) {
  return arrayOf(rule && rule.scenarios)
    .filter(item => item && item.scenarioName && item.requestJson && item.responseJson)
    .map(item => ({
      id: item.id,
      scenarioName: text(item.scenarioName),
      description: text(item.description),
      requestJson: text(item.requestJson),
      responseJson: text(item.responseJson),
      outerCode: item.outerCode,
      businessCodePath: text(item.businessCodePath),
      businessCode: text(item.businessCode),
      sortOrder: Number.isFinite(Number(item.sortOrder)) ? Number(item.sortOrder) : 0
    }))
    .sort((left, right) => left.sortOrder - right.sortOrder)
}

function normalizeAuthentication(auth) {
  return {
    authName: text(auth && auth.authName),
    authType: text(auth && auth.authType),
    placement: text(auth && auth.placement),
    parameterName: text(auth && auth.parameterName),
    tokenTtlSeconds: auth && auth.tokenTtlSeconds,
    tokenGraceSeconds: auth && auth.tokenGraceSeconds
  }
}

function normalizeRule(rule) {
  return {
    id: rule && rule.id,
    ruleCode: text(rule && rule.ruleCode),
    ruleName: text(rule && rule.ruleName),
    modelType: text(rule && rule.modelType),
    modelTypeLabel: text(rule && rule.modelTypeLabel),
    description: text(rule && rule.description),
    currentVersion: rule && rule.currentVersion,
    publishedVersion: rule && rule.publishedVersion,
    status: rule && rule.status,
    statusLabel: text(rule && rule.statusLabel),
    requestFields: flattenRequestFields(rule),
    responseFields: flattenResponseFields(rule),
    scenarios: documentedScenarios(rule)
  }
}

export function normalizeApiDoc(doc) {
  const project = doc && doc.project ? doc.project : {}
  return {
    project: {
      id: project.id,
      projectCode: text(project.projectCode),
      projectName: text(project.projectName),
      description: text(project.description),
      status: project.status
    },
    authentications: arrayOf(doc && doc.authentications).map(normalizeAuthentication),
    rules: arrayOf(doc && doc.rules).map(normalizeRule)
  }
}

export function buildExampleBody(fields) {
  const root = {}
  arrayOf(fields).forEach(field => {
    const parts = text(field.path).split('.').filter(Boolean)
    let current = root
    parts.forEach((part, index) => {
      if (index === parts.length - 1) current[part] = field.exampleValue
      else {
        if (!current[part] || typeof current[part] !== 'object') current[part] = {}
        current = current[part]
      }
    })
  })
  return root
}
