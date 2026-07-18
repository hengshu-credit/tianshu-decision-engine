import { renderOnlineRunner, renderOnlineRunnerScript } from '@/utils/apiDoc/onlineRunner'
import { apiDocStyles } from '@/utils/apiDoc/styles'

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

  test('调用台包含环境、鉴权、参数 Tab、三种 Body 和响应区', () => {
    const html = renderOnlineRunner()

    expect(html).toContain('在线调用')
    expect(html).toContain('Base URL')
    expect(html).toContain('鉴权方式')
    expect(html).toContain('data-runner-tab="query"')
    expect(html).toContain('data-runner-tab="header"')
    expect(html).toContain('data-runner-tab="body"')
    expect(html).toContain('data-body-type="none"')
    expect(html).toContain('data-body-type="form-data"')
    expect(html).toContain('data-body-type="json"')
    expect(html).not.toContain('runner-param-form')
    expect(html).toContain('返回结果')
    expect(html).toContain('取消请求')
  })

  test('Query、Header 和 JSON Body 使用离线代码编辑器', () => {
    const html = renderOnlineRunner()

    expect(html).toContain('data-code-editor="runner-query"')
    expect(html).toContain('data-code-editor="runner-headers"')
    expect(html).toContain('data-code-editor="runner-body"')
    expect(html).toContain('data-editor-mode="kv"')
    expect(html).toContain('data-editor-mode="json"')
  })

  test('form-data 支持 Text、File、新增和删除参数', () => {
    const html = renderOnlineRunner()
    const source = renderOnlineRunnerScript()

    expect(html).toContain('runner-form-data-rows')
    expect(html).toContain('runner-form-data-add')
    expect(source).toContain('new FormData()')
    expect(source).toContain("row.type === 'FILE'")
    expect(source).toContain('formData.append')
    expect(source).toContain('data-form-row-delete')
  })

  test('form-data 对对象和数组示例值按 JSON 文本发送', () => {
    const source = renderOnlineRunnerScript()

    expect(source).toContain('function formValue')
    expect(source).toContain('JSON.stringify(value)')
    expect(source).toContain('formValue(exampleValue(field))')
  })

  test('HMAC form-data 在读取签名缓冲区前限制文件总大小', () => {
    const source = renderOnlineRunnerScript()

    expect(source).toContain('4 * 1024 * 1024')
    expect(source).toContain('form-data 文件总大小不能超过 4 MB')
  })

  test('发送逻辑按 Body 类型处理 Content-Type 和请求体', () => {
    const source = renderOnlineRunnerScript()

    expect(source).toContain("state.bodyType === 'none'")
    expect(source).toContain("state.bodyType === 'form-data'")
    expect(source).toContain("headers.delete('Content-Type')")
    expect(source).toContain("headers.set('Content-Type', 'application/json')")
    expect(source).toContain('window.ApiDocEditors.validate')
    expect(source).toContain('window.ApiDocEditors.get')
  })

  test('鉴权凭据默认收进折叠区域', () => {
    const html = renderOnlineRunner()

    expect(html).toContain('<details class="runner-auth-details"')
    expect(html).toContain('<summary>鉴权凭据')
  })

  test('调试台运行时脚本语法有效并对 HMAC form-data 签名实际字节', () => {
    const source = renderOnlineRunnerScript()

    expect(() => new Function(source)).not.toThrow()
    expect(source).toContain('preparedRequest.arrayBuffer()')
    expect(source).toContain("preparedRequest.headers.get('Content-Type')")
  })

  test('Apifox 式参数区域只显示活动 Tab 和 Body 类型', () => {
    expect(apiDocStyles).toContain('.runner-config-grid')
    expect(apiDocStyles).toContain('.body-type-panel')
    expect(apiDocStyles).toContain('.body-type-panel.active')
    expect(apiDocStyles).toContain('.form-data-table')
    expect(apiDocStyles).toMatch(/\.runner\{[^}]*padding:0/)
  })
})
