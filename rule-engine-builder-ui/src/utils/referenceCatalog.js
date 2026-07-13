function flattenObjectFields(rows, out = []) {
  const fields = rows || []
  fields.forEach(row => {
    out.push(row)
    if (row && row.children && row.children.length) flattenObjectFields(row.children, out)
  })
  return out
}

function stripPrefix(value, prefix) {
  const text = value || ''
  const fullPrefix = prefix ? prefix + '.' : ''
  return fullPrefix && text.indexOf(fullPrefix) === 0 ? text.substring(fullPrefix.length) : text
}

function refEntry({ id, refType, refCode, label, varType, category, varObj, extra = {} }) {
  const refLabel = { label: label || '', code: refCode || '' }
  return {
    id,
    _varId: id,
    _refType: refType,
    refType,
    refCode,
    refLabel,
    displayName: [refLabel.label, refLabel.code].filter(Boolean).join(' '),
    varType: varType || 'STRING',
    category,
    varObj: Object.assign({ refType }, varObj || {}),
    ...extra
  }
}

export function buildReferenceCatalog(variables = [], objectTree = [], models = []) {
  const refs = []
  const options = {
    variable: [],
    constant: [],
    dataObject: [],
    model: []
  }
  const seenRefs = new Set()
  const addRef = (entry, groupKey) => {
    const key = `${entry.refType || 'REF'}:${entry.id == null ? entry.refCode : entry.id}`
    if (seenRefs.has(key)) return
    seenRefs.add(key)
    refs.push(entry)
    options[groupKey].push(entry)
  }

  ;(variables || []).forEach(variable => {
    const constant = variable.varSource === 'CONSTANT'
    const refType = constant ? 'CONSTANT' : 'VARIABLE'
    const entry = refEntry({
      id: variable.id,
      refType,
      refCode: variable.scriptName || variable.varCode || '',
      label: variable.varLabel || '',
      varType: variable.varType,
      category: constant ? 'constant' : 'standalone',
      varObj: variable,
      extra: { sourceType: constant ? 'constant' : 'variable', varSource: variable.varSource }
    })
    addRef(entry, constant ? 'constant' : 'variable')
  })

  ;(objectTree || []).forEach(group => {
    const object = group.object || group
    const objectCode = object.scriptName || object.objectCode || ''
    const objectLabel = object.objectLabel || object.objectCode || ''
    const fields = group.flatVariables || flattenObjectFields(group.variables)
    ;(fields || []).forEach(field => {
      const fieldPath = stripPrefix(field.scriptName || field.varCode || '', objectCode)
      const refCode = [objectCode, fieldPath].filter(Boolean).join('.')
      const fieldLabel = stripPrefix(field.varLabel || field.varCode || fieldPath, objectCode)
      const entry = refEntry({
        id: field.id,
        refType: 'DATA_OBJECT',
        refCode,
        label: fieldLabel,
        varType: field.varType,
        category: 'object',
        varObj: Object.assign({ objectField: true }, field),
        extra: {
          sourceType: 'dataObject',
          varSource: 'INPUT',
          objectCode,
          objectRawCode: object.objectCode || '',
          objectLabel
        }
      })
      addRef(entry, 'dataObject')
    })
  })

  ;(models || []).forEach(model => {
    const modelCode = model.modelCode || ''
    if (!modelCode) return
    const modelLabel = model.modelName || modelCode
    const inputFields = model.inputFields || []
    ;(model.outputFields || []).forEach(field => {
      const outputCode = field.scriptName || field.fieldName || ''
      if (!outputCode) return
      const entry = refEntry({
        id: field.id,
        refType: 'MODEL_OUTPUT',
        refCode: `${modelCode}.${outputCode}`,
        label: `${modelLabel}/${field.fieldLabel || field.fieldName || outputCode}`,
        varType: field.fieldType,
        category: 'model',
        varObj: Object.assign({}, field, {
          modelId: model.id,
          modelCode,
          modelName: modelLabel,
          modelInputFields: inputFields
        }),
        extra: { sourceType: 'model', modelCode, modelId: model.id, modelLabel, modelInputFields: inputFields }
      })
      addRef(entry, 'model')
    })
  })

  return {
    refs,
    groups: [
      { key: 'variable', label: '普通变量', options: options.variable },
      { key: 'constant', label: '常量', options: options.constant },
      { key: 'dataObject', label: '数据对象字段', options: options.dataObject },
      { key: 'model', label: '模型', options: options.model }
    ]
  }
}

export function buildDetailReferenceState(catalog) {
  const source = catalog || { refs: [], groups: [] }
  const itemByKey = new Map()
  const items = (source.refs || []).map(ref => {
    const item = {
      id: ref.id,
      _varId: ref.id,
      refType: ref.refType,
      _refType: ref.refType,
      varCode: ref.refCode,
      varCodeText: ref.refCode,
      scriptName: ref.refCode,
      varLabel: ref.displayName,
      varLabelText: ref.refLabel && ref.refLabel.label,
      varType: ref.varType,
      varSource: ref.varSource,
      sourceType: ref.sourceType,
      sourceLabel: ref.objectLabel || ref.modelLabel || '',
      sourceCode: ref.objectCode || ref.modelCode || '',
      objectLabel: ref.objectLabel || '',
      objectCode: ref.objectCode || '',
      modelLabel: ref.modelLabel || '',
      modelCode: ref.modelCode || '',
      modelInputFields: ref.modelInputFields || [],
      varObj: ref.varObj
    }
    itemByKey.set(`${ref.refType || 'VARIABLE'}:${ref.id}`, item)
    return item
  })
  const groups = (source.groups || []).map(group => ({
    key: group.key,
    label: group.label,
    options: (group.options || []).map(ref => itemByKey.get(`${ref.refType || 'VARIABLE'}:${ref.id}`)).filter(Boolean)
  }))
  return { items, groups }
}

export function buildDetailReferenceMap(state) {
  const map = {}
  const items = (state && state.items) || []
  items.forEach(item => {
    const refType = item.refType || 'VARIABLE'
    if (item.id != null) {
      map[`${refType}:${item.id}`] = item
      if (!map[String(item.id)]) map[String(item.id)] = item
    }
    const code = item.varCodeText || item.varCode || item.scriptName
    if (code) {
      map[`${refType}:CODE:${code}`] = item
      if (!map[`CODE:${code}`]) map[`CODE:${code}`] = item
    }
  })
  return map
}

export function resolveDetailReference(referenceMap, row) {
  if (!referenceMap || !row) return null
  const refType = row.refType || row._refType || 'VARIABLE'
  const refId = row.varId == null ? row._varId : row.varId
  if (refId != null) {
    const byId = referenceMap[`${refType}:${refId}`] || referenceMap[String(refId)]
    if (byId) return byId
  }
  const code = row.scriptName || row.fieldName || row.varCode
  if (!code) return null
  return referenceMap[`${refType}:CODE:${code}`] || referenceMap[`CODE:${code}`] || null
}
