import catalog from '@/constants/reserved-codes.json'

const keywords = new Set(catalog.keywords)
const engineCodes = new Set([...catalog.engineCodes, ...catalog.managedFunctions].map(code => code.toLowerCase()))
const generated = new RegExp('^(?:' + catalog.engineCodePatterns.join('|') + ')$')
const managedFunctions = new Set(catalog.managedFunctions)

export function isManagedFunction(code) {
  return managedFunctions.has(code)
}

export function reservedCodeError(value) {
  if (typeof value !== 'string') return ''
  const code = value.trim()
  const key = code.toLowerCase()
  if (keywords.has(key)) return `“${code}”是语言关键字，请更换编码`
  if (engineCodes.has(key) || generated.test(key)) return `“${code}”是引擎内置编码，请更换编码`
  return ''
}

export const reservedCodeRule = {
  trigger: ['blur', 'change'],
  validator(_rule, value, callback) {
    const error = reservedCodeError(value)
    callback(error ? new Error(error) : undefined)
  },
}

function readConfig(value) {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) } catch { return null }
}

// Version editing exposes the full snapshot. Check declarations, including nested rows,
// without treating references, sample JSON, external column names or expressions as codes.
export function resourceCodeIssue(type, snapshot, original = {}) {
  const identity = catalog.resourceFields[type]
  if (!identity || !snapshot) return null
  const ownFunction = type === 'FUNCTION' && isManagedFunction(original?.funcCode)
    && original.funcCode === snapshot.funcCode && original.scope === 'GLOBAL' && snapshot.scope === 'GLOBAL'
  function check(field, value, path = field) {
    const message = reservedCodeError(value)
    return message ? { field, message: `${path}：${message}` } : null
  }
  const root = !ownFunction && check(identity, snapshot[identity])
  if (root) return root
  if (['VARIABLE', 'DATA_OBJECT'].includes(type)) {
    const script = check('scriptName', snapshot.scriptName)
    if (script) return script
  }
  function rows(items, path, fields) {
    if (!Array.isArray(items)) return null
    for (const [index, item] of items.entries()) {
      for (const key of fields) {
        const issue = check(path, item?.[key], `${path}[${index}].${key}`)
        if (issue) return issue
      }
    }
    return null
  }
  if (type === 'FUNCTION') {
    const params = readConfig(snapshot.paramsJson)
    const originals = ownFunction ? readConfig(original.paramsJson) : []
    const existing = new Set(Array.isArray(originals) ? originals.map(param => param?.name) : [])
    if (Array.isArray(params)) return rows(params.map(param => existing.has(param?.name) ? null : param), 'paramsJson', ['name'])
  }
  if (type === 'DATA_OBJECT') return rows(snapshot.fields, 'fields', ['varCode', 'scriptName'])
  if (type === 'EXPERIMENT') return rows(snapshot.groups, 'groups', ['groupCode'])
  if (type === 'RULE') {
    const model = readConfig(snapshot.content?.modelJson)
    if (snapshot.modelType === 'RULE_SET') return rows(model?.rules, 'rules', ['ruleCode'])
    if (['TREE', 'FLOW'].includes(snapshot.modelType) && Array.isArray(model?.logicflow?.nodes)) {
      return rows(model.logicflow.nodes.map(node => node?.properties), 'logicflow.nodes', ['nodeCode'])
    }
  }
  return null
}
