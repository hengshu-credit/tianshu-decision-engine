import { collectReferencePaths, sampleValueForVarType } from '@/utils/testParamTemplate'

export function sampleValueForRef(ref) {
  const variable = ref && ref.varObj ? ref.varObj : {}
  if (variable.defaultValue !== undefined && variable.defaultValue !== null && variable.defaultValue !== '') {
    return variable.defaultValue
  }
  if (variable.exampleValue !== undefined && variable.exampleValue !== null && variable.exampleValue !== '') {
    return variable.exampleValue
  }
  const type = (ref && ref.varType) || variable.varType || 'STRING'
  return sampleValueForVarType(type)
}

export function coerceSampleValue(value, ref) {
  if (value === undefined || value === null) {
    return sampleValueForRef(ref)
  }
  let raw = String(value).trim()
  if ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'"))) {
    raw = raw.slice(1, -1)
  }
  if (raw === 'true') return true
  if (raw === 'false') return false
  const variable = ref && ref.varObj ? ref.varObj : {}
  const type = (ref && ref.varType) || variable.varType || 'STRING'
  if (type === 'NUMBER') {
    const n = Number(raw)
    if (!Number.isNaN(n)) return n
  }
  if (type === 'BOOLEAN') {
    return raw === '1' || raw.toLowerCase() === 'true'
  }
  return raw
}

export function buildSampleParamsFromCodes(codes, projectRefs) {
  const params = {}
  const list = codes || []
  const visited = new Set()
  list.forEach(code => {
    if (!code) return
    const ref = findRefByCode(projectRefs, code)
    if (appendExpandedSample(params, ref, code, projectRefs, visited)) return
    if (pathValueExists(params, code)) return
    setParamPath(params, code, sampleValueForRef(ref))
  })
  return params
}

export function collectKnownCodesFromText(text, projectRefs, out, options) {
  const source = text == null ? '' : String(text)
  if (!source) return out || new Set()
  const s = out || new Set()
  const refs = projectRefs || []
  const skipAssignmentLeft = options && options.skipAssignmentLeft
  refs.forEach(ref => {
    const code = ref && ref.refCode
    if (!code) return
    const regex = new RegExp('\\b' + escapeRegex(code) + '\\b', 'g')
    let match
    while ((match = regex.exec(source)) !== null) {
      if (skipAssignmentLeft && isAssignmentLeft(source, match.index + code.length)) {
        continue
      }
      s.add(code)
      break
    }
  })
  return s
}

export function collectActionDataInputCodes(actionData, projectRefs, out) {
  const s = out || new Set()
  if (!actionData) return s
  if (Array.isArray(actionData)) {
    actionData.forEach(item => collectActionDataInputCodes(item, projectRefs, s))
    return s
  }
  if (typeof actionData !== 'object') return s
  const block = actionData
  switch (block.type) {
    case 'assign':
      collectKnownCodesFromText(block.value, projectRefs, s)
      break
    case 'if-block':
      var branches = block.branches || []
      branches.forEach(branch => {
        addCode(s, branch.condVar)
        collectKnownCodesFromText(branch.condValue, projectRefs, s)
        collectActionDataInputCodes(branch.actions, projectRefs, s)
      })
      break
    case 'switch-block':
      addCode(s, block.matchVar)
      var cases = block.cases || []
      cases.forEach(item => collectActionDataInputCodes(item.actions, projectRefs, s))
      collectActionDataInputCodes(block.defaultActions, projectRefs, s)
      break
    case 'func-call':
      var args = block.args || []
      args.forEach(arg => collectKnownCodesFromText(arg, projectRefs, s))
      break
    case 'foreach':
      collectKnownCodesFromText(block.listExpr, projectRefs, s)
      collectActionDataInputCodes(block.actions, projectRefs, s)
      break
    case 'ternary':
      addCode(s, block.condVar)
      collectKnownCodesFromText(block.condValue, projectRefs, s)
      collectKnownCodesFromText(block.trueValue, projectRefs, s)
      collectKnownCodesFromText(block.falseValue, projectRefs, s)
      break
    case 'in-check':
      addCode(s, block.checkVar)
      var inValues = block.inValues || []
      inValues.forEach(value => collectKnownCodesFromText(value, projectRefs, s))
      collectKnownCodesFromText(block.trueValue, projectRefs, s)
      collectKnownCodesFromText(block.falseValue, projectRefs, s)
      break
    case 'template-str':
      var parts = block.parts || []
      parts.forEach(part => {
        if (part && part.type === 'expr') collectKnownCodesFromText(part.content, projectRefs, s)
      })
      break
    default:
      break
  }
  return s
}

export function collectScriptInputCodes(script, projectRefs, out) {
  return collectKnownCodesFromText(script, projectRefs, out, { skipAssignmentLeft: true })
}

export function applyConditionExpressionSamples(params, expression, projectRefs) {
  if (!params || !expression) return params
  const refs = projectRefs || []
  refs.forEach(ref => {
    const code = ref && ref.refCode
    if (!code) return
    const regex = new RegExp('\\b' + escapeRegex(code) + "\\b\\s*(==|>=|<=|>|<)\\s*(\"[^\"]*\"|'[^']*'|true|false|-?\\d+(?:\\.\\d+)?)")
    const match = regex.exec(String(expression))
    if (!match) return
    params[code] = sampleValueForOperator(match[2], match[1], ref)
  })
  return params
}

export function applyActionDataSampleValues(params, actionData, projectRefs) {
  if (!params || !actionData) return params
  if (Array.isArray(actionData)) {
    actionData.forEach(item => applyActionDataSampleValues(params, item, projectRefs))
    return params
  }
  if (typeof actionData !== 'object') return params
  if (actionData.type === 'if-block') {
    const branch = (actionData.branches || []).find(item => item && item.condVar && item.condValue !== undefined)
    if (branch) {
      const ref = findRefByCode(projectRefs, branch.condVar)
      params[branch.condVar] = coerceSampleValue(branch.condValue, ref)
      applyActionDataSampleValues(params, branch.actions, projectRefs)
    }
  } else if (actionData.type === 'switch-block') {
    const firstCase = (actionData.cases || []).find(item => item && item.caseValue !== undefined)
    if (actionData.matchVar && firstCase) {
      const ref = findRefByCode(projectRefs, actionData.matchVar)
      params[actionData.matchVar] = coerceSampleValue(firstCase.caseValue, ref)
      applyActionDataSampleValues(params, firstCase.actions, projectRefs)
    }
  } else if (actionData.type === 'ternary' && actionData.condVar && actionData.condValue !== undefined) {
    const ref = findRefByCode(projectRefs, actionData.condVar)
    params[actionData.condVar] = coerceSampleValue(actionData.condValue, ref)
  } else if (actionData.type === 'in-check' && actionData.checkVar) {
    const first = (actionData.inValues || [])[0]
    if (first !== undefined) {
      const ref = findRefByCode(projectRefs, actionData.checkVar)
      params[actionData.checkVar] = coerceSampleValue(first, ref)
    }
  }
  return params
}

export function addCode(out, code) {
  if (code) out.add(code)
}

export function refCodeById(projectRefs, varId, refType) {
  if (varId == null || varId === '') return ''
  const ref = (projectRefs || []).find(item => {
    const id = item && item.varObj ? item.varObj.id : null
    return String(id) === String(varId) && (!refType || !item.refType || item.refType === refType)
  })
  return ref ? ref.refCode : ''
}

function findRefByCode(projectRefs, code) {
  return (projectRefs || []).find(ref => ref && ref.refCode === code) || null
}

function appendExpandedSample(params, ref, code, projectRefs, visited) {
  if (!ref) return false
  if (ref.refType === 'MODEL_OUTPUT') return appendModelInputSamples(params, ref, projectRefs, visited)
  if (isLeafRef(ref)) {
    setParamPath(params, code || ref.refCode, sampleValueForRef(ref))
    return true
  }
  const paths = dependencyPathsForRef(ref, projectRefs)
  if (!paths.length) {
    if (isComputedRef(ref)) {
      setParamPath(params, code || ref.refCode, sampleValueForRef(ref))
      return true
    }
    return false
  }
  const key = refKey(ref)
  if (visited.has(key)) return true
  visited.add(key)
  paths.forEach(path => appendDependencyPath(params, path, projectRefs, visited))
  visited.delete(key)
  return true
}

function appendDependencyPath(params, path, projectRefs, visited) {
  if (!path) return
  const ref = findRefByCode(projectRefs, path)
  if (ref) {
    appendExpandedSample(params, ref, path, projectRefs, visited)
    return
  }
  if (!pathValueExists(params, path)) setParamPath(params, path, '')
}

function isLeafRef(ref) {
  const variable = ref && ref.varObj ? ref.varObj : {}
  const source = variable.varSource || ref.varSource
  return ref.refType === 'DATA_OBJECT' || source === 'INPUT' || (!source && ref.refType !== 'MODEL_OUTPUT')
}

function isComputedRef(ref) {
  const variable = ref && ref.varObj ? ref.varObj : {}
  return (variable.varSource || ref.varSource) === 'COMPUTED'
}

function dependencyPathsForRef(ref, projectRefs) {
  const variable = ref && ref.varObj ? ref.varObj : {}
  const source = variable.varSource || ref.varSource
  const config = parseSourceConfig(variable.sourceConfig || ref.sourceConfig)
  const paths = []
  const seen = new Set()
  const addPaths = value => {
    collectReferencePaths(value, { allowBarePath: true }).forEach(path => addPath(paths, seen, path))
  }

  if (source === 'API') {
    addPaths(config.paramMapping)
    if (!paths.length) {
      addPaths(config.headerConfig)
      addPaths(config.queryConfig)
      addPaths(config.requestMapping)
      addPaths(config.bodyTemplate)
      addPaths(config.authApiConfig)
    }
  } else if (source === 'DB') {
    addPaths(config.params)
  } else if (source === 'LIST') {
    addPaths(config.queryField || config.queryPath || config.field)
  } else if (source === 'COMPUTED') {
    const codes = collectKnownCodesFromText(config.expression || config.script || config.formula, projectRefs, new Set(), { skipAssignmentLeft: true })
    Array.from(codes).forEach(path => addPath(paths, seen, path))
  }
  return paths
}

function appendModelInputSamples(params, ref, projectRefs, visited) {
  if (!ref || ref.refType !== 'MODEL_OUTPUT') return false
  const fields = ref.modelInputFields || ref.inputFields || (ref.varObj && (ref.varObj.modelInputFields || ref.varObj.inputFields)) || []
  if (!fields.length) return false
  let expanded = false
  fields.forEach(field => {
    if (!field || field.status === 0) return
    const code = field.scriptName || field.fieldName
    const depRef = findRefByCode(projectRefs, code)
    if (!depRef) return
    appendExpandedSample(params, depRef, code, projectRefs, visited)
    expanded = true
  })
  if (expanded) return true
  const modelCode = ref.modelCode || (ref.varObj && ref.varObj.modelCode)
  const target = modelCode ? ensureObject(params, `${modelCode}_fields`) : params
  fields.forEach(field => {
    if (!field || field.status === 0) return
    const code = field.scriptName || field.fieldName
    if (!code) return
    setParamPath(target, code, sampleValueForModelField(field))
  })
  return true
}

function parseSourceConfig(value) {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch (e) {
    return {}
  }
}

function addPath(paths, seen, path) {
  if (!path || seen.has(path)) return
  seen.add(path)
  paths.push(path)
}

function refKey(ref) {
  const id = ref && ref.varObj ? ref.varObj.id : ''
  return (ref && ref.refType ? ref.refType : 'REF') + ':' + (id || (ref && ref.refCode) || '')
}

function sampleValueForModelField(field) {
  if (field.defaultValue !== undefined && field.defaultValue !== null && field.defaultValue !== '') {
    return field.defaultValue
  }
  const type = field.fieldType || field.varType || 'STRING'
  if (['NUMBER', 'INTEGER', 'INT', 'LONG', 'DECIMAL', 'DOUBLE', 'FLOAT'].indexOf(String(type).toUpperCase()) >= 0) return 0
  if (['BOOLEAN', 'BOOL'].indexOf(String(type).toUpperCase()) >= 0) return false
  return ''
}

function ensureObject(params, key) {
  if (!params[key] || typeof params[key] !== 'object' || Array.isArray(params[key])) {
    params[key] = {}
  }
  return params[key]
}

function setParamPath(target, path, value) {
  const parts = String(path).split('.').map(item => item.trim()).filter(Boolean)
  if (!parts.length) return
  let current = target
  parts.forEach((part, index) => {
    if (index === parts.length - 1) {
      current[part] = value
      return
    }
    if (!current[part] || typeof current[part] !== 'object' || Array.isArray(current[part])) {
      current[part] = {}
    }
    current = current[part]
  })
}

function pathValueExists(target, path) {
  const parts = String(path).split('.').map(item => item.trim()).filter(Boolean)
  if (!parts.length) return false
  let current = target
  for (let i = 0; i < parts.length; i++) {
    if (!current || typeof current !== 'object' || !(parts[i] in current)) return false
    current = current[parts[i]]
  }
  return true
}

function sampleValueForOperator(value, operator, ref) {
  const sample = coerceSampleValue(value, ref)
  if (typeof sample !== 'number') return sample
  if (operator === '>') return sample + 1
  if (operator === '<') return sample - 1
  return sample
}

function escapeRegex(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function isAssignmentLeft(source, endIndex) {
  let i = endIndex
  while (i < source.length && /\s/.test(source.charAt(i))) i++
  if (source.charAt(i) !== '=') return false
  const next = source.charAt(i + 1)
  return next !== '=' && next !== '>' && next !== '<'
}
