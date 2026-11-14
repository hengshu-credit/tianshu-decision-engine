/** 与服务端一致，属性名原样保留，数组下标返回数字，根节点返回空数组。 */
export function parseRestrictedJsonPath(path) {
  if (typeof path !== 'string' || !path.startsWith('$') || path.length > 512) return null
  const parts = []
  let remaining = path.slice(1)
  while (remaining) {
    const part = /^(?:\.([\p{L}\p{N}_-]+)|\[(\d+)\]|\[("(?:[^"\\]|\\.)*")\])/u.exec(remaining)
    if (!part) return null
    if (part[2] !== undefined && Number(part[2]) > 2147483647) return null
    if (part[3]) {
      try { parts.push(JSON.parse(part[3])) } catch { return null }
    } else {
      parts.push(part[1] ?? Number(part[2]))
    }
    remaining = remaining.slice(part[0].length)
  }
  return parts
}

export function isRestrictedJsonPath(path) {
  return parseRestrictedJsonPath(path) !== null
}
