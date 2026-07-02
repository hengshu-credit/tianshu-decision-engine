export function sampleValueForRef(ref) {
  const variable = ref && ref.varObj ? ref.varObj : {}
  if (variable.defaultValue !== undefined && variable.defaultValue !== null && variable.defaultValue !== '') {
    return variable.defaultValue
  }
  const type = (ref && ref.varType) || variable.varType || 'STRING'
  if (type === 'NUMBER') return 0
  if (type === 'BOOLEAN') return false
  return ''
}

export function buildSampleParamsFromCodes(codes, projectRefs) {
  const params = {}
  const list = codes || []
  list.forEach(code => {
    if (!code || params[code] !== undefined) return
    const ref = findRefByCode(projectRefs, code)
    params[code] = sampleValueForRef(ref)
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
