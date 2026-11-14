import { parseRestrictedJsonPath } from '../restrictedJsonPath'
import { managedHeaderNames } from './codeSamples'
import { buildExampleBody, typeSchema } from './fieldSchema'

export { buildExampleBody } from './fieldSchema'

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
    managedHeaderNames: managedHeaderNames(auth),
    tokenTtlSeconds: auth && auth.tokenTtlSeconds,
    tokenGraceSeconds: auth && auth.tokenGraceSeconds
  }
}

function publishedFieldsByRef(variables, objects) {
  const byRef = new Map()
  for (const item of arrayOf(variables)) if (item.id != null) byRef.set((item.varSource === 'DATA_OBJECT' ? 'DATA_OBJECT' : 'VARIABLE') + ':' + item.id, item)
  for (const object of arrayOf(objects)) for (const field of arrayOf(object.fields)) if (field.id != null) byRef.set('DATA_OBJECT:' + field.id, field)
  return byRef
}

function openApiRequestFields(contract, inputs) {
  return arrayOf(contract.requestMappings).filter(item => item?.sourceType === 'BODY').flatMap(item => {
    const parts = parseRestrictedJsonPath(item.sourcePath)
    if (!parts) return []
    const type = item.targetType || 'STRING'
    const hasDefault = item.defaultValue != null && String(item.defaultValue).trim() !== ''
    const input = inputs.get(item.targetRefType + ':' + item.targetVarId)
    const value = sampleValue({ varType: type, defaultValue: item.defaultValue, exampleValue: hasDefault ? undefined : input?.exampleValue })
    return [{ path: item.sourcePath, parts, type, required: Boolean(item.required && !hasDefault),
      label: hasDefault ? '省略时使用已配置的默认值' : '请求体中的字段',
      exampleValue: value === '' ? 'example' : value }]
  })
}

function openApiHeaders(contract) {
  const rows = new Map([['x-auth-code', { name: 'X-Auth-Code', required: true, example: '<AUTH_CODE>',
    description: '与本次项目凭据匹配的鉴权配置编码' }]])
  for (const item of arrayOf(contract.requestMappings)) {
    if (item?.sourceType !== 'HEADER' || !item.sourcePath) continue
    const key = item.sourcePath.toLowerCase()
    const required = Boolean(item.required && !item.hasDefault && !text(item.defaultValue).trim())
    const row = rows.get(key)
    if (row) row.required ||= required
    else rows.set(key, { name: item.sourcePath, required, example: '<HEADER_VALUE>', description: '按已发布契约转换为 ' + (item.targetType || 'STRING') })
  }
  return [...rows.values()]
}

function normalizeRule(rule) {
  const openApiContract = rule && rule.openApiContract ? rule.openApiContract : {}
  const openApiEnabled = Boolean(rule && rule.openApiEnabled)
  const requestFields = flattenRequestFields(rule)
  const inputs = publishedFieldsByRef(rule?.inputVariables, rule?.inputDataObjects)
  const outputs = publishedFieldsByRef(rule?.outputVariables, rule?.outputDataObjects)
  const bodyFields = openApiEnabled ? openApiRequestFields(openApiContract, inputs) : []
  return {
    id: rule && rule.id,
    ruleCode: text(rule && rule.ruleCode),
    ruleName: text(rule && rule.ruleName),
    modelType: text(rule && rule.modelType),
    modelTypeLabel: text(rule && rule.modelTypeLabel),
    description: text(rule && rule.description),
    currentVersion: rule && rule.currentVersion,
    publishedVersion: rule && rule.publishedVersion,
    publishedRevisionId: rule && rule.publishedRevisionId,
    publishedArtifactDigest: text(rule && rule.publishedArtifactDigest),
    openApiEnabled,
    mappedResponseFields: arrayOf(openApiContract.responseMappings).map(item => {
      const field = outputs.get(item.sourceRefType + ':' + item.sourceVarId)
      const schema = field?.varType ? typeSchema(field.varType) : {}
      if (schema.type) schema.type = [schema.type, 'null']
      return { name: item.targetField, schema }
    }),
    openApiHeaderFields: openApiEnabled ? openApiHeaders(openApiContract) : [],
    openApiContract,
    schemaTrust: text(rule && rule.schemaTrust),
    schemaDiagnostics: arrayOf(rule && rule.schemaDiagnostics).map(item => text(item)),
    inputSchema: rule && rule.inputSchema ? rule.inputSchema : {},
    outputSchema: rule && rule.outputSchema ? rule.outputSchema : {},
    status: rule && rule.status,
    statusLabel: text(rule && rule.statusLabel),
    requestFields,
    exampleBody: { clientAppName: 'api-doc-example', params: buildExampleBody(requestFields).params || {} },
    openApiRequestFields: bodyFields,
    openApiExample: openApiEnabled ? buildExampleBody(bodyFields) : undefined,
    responseFields: flattenResponseFields(rule),
    scenarios: documentedScenarios(rule)
  }
}

export function normalizeApiDoc(doc) {
  const project = doc && doc.project ? doc.project : {}
  return {
    codeMapping: doc?.codeMapping || { enabled: false, mappings: {} },
    project: {
      id: project.id,
      projectCode: text(project.projectCode),
      projectName: text(project.projectName),
      description: text(project.description),
      status: project.status
    },
    authentications: arrayOf(doc && doc.authentications).map(normalizeAuthentication),
    experiments: arrayOf(doc?.experiments).map(item => {
      const requestFields = flattenRequestFields(item)
      const experimentCode = text(item.experimentCode)
      return {
        id: 'experiment:' + experimentCode, experimentCode, experimentName: text(item.experimentName),
        description: text(item.description), routingMode: text(item.routingMode), routingErrorPolicy: item.routingErrorPolicy || 'FALLBACK',
        requestFields, inputFieldsAvailable: item.inputFieldsAvailable === true,
        exampleBody: { requestKey: '<UNIQUE_ORDER_ID>', clientAppName: 'api-doc-example', params: buildExampleBody(requestFields).params || {} }
      }
    }),
    rules: arrayOf(doc && doc.rules).map(rule => ({ ...normalizeRule(rule), codeMapping: doc?.codeMapping }))
  }
}
