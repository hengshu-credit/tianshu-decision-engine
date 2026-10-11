// 只选择明确的阶段数组，不解释或改写业务报文中的 $ref 字段。
export function selectTraceSteps(...candidates) {
  return candidates.find(value => Array.isArray(value) && value.length > 0) || []
}
