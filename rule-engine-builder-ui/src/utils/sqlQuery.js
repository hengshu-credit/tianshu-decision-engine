// 与服务端 SqlQuerySupport 共享测试语料；只识别单条只读 SELECT/WITH，不扩大 SQL 执行权限。
export function analyzeSqlQuery(sql) {
  const text = String(sql || '')
  let code = ''
  let placeholderCount = 0
  let invalid = false
  let terminator = -1
  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    const next = text[i + 1]
    if ((char === '-' && next === '-') || char === '#') {
      while (i < text.length && text[i] !== '\n' && text[i] !== '\r') i++
      code += ' '
    } else if (char === '/' && next === '*') {
      if (text[i + 2] === '!' || /^M!/i.test(text.slice(i + 2, i + 4))) invalid = true
      const end = text.indexOf('*/', i + 2)
      if (end < 0) { invalid = true; break }
      // 嵌套注释在数据库间语义不同，要求改为普通注释。
      if (text.slice(i + 2, end).includes('/*')) invalid = true
      i = end + 1
      code += ' '
    } else {
      const dollar = char === '$' && text.slice(i).match(/^\$(?:[A-Za-z_][A-Za-z0-9_]*)?\$/)
      const oracle = (char === 'q' || char === 'Q') && next === "'" && text[i + 2]
      if (dollar || oracle) {
        const opener = text[i + 2]
        const ending = dollar ? dollar[0] : ({ '[': ']', '(': ')', '{': '}', '<': '>' }[opener] || opener) + "'"
        const start = i + (dollar ? dollar[0].length : 3)
        const end = text.indexOf(ending, start)
        if (end < 0) { invalid = true; break }
        i = end + ending.length - 1
        code += ' literal '
      } else if (["'", '"', '`', '['].includes(char)) {
        const ending = char === '[' ? ']' : char
        let closed = false
        for (i++; i < text.length; i++) {
          if (text[i] === ending && text[i + 1] === ending) { i++; continue }
          if (text[i] === '\\' && char !== '[') {
            if (text[i + 1] === ending || text[i + 1] === '\\') invalid = true
            i++
          } else if (text[i] === ending) { closed = true; break }
        }
        if (!closed) invalid = true
        code += ' literal '
      } else {
        code += char
        if (char === '?') placeholderCount++
        if (char === ';') terminator = i
      }
    }
  }
  return { code: code.trim(), placeholderCount, invalid, terminator }
}

export function validateReadOnlyQuery(sql) {
  if (!String(sql || '').trim()) return '请输入要执行的 SELECT 查询'
  const { code, invalid } = analyzeSqlQuery(sql)
  if (invalid) return 'SQL 引号或注释未闭合，或包含不支持的转义/可执行注释'
  if (!/^(?:select|with)\b/i.test(code)) return '只允许执行 SELECT 或只读 WITH 查询'
  if (!/^[^;]*;?$/.test(code)) return '只允许单条 SELECT 查询'
  if (/\b(?:insert|update|delete|merge|drop|alter|truncate|create)\b|\binto\b|\bfor\s+(?:update|share)\b|\block\s+in\s+share\s+mode\b/i.test(code)) {
    return '只读查询不允许写入结果或使用数据库锁定语句'
  }
  return ''
}
