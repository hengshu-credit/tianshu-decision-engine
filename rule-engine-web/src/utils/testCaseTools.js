import { exactNumberText, isExactNumber, parseExactJson, parseExecutionSnapshotJson } from '@/utils/exactJson'

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) && !isExactNumber(value)
}

function parseObject(value) {
  if (isObject(value)) return value
  if (typeof value !== 'string' || !value.trim()) return {}
  try {
    var parsed = parseExactJson(value)
    return isObject(parsed) ? parsed : {}
  } catch (e) {
    return {}
  }
}

export function scenarioParams(requestJson) {
  var request = parseObject(requestJson)
  return isObject(request.params) ? request.params : {}
}

function comparableResult(response) {
  var root = typeof response === 'string' ? parseExecutionSnapshotJson(response) : parseObject(response)
  var payload = isObject(root.data) ? root.data : root
  var result
  ;['result', 'outputs', 'value', 'resolvedValue'].some(function (key) {
    if (!Object.prototype.hasOwnProperty.call(payload, key)) return false
    result = payload[key]
    return true
  })
  return {
    success: payload.success,
    result: result,
    errorMessage: payload.errorMessage || payload.error || ''
  }
}

function appendDiffs(expected, actual, path, diffs) {
  const expectedNumber = typeof expected === 'number' || isExactNumber(expected)
  const actualNumber = typeof actual === 'number' || isExactNumber(actual)
  if (expectedNumber && actualNumber) {
    if (canonicalNumber(expected) !== canonicalNumber(actual)) diffs.push({ path, expected: isExactNumber(expected) ? exactNumberText(expected) : expected, actual: isExactNumber(actual) ? exactNumberText(actual) : actual })
    return
  }
  if (Array.isArray(expected) || Array.isArray(actual)) {
    if (!Array.isArray(expected) || !Array.isArray(actual)) diffs.push({ path, expected, actual })
    else for (let index = 0; index < Math.max(expected.length, actual.length); index++) appendDiffs(expected[index], actual[index], `${path}[${index}]`, diffs)
    return
  }
  if (isObject(expected) && isObject(actual)) {
    var keys = Array.from(new Set(Object.keys(expected).concat(Object.keys(actual)))).sort()
    keys.forEach(function (key) {
      appendDiffs(expected[key], actual[key], path + '.' + key, diffs)
    })
    return
  }
  if (expected !== actual) diffs.push({ path: path, expected: expected, actual: actual })
}

// 旧Diff调用方只需数值等价，不展开指数；正式用例校验统一由服务端完成。
function canonicalNumber(value) {
  const token = exactNumberText(value)
  const parts = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(token)
  if (!parts) throw new TypeError('数字无法精确比较')
  let digits = (parts[2] + (parts[3] || '')).replace(/^0+/, '')
  if (!digits) return '0'
  const trimmed = digits.replace(/0+$/, '')
  const exponent = BigInt(parts[4] || 0) - BigInt((parts[3] || '').length) + BigInt(digits.length - trimmed.length)
  digits = trimmed
  return `${parts[1]}${digits}e${exponent}`
}

export function diffTestResults(expected, actual) {
  var diffs = []
  appendDiffs(comparableResult(expected), comparableResult(actual), '$', diffs)
  return diffs
}

function ownText(node) {
  if (!isObject(node)) return ''
  var copy = {}
  Object.keys(node).forEach(function (key) {
    if (key !== 'children' && key !== 'events') copy[key] = node[key]
  })
  try { return JSON.stringify(copy).toLowerCase() } catch (e) { return '' }
}

function matchesTrace(node, filters) {
  var status = filters.status || 'ALL'
  var keyword = (filters.keyword || '').trim().toLowerCase()
  var statusMatched = status === 'ALL' || String(node.status || '').toUpperCase() === status
  if (status === 'HIT') statusMatched = node.hit === true || node.matched === true || node.result === true
  if (status === 'MISS') statusMatched = node.hit === false || node.matched === false || node.result === false
  var keywordMatched = !keyword || ownText(node).indexOf(keyword) >= 0
  return statusMatched && keywordMatched
}

function filterNode(node, filters) {
  if (!isObject(node)) return null
  var clone = Object.assign({}, node)
  var childMatched = false
  ;['children', 'events'].forEach(function (key) {
    if (!Array.isArray(node[key])) return
    clone[key] = node[key].map(function (child) { return filterNode(child, filters) }).filter(Boolean)
    if (clone[key].length) childMatched = true
  })
  return matchesTrace(node, filters) || childMatched ? clone : null
}

export function filterTraceTree(trace, filters) {
  var effectiveFilters = filters || {}
  if (Array.isArray(trace)) return trace.map(function (node) { return filterNode(node, effectiveFilters) }).filter(Boolean)
  return filterNode(trace, effectiveFilters)
}
