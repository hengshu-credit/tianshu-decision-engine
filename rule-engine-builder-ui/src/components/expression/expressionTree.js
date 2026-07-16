import {
  cloneOperand,
  createArrayOperand,
  createFunctionOperand,
  createLiteralOperand,
  createOperationOperand,
  inferOperandType
} from '@/utils/operand'

export function getExpressionNode(root, path = []) {
  return (path || []).reduce((node, key) => node == null ? null : node[key], root)
}

export function setExpressionNode(root, path = [], value) {
  if (!(path || []).length) return cloneOperand(value)
  const copy = cloneOperand(root) || {}
  let current = copy
  for (let index = 0; index < path.length - 1; index++) {
    const key = path[index]
    const nextKey = path[index + 1]
    if (current[key] == null) current[key] = typeof nextKey === 'number' ? [] : {}
    current = current[key]
  }
  current[path[path.length - 1]] = cloneOperand(value)
  return copy
}

export function removeExpressionNode(root, path = []) {
  return setExpressionNode(root, path, null)
}

export function expressionChildEntries(node, basePath = []) {
  if (!node || !node.kind) return []
  if (node.kind === 'FUNCTION') return (node.args || []).map((value, index) => ({ label: '参数 ' + (index + 1), path: basePath.concat(['args', index]), value }))
  if (node.kind === 'OPERATION') {
    return (node.terms || []).map((term, index) => ({
      label: '运算项 ' + (index + 1),
      operator: index === 0 ? '' : (term.operator || ''),
      termIndex: index,
      path: basePath.concat(['terms', index, 'operand']),
      value: term.operand
    }))
  }
  if (node.kind === 'ACCESS') return [
    { label: '目标', path: basePath.concat(['target']), value: node.target },
    { label: node.accessType === 'INDEX' ? '下标' : '键', path: basePath.concat(['accessor']), value: node.accessor }
  ]
  if (node.kind === 'CAST') return [{ label: '待转换值', path: basePath.concat(['operand']), value: node.operand }]
  if (node.kind === 'ARRAY') return (node.items || []).map((value, index) => ({ label: '元素 ' + (index + 1), path: basePath.concat(['items', index]), value }))
  return []
}

export function expressionPathKey(path = []) {
  return (path || []).length ? path.join('.') : '$'
}

export function expressionAncestorKeys(path = []) {
  return (path || []).map((unused, index) => expressionPathKey(path.slice(0, index)))
}

export function expressionDescendantCount(node) {
  return expressionChildEntries(node).reduce((total, entry) => total + 1 + expressionDescendantCount(entry.value), 0)
}

export function collapsedExpressionPaths(root, visibleLevels = 2) {
  const result = []
  const collapseDepth = Math.max(visibleLevels - 1, 0)
  const visit = (node, path, depth) => {
    const children = expressionChildEntries(node, path)
    if (!children.length) return
    if (depth >= collapseDepth) {
      result.push(expressionPathKey(path))
      return
    }
    children.forEach(entry => visit(entry.value, entry.path, depth + 1))
  }
  visit(root, [], 0)
  return result
}

export function existingCollapsedPaths(root, collapsedPathKeys = []) {
  const existing = new Set()
  const visit = (node, path) => {
    const children = expressionChildEntries(node, path)
    if (!children.length) return
    existing.add(expressionPathKey(path))
    children.forEach(entry => visit(entry.value, entry.path))
  }
  visit(root, [])
  return (collapsedPathKeys || []).filter(key => existing.has(key))
}

export function firstEditablePath(node, basePath = []) {
  const children = expressionChildEntries(node, basePath)
  if (!children.length) return basePath
  const empty = children.find(entry => !entry.value)
  if (empty) return empty.path
  return children[0].path
}

export function createFunctionTemplate(fn) {
  const definitions = functionParameters(fn)
  const args = definitions.map(functionExampleOperand)
  return createFunctionOperand(fn, args)
}

function functionExampleOperand(param) {
  const example = param && param.example
  const type = String((param && (param.type || param.valueType)) || 'STRING').toUpperCase()
  if (Array.isArray(example)) {
    return createArrayOperand(example.map(value => createLiteralOperand(
      value,
      typeof value === 'number' ? 'NUMBER' : (typeof value === 'boolean' ? 'BOOLEAN' : 'STRING')
    )))
  }
  if (example && typeof example === 'object') {
    return createLiteralOperand(JSON.stringify(example), type === 'OBJECT' ? 'MAP' : type)
  }
  return createLiteralOperand(example == null ? '' : example, type)
}

export function functionParameters(fn) {
  if (!fn) return []
  if (Array.isArray(fn.params)) return fn.params
  if (Array.isArray(fn.parameters)) return fn.parameters
  if (!fn.paramsJson) return []
  try {
    const parsed = typeof fn.paramsJson === 'string' ? JSON.parse(fn.paramsJson) : fn.paramsJson
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

export function insertExpressionOperation(root, selectedPath = [], operator) {
  const path = selectedPath.slice()
  const current = getExpressionNode(root, path)
  if (current && current.kind === 'OPERATION') {
    const next = cloneOperand(current)
    const index = (next.terms || []).length
    next.terms = (next.terms || []).concat([{ operator, operand: null }])
    return {
      root: setExpressionNode(root, path, next),
      selectedPath: path.concat(['terms', index, 'operand'])
    }
  }

  if (path.length >= 3 && path[path.length - 3] === 'terms' && path[path.length - 1] === 'operand') {
    const index = path[path.length - 2]
    const parentPath = path.slice(0, -3)
    const parent = getExpressionNode(root, parentPath)
    if (parent && parent.kind === 'OPERATION' && Number.isInteger(index)) {
      const next = cloneOperand(parent)
      next.terms.splice(index + 1, 0, { operator, operand: null })
      return {
        root: setExpressionNode(root, parentPath, next),
        selectedPath: parentPath.concat(['terms', index + 1, 'operand'])
      }
    }
  }

  const next = createOperationOperand([
    { operand: cloneOperand(current) },
    { operator, operand: null }
  ], inferOperandType(current))
  return {
    root: setExpressionNode(root, path, next),
    selectedPath: path.concat(['terms', 1, 'operand'])
  }
}

export function wrapExpressionNode(current, template) {
  const source = cloneOperand(template)
  if (!current) return source
  if (source.kind === 'OPERATION') {
    source.terms[0].operand = cloneOperand(current)
  } else if (source.kind === 'ACCESS') {
    source.target = cloneOperand(current)
    source.accessor = source.accessor || createLiteralOperand('', source.accessType === 'INDEX' ? 'NUMBER' : 'STRING')
  } else if (source.kind === 'CAST') {
    source.operand = cloneOperand(current)
  }
  return source
}

function operationTermLocation(root, path) {
  if (!Array.isArray(path) || path.length < 3) return null
  const suffix = path.slice(-3)
  if (suffix[0] !== 'terms' || !Number.isInteger(suffix[1]) || suffix[2] !== 'operand') return null
  const parentPath = path.slice(0, -3)
  const parent = getExpressionNode(root, parentPath)
  if (!parent || parent.kind !== 'OPERATION' || !(parent.terms || [])[suffix[1]]) return null
  return { parent, parentPath, index: suffix[1] }
}

function unchanged(root, selectedPath) {
  return { root, selectedPath: (selectedPath || []).slice(), changed: false }
}

export function indentExpressionTerm(root, selectedPath = []) {
  const location = operationTermLocation(root, selectedPath)
  if (!location || location.index < 1) return unchanged(root, selectedPath)
  const terms = cloneOperand(location.parent.terms || [])
  const previousIndex = location.index - 1
  const previous = terms[previousIndex]
  const current = terms[location.index]
  if (!previous || !previous.operand || !current || !current.operand || !current.operator) return unchanged(root, selectedPath)

  const nested = createOperationOperand([
    { operand: previous.operand },
    { operator: current.operator, operand: current.operand }
  ], location.parent.valueType)
  const grouped = { operand: nested }
  if (previousIndex > 0 && previous.operator) grouped.operator = previous.operator
  terms.splice(previousIndex, 2, grouped)
  const nextParent = { ...cloneOperand(location.parent), terms }
  const nextPath = location.parentPath.concat(['terms', previousIndex, 'operand', 'terms', 1, 'operand'])
  return {
    root: setExpressionNode(root, location.parentPath, nextParent),
    selectedPath: nextPath,
    changed: true
  }
}

export function outdentExpressionOperation(root, selectedPath = []) {
  const location = operationTermLocation(root, selectedPath)
  const current = getExpressionNode(root, selectedPath)
  if (!location || !current || current.kind !== 'OPERATION' || (current.terms || []).length < 2) {
    return unchanged(root, selectedPath)
  }

  const parentTerms = cloneOperand(location.parent.terms || [])
  const wrapper = parentTerms[location.index]
  const expanded = cloneOperand(current.terms).map((term, index) => {
    const next = { operand: term.operand }
    if (index === 0) {
      if (location.index > 0 && wrapper.operator) next.operator = wrapper.operator
    } else if (term.operator) {
      next.operator = term.operator
    }
    return next
  })
  parentTerms.splice(location.index, 1, ...expanded)
  const nextParent = { ...cloneOperand(location.parent), terms: parentTerms }
  const nextPath = location.parentPath.concat(['terms', location.index, 'operand'])
  return {
    root: setExpressionNode(root, location.parentPath, nextParent),
    selectedPath: nextPath,
    changed: true
  }
}

export function moveExpressionSibling(root, selectedPath = [], offset = 0) {
  const location = operationTermLocation(root, selectedPath)
  if (location) {
    const targetIndex = location.index + Number(offset)
    if (!Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= location.parent.terms.length || targetIndex === location.index) {
      return unchanged(root, selectedPath)
    }
    const terms = cloneOperand(location.parent.terms)
    const sourceOperand = terms[location.index].operand
    terms[location.index].operand = terms[targetIndex].operand
    terms[targetIndex].operand = sourceOperand
    return {
      root: setExpressionNode(root, location.parentPath, { ...cloneOperand(location.parent), terms }),
      selectedPath: location.parentPath.concat(['terms', targetIndex, 'operand']),
      changed: true
    }
  }

  if (!Array.isArray(selectedPath) || selectedPath.length < 2) return unchanged(root, selectedPath)
  const index = selectedPath[selectedPath.length - 1]
  const key = selectedPath[selectedPath.length - 2]
  if (!Number.isInteger(index) || !['args', 'items'].includes(key)) return unchanged(root, selectedPath)
  const parentPath = selectedPath.slice(0, -2)
  const parent = getExpressionNode(root, parentPath)
  const values = parent && parent[key]
  const targetIndex = index + Number(offset)
  if (!Array.isArray(values) || !Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= values.length || targetIndex === index) {
    return unchanged(root, selectedPath)
  }
  const nextValues = cloneOperand(values)
  const source = nextValues[index]
  nextValues[index] = nextValues[targetIndex]
  nextValues[targetIndex] = source
  return {
    root: setExpressionNode(root, parentPath, { ...cloneOperand(parent), [key]: nextValues }),
    selectedPath: parentPath.concat([key, targetIndex]),
    changed: true
  }
}

function pathStartsWith(path, prefix) {
  return prefix.length <= path.length && prefix.every((value, index) => value === path[index])
}

function isOperandSlot(root, path) {
  if (!Array.isArray(path) || !path.length) return false
  const operation = operationTermLocation(root, path)
  if (operation) return true

  const key = path[path.length - 1]
  const parent = getExpressionNode(root, path.slice(0, -1))
  if (key === 'target' || key === 'accessor') return parent && parent.kind === 'ACCESS'
  if (key === 'operand') return parent && parent.kind === 'CAST'

  if (path.length < 2 || !Number.isInteger(key)) return false
  const collectionKey = path[path.length - 2]
  const collectionParent = getExpressionNode(root, path.slice(0, -2))
  if (collectionKey === 'args') return collectionParent && collectionParent.kind === 'FUNCTION' && key < (collectionParent.args || []).length
  if (collectionKey === 'items') return collectionParent && collectionParent.kind === 'ARRAY' && key < (collectionParent.items || []).length
  return false
}

export function moveExpressionNode(root, fromPath = [], toPath = []) {
  if (!fromPath.length || !toPath.length || pathsEqual(fromPath, toPath)) return unchanged(root, fromPath)
  if (pathStartsWith(toPath, fromPath) || !isOperandSlot(root, toPath)) return unchanged(root, fromPath)
  const source = getExpressionNode(root, fromPath)
  const target = getExpressionNode(root, toPath)
  if (!source || target != null) return unchanged(root, fromPath)

  const withTarget = setExpressionNode(root, toPath, source)
  return {
    root: setExpressionNode(withTarget, fromPath, null),
    selectedPath: toPath.slice(),
    changed: true
  }
}

export function pathsEqual(left, right) {
  return JSON.stringify(left || []) === JSON.stringify(right || [])
}
