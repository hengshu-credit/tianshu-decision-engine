function isEqual(a, b) {
  if (a === b) return true
  if (!a || !b || typeof a !== 'object' || typeof b !== 'object' || Array.isArray(a) !== Array.isArray(b)) return false
  const keys = Object.keys(a)
  return keys.length === Object.keys(b).length && keys.every(key => Object.hasOwn(b, key) && isEqual(a[key], b[key]))
}

export function compareResourceCase(result, checks) {
  if (!Array.isArray(checks) || !checks.length || result?.success === false) return false
  return checks.every(({ path, op, value }) => {
    let actual = result
    for (const key of path) {
      if (actual == null || !Object.hasOwn(actual, key)) return false
      actual = actual[key]
    }
    if (op === 'eq') return isEqual(actual, value)
    if (op === 'between') return typeof actual === 'number' && actual >= value[0] && actual <= value[1]
    if (op === 'reportPath') return typeof actual === 'string' && /^\/reports\/[a-f0-9]{32}\.json$/.test(actual)
    return false
  })
}
