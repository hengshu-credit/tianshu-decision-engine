import { generateCodeSamples } from '@/utils/apiDoc/codeSamples'

describe('API 文档五语言代码示例', () => {
  const endpoint = {
    method: 'POST',
    path: '/api/rule/sync/execute/RISK',
    baseUrl: 'https://api.example.com',
    body: '{"clientAppName":"api-doc-example","params":{"age":17}}'
  }

  test('HMAC 生成五种可执行思路一致的语言示例', () => {
    const samples = generateCodeSamples(endpoint, { authType: 'HMAC_SHA256' })

    expect(Object.keys(samples)).toEqual(['shell', 'java', 'python', 'javascript', 'go'])
    Object.values(samples).forEach(code => {
      expect(code).toContain('X-Rule-Signature')
      expect(code).toContain('X-Rule-Timestamp')
      expect(code).toContain('X-Rule-Nonce')
      expect(code).toContain('<HMAC_SECRET>')
    })
    expect(samples.java).toContain('HttpURLConnection')
    expect(samples.java).not.toContain('HttpClient.newHttpClient')
  })

  test.each([
    ['LEGACY_TOKEN', 'X-Rule-Token', '<PROJECT_TOKEN>'],
    ['API_KEY', 'X-Partner-Key', '<API_KEY>'],
    ['BEARER_TOKEN', 'Bearer', '<ACCESS_TOKEN>']
  ])('%s 示例使用对应传递方式', (authType, marker, credential) => {
    const samples = generateCodeSamples(endpoint, {
      authType,
      placement: 'HEADER',
      parameterName: 'X-Partner-Key'
    })

    Object.values(samples).forEach(code => {
      expect(code).toContain(marker)
      expect(code).toContain(credential)
    })
  })

  test('Basic 示例使用各语言标准的 Basic Auth 写法', () => {
    const samples = generateCodeSamples(endpoint, { authType: 'BASIC' })

    expect(samples.shell).toContain("-u '<USERNAME>:<PASSWORD>'")
    expect(samples.java).toContain('Basic ')
    expect(samples.python).toContain("'Basic ' + credentials")
    expect(samples.javascript).toContain("'Basic ' + btoa")
    expect(samples.go).toContain('SetBasicAuth("<USERNAME>", "<PASSWORD>")')
  })

  test('Query API Key 追加到查询参数而不是请求头', () => {
    const samples = generateCodeSamples(endpoint, {
      authType: 'API_KEY',
      placement: 'QUERY',
      parameterName: 'partner_key'
    })

    expect(samples.shell).toContain('partner_key=%3CAPI_KEY%3E')
    expect(samples.javascript).toContain('searchParams.set("partner_key", \'<API_KEY>\')')
  })
})
