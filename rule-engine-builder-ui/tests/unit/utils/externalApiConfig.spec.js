import {
  emptyPayloadCaptureConfig,
  isPayloadCapturePath,
  parsePayloadCaptureConfig,
  stringifyPayloadCaptureConfig,
} from '@/utils/externalApiConfig'

describe('external API payload capture config', () => {
  test('支持对象、数组下标、通配符和含点字段路径并保留大小写', () => {
    expect(isPayloadCapturePath('$.userId')).toBe(true)
    expect(isPayloadCapturePath('$.items[*].Base64')).toBe(true)
    expect(isPayloadCapturePath("$['含点.字段'][0]")).toBe(true)
    expect(isPayloadCapturePath('$.items[0]')).toBe(true)
    expect(isPayloadCapturePath('items[*]')).toBe(false)
    expect(isPayloadCapturePath('$.items[foo]')).toBe(false)
  })

  test('空配置使用原始报文和不限制大小的默认值', () => {
    expect(parsePayloadCaptureConfig('')).toEqual(emptyPayloadCaptureConfig())
  })

  test('规范化并序列化请求响应留存配置', () => {
    const value = parsePayloadCaptureConfig(JSON.stringify({
      request: {
        source: 'PROCESSED',
        excludePaths: ['$.items[*].Base64', '$.items[*].Base64', "$['含点.字段']"],
        maxFieldBytes: '4096',
      },
      response: { source: 'ORIGINAL', excludePaths: [], maxFieldBytes: 0 },
    }))

    expect(value.request).toEqual({
      source: 'PROCESSED',
      saveOriginal: true,
      excludePaths: ['$.items[*].Base64', "$['含点.字段']"],
      maxFieldBytes: 4096,
      oversizedFields: [],
    })
    expect(JSON.parse(stringifyPayloadCaptureConfig(value))).toEqual(value)
  })

  test('拒绝非法路径、来源和大小上限', () => {
    expect(() => parsePayloadCaptureConfig('null')).toThrow('报文留存配置必须是 JSON 对象')
    expect(() => parsePayloadCaptureConfig({
      request: { source: 'MASKED', excludePaths: [], maxFieldBytes: 0 },
    })).toThrow('请求留存来源')
    expect(() => parsePayloadCaptureConfig({
      request: { source: 'ORIGINAL', excludePaths: ['$.items[bad]'], maxFieldBytes: 0 },
    })).toThrow('请求排除路径')
    expect(() => parsePayloadCaptureConfig({
      response: { source: 'ORIGINAL', excludePaths: [], maxFieldBytes: -1 },
    })).toThrow('响应单字段上限')
    expect(() => parsePayloadCaptureConfig({
      response: { source: 'ORIGINAL', excludePaths: [], maxFieldBytes: 52428801 },
    })).toThrow('响应单字段上限')
  })

  test('支持按路径配置 Base64 或 3DES 留存解密', () => {
    const value = parsePayloadCaptureConfig({
      response: {
        source: 'ORIGINAL',
        decrypt: { enabled: true, mode: 'TRIPLE_DES_BASE64', path: '$.payload', keyVariable: 'desKey' },
      },
    })
    expect(value.response.decrypt).toEqual({
      enabled: true,
      mode: 'TRIPLE_DES_BASE64',
      path: '$.payload',
      keyVariable: 'desKey',
    })
    expect(() => parsePayloadCaptureConfig({
      response: { decrypt: { enabled: true, mode: 'TRIPLE_DES_BASE64', path: '$' } },
    })).toThrow('3DES解密')
    expect(parsePayloadCaptureConfig({ request: { saveOriginal: false } }).request.saveOriginal).toBe(false)
    expect(() => parsePayloadCaptureConfig({ request: { saveOriginal: 'false' } })).toThrow('保留原文')
  })

  test('超长字段策略按字段意图保存或省略，不按运行时长度二次判断', () => {
    const value = parsePayloadCaptureConfig({
      response: {
        oversizedFields: [
          { path: '$.image', store: false },
          { path: '$.encrypted', store: true },
        ],
      },
    })
    expect(value.response.oversizedFields).toEqual([
      { path: '$.image', store: false },
      { path: '$.encrypted', store: true },
    ])
    expect(() => parsePayloadCaptureConfig({ response: { oversizedFields: [{ path: '$.image' }, { path: '$.image' }] } })).toThrow('不能重复')
  })
})
