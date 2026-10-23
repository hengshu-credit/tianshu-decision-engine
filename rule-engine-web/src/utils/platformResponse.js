import catalogue from '@/constants/platformResponseCodes.json'

export const PLATFORM_CODES = Object.fromEntries(catalogue.map(item => [item.name, item]))
export const PLATFORM_SUCCESS_CODE = PLATFORM_CODES.SUCCESS.code
export const PLATFORM_RESPONSE_CODES = catalogue
const codesByValue = Object.fromEntries(catalogue.map(item => [item.code, item]))

export function isPlatformResponse(body, httpStatus) {
  const status = typeof body?.code === 'string' ? codesByValue[body.code] : null
  return Boolean(status && body.message === status.message
    && body.success === (body.code === PLATFORM_SUCCESS_CODE)
    && (httpStatus == null || httpStatus === status.httpStatus))
}

export function platformResponseMessage(body, fallback = '请求失败') {
  const message = codesByValue[body?.code]?.message || fallback
  return body?.detail && body.detail !== message ? `${message}：${body.detail}` : message
}

// 测试页面需要展示失败执行的 trace 和输出；仅显式选择的请求接收此类数据。
export function acceptsExecutionFailure(config, body) {
  return Boolean(config?.acceptExecutionFailure && body && codesByValue[body.code]
    && body.code !== PLATFORM_SUCCESS_CODE && !body.code.startsWith('3') && !body.code.startsWith('5')
    && body.data?.success === false)
}
