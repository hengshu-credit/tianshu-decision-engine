function testExecutionIdentity(request) {
  const body = request.postDataJSON() || {}
  const source = body.executionSource
  if (!source) return undefined
  const definitionId = body.definitionId || body.targetId || new URL(request.url()).pathname.match(/\/definition\/(\d+)\//)?.[1]
  if (definitionId == null) throw new Error('测试身份fixture缺少规则ID')
  return {
    ...source,
    definitionId: String(definitionId),
    ...(source.sourceId != null ? { sourceId: String(source.sourceId) } : {}),
    ...(source.sourceType === 'REVISION' ? { revisionId: String(source.sourceId) } : {}),
  }
}

function withTestExecutionIdentity(value) {
  return async context => {
    const data = typeof value === 'function' ? await value(context) : value
    const identity = testExecutionIdentity(context.request)
    return { ...data, ...(identity ? { executionIdentity: identity } : {}) }
  }
}

module.exports = { testExecutionIdentity, withTestExecutionIdentity }
