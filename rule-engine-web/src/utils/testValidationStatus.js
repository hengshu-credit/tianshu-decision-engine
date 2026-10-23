export function validationLabel(status) {
  return { PASSED: '校验通过', FAILED: '预期不符', NOT_CONFIGURED: '未设置预期', INVALID: '预期配置无效', NOT_EVALUATED: '未校验' }[status] || '未校验'
}

export function validationTagType(status) {
  return status === 'PASSED' ? 'success' : ['FAILED', 'INVALID'].includes(status) ? 'danger' : status === 'NOT_EVALUATED' ? 'warning' : 'info'
}
