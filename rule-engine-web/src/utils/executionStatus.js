const LABELS = {
  SUCCESS: '成功',
  FAILED: '失败',
  RUNNING: '执行中',
  READY: '已准备',
  SENT: '已发送',
  SKIPPED: '已跳过',
  THROTTLED: '容量受限',
}

// 仅解释后端执行状态；命中/未命中、DONE等模型步骤状态保留各自语义。
export function executionStatusLabel(status, fallback = '未知') {
  return LABELS[status] || status || fallback
}

export function executionStatusHint(status) {
  return status === 'THROTTLED'
    ? '执行资源暂时不足，本次未完成决策，请稍后重试。'
    : ''
}
