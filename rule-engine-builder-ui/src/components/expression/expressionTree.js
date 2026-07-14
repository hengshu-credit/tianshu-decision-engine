import {
  cloneOperand,
  createArrayOperand,
  createFunctionOperand,
  createLiteralOperand
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
  if (node.kind === 'OPERATION') return (node.operands || []).map((value, index) => ({ label: '运算项 ' + (index + 1), path: basePath.concat(['operands', index]), value }))
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

export function collapsedExpressionPaths(root, maxDepth = 2) {
  const result = []
  const visit = (node, path, depth) => {
    const children = expressionChildEntries(node, path)
    if (!children.length) return
    if (depth >= maxDepth) {
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

export function wrapExpressionNode(current, template) {
  const source = cloneOperand(template)
  if (!current) return source
  if (source.kind === 'OPERATION') {
    source.operands = [cloneOperand(current), null]
  } else if (source.kind === 'ACCESS') {
    source.target = cloneOperand(current)
    source.accessor = source.accessor || createLiteralOperand('', source.accessType === 'INDEX' ? 'NUMBER' : 'STRING')
  } else if (source.kind === 'CAST') {
    source.operand = cloneOperand(current)
  }
  return source
}

export function pathsEqual(left, right) {
  return JSON.stringify(left || []) === JSON.stringify(right || [])
}
