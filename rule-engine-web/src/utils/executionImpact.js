// 只说明所选版本可静态识别的调用，不把未识别到的副作用解释成“安全试算”。
export function executionImpact(runtimeNodes = [], model = null, modelType = '') {
  const calls = new Map(), writes = new Map()
  let dynamic = modelType === 'SCRIPT'
  const add = (type, id, label) => calls.set(`${type}:${id}`, { type, id: String(id ?? ''), label })
  const labels = { API: '外数接口', DB: '数据库查询', SFTP: 'SFTP 文件读取', LIST: '名单匹配', MODEL: '模型执行', DERIVED: '衍生取值' }
  for (const node of runtimeNodes) {
    if (labels[node.sourceType]) add(node.sourceType, node.refId, `${labels[node.sourceType]}：${node.label || node.code || node.refId}`)
  }
  const visit = value => {
    if (!value || typeof value !== 'object') return
    if (Array.isArray(value)) { value.forEach(visit); return }
    if (value.type === 'api-call') add('API', value.apiId, `外数接口 #${value.apiId}`)
    if (value.type === 'list-write') for (const id of value.listIds || []) writes.set(`${id}:${value.operation}`, { id: String(id), operation: value.operation || '未配置' })
    if (['rule-call', 'func-call'].includes(value.type) || value.kind === 'FUNCTION' || ['RULE', 'FUNCTION'].includes(value.refType)) dynamic = true
    if (value.refType === 'MODEL' && value.refId) add('MODEL', value.refId, `模型 #${value.refId}`)
    if (value.refType === 'EXTERNAL_API' && value.refId) add('API', value.refId, `外数接口 #${value.refId}`)
    Object.values(value).forEach(visit)
  }
  visit(model)
  return { calls: [...calls.values()], writes: [...writes.values()], dynamic }
}
