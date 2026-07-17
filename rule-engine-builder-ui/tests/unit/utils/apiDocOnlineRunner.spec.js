import { renderOnlineRunner, renderOnlineRunnerScript } from '@/utils/apiDoc/onlineRunner'

describe('API 文档在线调用台', () => {
  test('运行时代码不持久化用户输入的凭据', () => {
    const source = renderOnlineRunnerScript()

    expect(source).not.toContain('localStorage')
    expect(source).not.toContain('sessionStorage')
    expect(source).not.toContain('document.cookie')
  })

  test('区分超时、网络跨域与非 JSON 响应', () => {
    const source = renderOnlineRunnerScript()

    expect(source).toContain('AbortError')
    expect(source).toContain('请求超时')
    expect(source).toContain('网络连接失败或被跨域策略阻止')
    expect(source).toContain('response.text()')
    expect(source).toContain('JSON.parse')
  })

  test('浏览器不支持 Web Crypto 时拒绝 HMAC', () => {
    const source = renderOnlineRunnerScript()

    expect(source).toContain('window.crypto.subtle')
    expect(source).toContain('当前浏览器环境不支持安全的 HMAC 计算')
  })

  test('调用台包含环境、鉴权、表单、Header、Body 和响应区', () => {
    const html = renderOnlineRunner()

    expect(html).toContain('在线调用')
    expect(html).toContain('Base URL')
    expect(html).toContain('鉴权方式')
    expect(html).toContain('请求参数表单')
    expect(html).toContain('Header')
    expect(html).toContain('请求 Body')
    expect(html).toContain('返回结果')
    expect(html).toContain('取消请求')
  })
})
