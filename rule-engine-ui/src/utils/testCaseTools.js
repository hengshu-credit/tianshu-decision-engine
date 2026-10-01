function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function parseObject(value) {
  if (isObject(value)) return value
  if (typeof value !== 'string' || !value.trim()) return {}
  try {
    var parsed = JSON.parse(value)
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
  var root = parseObject(response)
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
  if (Array.isArray(expected) || Array.isArray(actual)) {
    if (JSON.stringify(expected) !== JSON.stringify(actual)) diffs.push({ path: path, expected: expected, actual: actual })
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
