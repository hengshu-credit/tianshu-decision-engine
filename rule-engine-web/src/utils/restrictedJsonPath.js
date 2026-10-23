/** 开放 API 只允许确定性属性与数组下标，带特殊字符的键使用 JSON 双引号。 */
export function isRestrictedJsonPath(path) {
  if (typeof path !== 'string' || !path.startsWith('$') || path.length > 512) return false
  let remaining = path.slice(1)
  while (remaining) {
    const part = /^(?:\.[\p{L}\p{N}_-]+|\[(\d+)\]|\[("(?:[^"\\]|\\.)*")\])/u.exec(remaining)
    if (!part) return false
    if (part[1] && Number(part[1]) > 2147483647) return false
    if (part[2]) { try { JSON.parse(part[2]) } catch { return false } }
    remaining = remaining.slice(part[0].length)
  }
  return true
}
