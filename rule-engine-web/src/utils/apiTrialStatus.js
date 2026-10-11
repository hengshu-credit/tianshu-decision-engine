export function trialStatusLabel(status) {
  return { CREATED: '已建立', RUNNING: '执行中', WAITING: '等待结果', SUCCEEDED: '执行完成', FAILED: '执行失败', CANCELLED: '已取消', EXPIRED: '已过期', UNKNOWN: '状态待核实' }[status] || '状态待核实'
}

export function trialStatusType(status) {
  return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : ['WAITING', 'EXPIRED', 'UNKNOWN'].includes(status) ? 'warning' : 'info'
}
