function parseReferencePath(path) {
  if (typeof path !== 'string' || path.charAt(0) !== '$') return null
  var tokens = []
  var index = 1
  while (index < path.length) {
    if (path.charAt(index) === '.') {
      var property = path.slice(index + 1).match(/^[A-Za-z_$][\w$]*/)
      if (!property) return null
      tokens.push(property[0])
      index += property[0].length + 1
    } else if (path.charAt(index) === '[') {
      var item = path.slice(index).match(/^\[(\d+)\]/)
      if (!item) return null
      tokens.push(Number(item[1]))
      index += item[0].length
    } else {
      return null
    }
  }
  return tokens
}

function valueAtPath(root, tokens) {
  var current = root
  for (var i = 0; i < tokens.length; i++) {
    if (current === null || current === undefined) return undefined
    current = current[tokens[i]]
  }
  return current
}

function cloneResolved(value, root, resolving) {
  if (value === null || typeof value !== 'object') return value
  if (!Array.isArray(value) && typeof value.$ref === 'string') {
    var path = value.$ref
    var tokens = parseReferencePath(path)
    if (!tokens || resolving[path]) return undefined
    var target = valueAtPath(root, tokens)
    if (target === undefined) return undefined
    resolving[path] = true
    var result = cloneResolved(target, root, resolving)
    delete resolving[path]
    return result
  }
  if (Array.isArray(value)) {
    return value.map(function (item) {
      var result = cloneResolved(item, root, resolving)
      return result === undefined ? null : result
    })
  }
  var output = {}
  Object.keys(value).forEach(function (key) {
    var result = cloneResolved(value[key], root, resolving)
    if (result !== undefined) output[key] = result
  })
  return output
}

export function resolveTraceReferences(root) {
  return cloneResolved(root, root, {})
}
