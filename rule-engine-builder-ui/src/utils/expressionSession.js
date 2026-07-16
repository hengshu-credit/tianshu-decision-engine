function routeSegment(value) {
  return encodeURIComponent(String(value == null ? '' : value))
}

export function createExpressionSessionId(ruleId, pickerUid) {
  return `expression-${routeSegment(ruleId)}-${routeSegment(pickerUid)}`
}
