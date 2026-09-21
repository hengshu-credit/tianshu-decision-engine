export function validationRepairHint(issue) {
  const code = String(issue && issue.code || '')
  if (/REFERENCE|DEPENDENCY|MISSING_.*ID/.test(code)) return '请在问题位置重新选择已启用的字段或依赖；引用使用 ID，修改显示名称不能修复失效引用。'
  if (/SCHEMA|FIELD|TYPE/.test(code)) return '请核对输入、输出字段的类型和必填要求，修改后重新校验当前修订。'
  if (/COMPILE|MODEL_JSON/.test(code)) return '请检查条件、动作和模型内容是否完整，修复后点击“编译”。未完成配置可先点击“保存”保留草稿。'
  return '请核对该位置的配置。修复后点击“保存”和“编译”，再返回生命周期重新校验。'
}

export function validationPathLabel(path) {
  const labels = { nodes: '节点', edges: '连线', rules: '规则行', scoreItems: '评分项', thresholds: '等级区间', dims: '维度' }
  const match = String(path || '').match(/^\$\.([A-Za-z]+)\[(\d+)\]/)
  return match && labels[match[1]] ? `第 ${Number(match[2]) + 1} 个${labels[match[1]]}` : '当前规则配置'
}

export function graphIssueTarget(model, path) {
  const match = String(path || '').match(/^\$\.(nodes|edges)\[(\d+)\](?:\.|$)/)
  if (!match) return null
  const item = model && model[match[1]] && model[match[1]][Number(match[2])]
  return item && item.id ? { elementId: item.id, baseType: match[1] === 'edges' ? 'edge' : 'node' } : null
}
