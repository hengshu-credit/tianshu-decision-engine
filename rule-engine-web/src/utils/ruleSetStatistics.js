export function ruleSetCoverageHint(coverage) {
  if (!coverage) return ''
  const parts = []
  if (coverage.pendingCount > 0) parts.push(`${coverage.pendingCount} 条日志正在后台补齐，当前统计尚未完整`)
  if (coverage.noTraceCount > 0) parts.push(`${coverage.noTraceCount} 条未记录追踪`)
  if (coverage.invalidCount > 0) parts.push(`${coverage.invalidCount} 条追踪无法解析`)
  if (coverage.truncatedCount > 0) parts.push(`${coverage.truncatedCount} 条追踪已截断`)
  return parts.length ? `所选项目与时间范围内：${parts.join('；')}。统计仅包含可用追踪中实际执行的规则集。` : ''
}
