function hasOwn(target, key) {
  return target != null && Object.prototype.hasOwnProperty.call(target, key)
}

export function normalizeTestResult(response) {
  const payload = response && hasOwn(response, 'data') ? response.data : (response || {})
  let output
  let hasOutput = false
  ;['result', 'outputs', 'value', 'resolvedValue'].some(key => {
    if (!hasOwn(payload, key)) return false
    output = payload[key]
    hasOutput = true
    return true
  })
  return {
    ...payload,
    hasOutput,
    output,
    errorMessage: payload.errorMessage || payload.error || ''
  }
}

export function formatTestOutput(value) {
  if (value === null) return 'null'
  if (value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}
