export const ASYNC_REQUEST_KEYS = ['headerConfig', 'queryConfig', 'requestMapping', 'contentType', 'requestScript', 'responseScript']

export function parseAsyncRequest(text) {
  const value = JSON.parse(text || '{}')
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('轮询请求配置必须是 JSON 对象')
  for (const key of Object.keys(value)) {
    if (!ASYNC_REQUEST_KEYS.includes(key)) throw new Error('轮询请求配置不支持字段：' + key)
  }
  for (const key of ['headerConfig', 'queryConfig', 'requestMapping']) {
    if (value[key] != null && (typeof value[key] !== 'object' || Array.isArray(value[key]))) throw new Error(key + ' 必须是 JSON 对象')
  }
  return value
}

export function validateAsyncApi(config) {
  if (config.requestMode !== 'ASYNC') return
  if (!['POLL', 'CALLBACK'].includes(config.asyncResultMode)) throw new Error('请选择异步结果获取方式')
  const polling = config.asyncResultMode === 'POLL'
  const protocol = JSON.parse((polling ? config.asyncPollConfig : config.asyncCallbackConfig) || '{}')
  for (const [key, label] of [['taskIdPath', '任务号路径'], ['statusPath', '状态路径'], ['successValue', '成功值']]) {
    if (protocol[key] == null || !String(protocol[key]).trim()) throw new Error('异步' + label + '不能为空')
  }
  if (protocol.failureValue && String(protocol.failureValue) === String(protocol.successValue)) throw new Error('异步成功值和失败值不能相同')
  if (polling) {
    if (!protocol.resultEndpointUrl?.trim()) throw new Error('请填写异步结果查询地址')
    for (const key of ['intervalMs', 'maxAttempts']) {
      if (!Number.isInteger(protocol[key]) || protocol[key] <= 0 || protocol[key] > 2147483647) throw new Error('异步 ' + key + ' 必须是正整数')
    }
  } else {
    if (!/^https?:\/\/.+\/api\/external-callback\/\$\{invocationId\}$/.test(config.asyncCallbackUrl || '')) throw new Error('请填写公网回调地址模板，并保留 /api/external-callback/${invocationId}')
    if (!protocol.signatureHeader?.trim() || !protocol.signatureSecret?.trim()) throw new Error('请填写回调签名 Header 和 HMAC-SHA256 密钥')
  }
}
