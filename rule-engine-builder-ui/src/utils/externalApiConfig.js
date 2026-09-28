export const ASYNC_REQUEST_KEYS = ['headerConfig', 'queryConfig', 'requestMapping', 'contentType', 'requestScript', 'responseScript']

const PAYLOAD_CAPTURE_SOURCES = ['ORIGINAL', 'PROCESSED']
const PAYLOAD_CAPTURE_DECRYPT_MODES = ['BASE64', 'TRIPLE_DES_BASE64']
export const MAX_PAYLOAD_CAPTURE_FIELD_BYTES = 50 * 1024 * 1024
const PAYLOAD_CAPTURE_PATH_PATTERN = /^\$(?:(?:\.[^.[\]\s]+)|(?:\[(?:\d+|\*)\])|(?:\[(?:'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")\]))*$/

export function emptyPayloadCaptureConfig() {
  return {
    request: { source: 'ORIGINAL', saveOriginal: true, excludePaths: [], maxFieldBytes: 0, decrypt: { enabled: false, mode: 'BASE64', path: '$', keyVariable: '' } },
    response: { source: 'ORIGINAL', saveOriginal: true, excludePaths: [], maxFieldBytes: 0, decrypt: { enabled: false, mode: 'BASE64', path: '$', keyVariable: '' } },
  }
}

export function isPayloadCapturePath(value) {
  return PAYLOAD_CAPTURE_PATH_PATTERN.test(String(value || '').trim())
}

function normalizePayloadCaptureSide(value, label) {
  const side = value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const source = side.source == null || side.source === '' ? 'ORIGINAL' : String(side.source)
  if (!PAYLOAD_CAPTURE_SOURCES.includes(source)) {
    throw new Error(label + '留存来源只能是 ORIGINAL 或 PROCESSED')
  }
  const rawPaths = side.excludePaths == null ? [] : side.excludePaths
  if (!Array.isArray(rawPaths)) throw new Error(label + '排除路径必须是数组')
  const excludePaths = []
  rawPaths.forEach((path) => {
    const normalized = String(path == null ? '' : path).trim()
    if (!normalized) return
    if (!isPayloadCapturePath(normalized)) {
      throw new Error(label + '排除路径格式不合法：' + normalized)
    }
    if (!excludePaths.includes(normalized)) excludePaths.push(normalized)
  })
  const rawMaxFieldBytes = side.maxFieldBytes == null || side.maxFieldBytes === '' ? 0 : Number(side.maxFieldBytes)
  if (!Number.isSafeInteger(rawMaxFieldBytes) || rawMaxFieldBytes < 0 || rawMaxFieldBytes > MAX_PAYLOAD_CAPTURE_FIELD_BYTES) {
    throw new Error(label + '单字段上限必须在 0 到 52428800 字节之间')
  }
  if (side.saveOriginal != null && typeof side.saveOriginal !== 'boolean') throw new Error(label + '保留原文必须是布尔值')
  const saveOriginal = side.saveOriginal !== false
  const rawDecrypt = side.decrypt && typeof side.decrypt === 'object' && !Array.isArray(side.decrypt)
    ? side.decrypt
    : {}
  const decrypt = {
    enabled: rawDecrypt.enabled === true,
    mode: rawDecrypt.mode == null || rawDecrypt.mode === '' ? 'BASE64' : String(rawDecrypt.mode).toUpperCase(),
    path: rawDecrypt.path == null || rawDecrypt.path === '' ? '$' : String(rawDecrypt.path).trim(),
    keyVariable: rawDecrypt.keyVariable == null ? '' : String(rawDecrypt.keyVariable).trim()
  }
  if (!PAYLOAD_CAPTURE_DECRYPT_MODES.includes(decrypt.mode)) throw new Error(label + '解密模式不受支持')
  if (!isPayloadCapturePath(decrypt.path)) throw new Error(label + '解密路径格式不合法：' + decrypt.path)
  if (decrypt.enabled && decrypt.mode === 'TRIPLE_DES_BASE64' && !decrypt.keyVariable) throw new Error(label + '3DES解密必须填写密钥变量名')
  const result = { source, saveOriginal, excludePaths, maxFieldBytes: rawMaxFieldBytes }
  if (decrypt.enabled || decrypt.keyVariable || decrypt.path !== '$' || decrypt.mode !== 'BASE64') result.decrypt = decrypt
  return result
}

export function normalizePayloadCaptureConfig(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('报文留存配置必须是 JSON 对象')
  }
  const config = value
  return {
    request: normalizePayloadCaptureSide(config.request, '请求'),
    response: normalizePayloadCaptureSide(config.response, '响应'),
  }
}

export function parsePayloadCaptureConfig(value) {
  if (value == null || String(value).trim() === '') return emptyPayloadCaptureConfig()
  const parsed = typeof value === 'string' ? JSON.parse(value) : value
  return normalizePayloadCaptureConfig(parsed)
}

export function stringifyPayloadCaptureConfig(value) {
  return JSON.stringify(normalizePayloadCaptureConfig(value))
}

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
