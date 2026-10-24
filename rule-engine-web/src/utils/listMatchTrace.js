export function listMatchPayload(value) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (error) {
    return {}
  }
}

export function listMatchOutcome(request, response, success, errorMessage) {
  if (success === 0 || errorMessage || response.error) {
    return { label: '匹配失败', tone: 'danger', description: '本次匹配未正常完成，不能据此判断是否符合名单条件。' }
  }
  if (typeof response.hit !== 'boolean') {
    return { label: '未记录匹配结果', tone: 'info', description: '日志没有保存明确的匹配结果，请结合原始报文排查。' }
  }
  const excluded = ['NOT_IN_LIST', 'NOT_CONTAINED_IN_LIST'].includes(request.matchMode)
  return {
    label: excluded ? (response.hit ? '满足排除条件' : '不满足排除条件') : (response.hit ? '命中' : '未命中'),
    tone: response.hit ? 'success' : 'info',
    description: response.hit ? '本次输入满足所配置的匹配方式和组合条件。' : '本次输入不满足所配置的匹配方式和组合条件。',
  }
}
