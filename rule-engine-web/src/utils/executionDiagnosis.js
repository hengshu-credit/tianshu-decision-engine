import { parseMetadataResponse } from './metadataResponse'
import { resolveTraceReferences } from './traceReference'

const labels = { API: '外数接口', EXTERNAL_API: '外数接口', DB: '数据库查询', DATABASE: '数据库查询', MODEL: '模型执行', LIST: '名单匹配', SFTP: 'SFTP 文件读取' }
export function executionDiagnosis(detail = {}) {
  let trace
  try { trace = resolveTraceReferences(parseMetadataResponse(detail.traceInfo || [])) } catch { trace = [] }
  const frames = [], events = []
  const visit = value => {
    if (!value || typeof value !== 'object') return
    if (Array.isArray(value)) { value.forEach(visit); return }
    if (value.traceKind === 'RULE') frames.push(value)
    if (['MODULE_CALL', 'LIST_ACTION'].includes(value.type)) events.push(value)
    // 只遍历执行追踪结构；不把输入/输出报文中的同名字段当成执行证据。
    for (const key of ['events', 'children', 'traceInfo']) visit(value[key])
  }
  visit(trace)
  const failures = events.filter(event => ['FAILED', 'ERROR', 'REJECTED', 'TIMEOUT'].includes(event.status || event.result?.status)).map(event => ({ ...event, stageLabel: labels[event.moduleType] || event.label || '名单动作', message: event.errorMessage || event.result?.error || '请查看原始追踪' }))
  for (const frame of frames.filter(frame => ['FAILED', 'ERROR', 'TIMEOUT'].includes(frame.status))) failures.push({ moduleType: 'RULE', resourceId: frame.ruleId, stageLabel: '规则执行', message: frame.errorMessage || detail.errorMessage || '具体失败步骤请查看执行时追踪' })
  const effects = events.filter(event => event.type === 'LIST_ACTION').map(event => ({ taskId: event.result?.taskId, status: event.result?.status || 'UNKNOWN', message: ({ QUEUED: '已持久化交接，尚不能证明已完成写入', REJECTED: '交接被拒绝', SKIPPED: '本次模式已跳过写入', SUCCESS: '追踪记录为成功' })[event.result?.status] || '未记录可确认的完成状态' }))
  const calls = events.filter(event => event.type === 'MODULE_CALL').map(event => ({ ...event, stageLabel: labels[event.moduleType] || event.moduleType || '运行模块' }))
  return { failures, effects, calls, revisionId: detail.revisionId || frames[0]?.revisionId, artifactDigest: detail.artifactDigest || frames[0]?.artifactDigest, snapshot: frames.some(frame => Number(frame.schemaVersion) >= 2 && frame.modelJson), ruleId: frames[0]?.ruleId || detail.rootRuleId || detail.ruleId,
    mode: detail.replayed ? '历史离线回放' : '原始执行记录',
    retry: effects.some(effect => effect.status === 'QUEUED') ? '先按任务 ID 核对名单恢复队列及写入结果。已交接任务可能继续执行，不要重新触发整条规则。' : '先修复失败配置，再核对供应商请求、账单与名单写入是否已发生；确认幂等键和重复调用策略后才能重试。历史回放仅使用已保存快照，不是生产重试。' }
}

export function diagnosticResource(event) {
  const id = String(event.resourceId || '')
  if (!/^[1-9]\d*$/.test(id)) return null
  const routes = { RULE: ['rule', `/rule/${id}`], API: ['datasource', `/datasource/api/${id}`], EXTERNAL_API: ['datasource', `/datasource/api/${id}`], DB: ['database', `/database/${id}`], DATABASE: ['database', `/database/${id}`], MODEL: ['model', `/model/${id}`], LIST: ['list', `/list/${id}`], SFTP: ['database', '/sftp'] }
  const route = routes[event.moduleType]
  return route ? { permission: `${route[0]}:view`, path: route[1] } : null
}
