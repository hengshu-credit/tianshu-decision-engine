import { JSDOM, VirtualConsole } from 'jsdom'
import { generateApiDocHtml } from '@/utils/apiDoc'

const doc = {
  project: { id: 7, projectCode: 'credit', projectName: '接口交互验收' },
  authentications: [
    { authType: 'API_KEY', authName: 'API Key', parameterName: 'X-Partner-Key' },
    { authType: 'BASIC', authName: 'Basic' }
  ],
  rules: [
    { id: 8, ruleCode: 'RISK', ruleName: '风险决策', inputVariables: [{ varCode: 'customer.age', varType: 'INTEGER', exampleValue: 17 }] },
    { id: 9, ruleCode: 'SECOND', ruleName: '第二接口', inputVariables: [{ varCode: 'amount', varType: 'INTEGER', exampleValue: 100 }] }
  ]
}

describe('导出 API 文档真实脚本交互', () => {
  let dom
  let errors

  beforeEach(async () => {
    errors = []
    const virtualConsole = new VirtualConsole()
    virtualConsole.on('jsdomError', error => errors.push(error.message))
    dom = new JSDOM(generateApiDocHtml(doc, { logoSvg: '<svg></svg>' }), {
      url: 'https://docs.example.com',
      runScripts: 'dangerously',
      virtualConsole,
      beforeParse(window) {
        window.HTMLElement.prototype.scrollIntoView = vi.fn()
        window.Headers = Headers
        window.fetch = vi.fn().mockResolvedValue(new Response('{"code":200,"data":{"success":true}}', {
          headers: { 'Content-Type': 'application/json' }
        }))
        window.navigator.clipboard = { writeText: vi.fn().mockResolvedValue(undefined) }
      }
    })
    await new Promise(resolve => dom.window.addEventListener('load', resolve, { once: true }))
  })

  afterEach(() => dom.window.close())

  test('完整 HTML 启动无脚本异常，页签和接口按钮均能切换内容', () => {
    expect(errors).toEqual([])
    const page = dom.window.document
    expect(dom.window.ApiDocEditors).toBeDefined()
    page.querySelector('[data-tab-target="auth-1"]').click()
    expect(page.getElementById('auth-1').classList.contains('active')).toBe(true)
    expect(page.getElementById('auth-0').classList.contains('active')).toBe(false)
    page.querySelector('[data-endpoint-nav="9"]').click()
    expect(page.querySelector('.endpoint-panel.active').id).toBe('endpoint-SECOND')
    expect(page.getElementById('runner-endpoint').value).toBe('9')
    expect(JSON.parse(page.getElementById('runner-body').value).params).toEqual({ amount: 100 })
    expect(errors).toEqual([])
  })

  test('桌面调试栏关闭后隐藏并释放空间，在线调用按钮可重新打开', () => {
    const page = dom.window.document
    page.getElementById('runner-close').click()
    expect(dom.window.getComputedStyle(page.getElementById('online-runner')).display).toBe('none')
    expect(dom.window.getComputedStyle(page.getElementById('runner-toggle')).display).not.toBe('none')
    page.getElementById('runner-toggle').click()
    expect(dom.window.getComputedStyle(page.getElementById('online-runner')).display).not.toBe('none')
    expect(dom.window.getComputedStyle(page.getElementById('runner-toggle')).display).toBe('none')
    expect(page.activeElement.id).toBe('runner-close')
  })

  test('准备请求时立即防止重复发送，取消后不会继续请求', async () => {
    const page = dom.window.document
    const send = page.getElementById('runner-send')
    send.click()
    expect(send.disabled).toBe(true)
    expect(page.getElementById('runner-cancel').disabled).toBe(false)
    send.click()
    page.getElementById('runner-cancel').click()
    await vi.waitFor(() => expect(send.disabled).toBe(false))
    expect(dom.window.fetch).not.toHaveBeenCalled()
    expect(page.getElementById('runner-status').textContent).toBe('请求已取消')
  })

  test('发送后展示响应并能复制，取消只中止当前请求', async () => {
    const page = dom.window.document
    page.getElementById('runner-base-url').value = 'localhost:8080/gateway'
    page.getElementById('credential-apiKey').value = 'test-key'
    page.getElementById('runner-send').click()
    await vi.waitFor(() => expect(page.getElementById('runner-status').textContent).toBe('请求完成'))
    const [url, options] = dom.window.fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/gateway/api/rule/sync/execute/RISK')
    expect(options.headers.get('X-Partner-Key')).toBe('test-key')
    expect(JSON.parse(options.body).params).toEqual({ customer: { age: 17 } })
    page.getElementById('runner-copy-response').click()
    await vi.waitFor(() => expect(dom.window.navigator.clipboard.writeText).toHaveBeenCalledWith(JSON.stringify({ code: 200, data: { success: true } }, null, 2)))

    dom.window.fetch.mockImplementationOnce((url, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new dom.window.DOMException('Cancelled', 'AbortError')))
    }))
    page.getElementById('runner-send').click()
    await vi.waitFor(() => expect(dom.window.fetch).toHaveBeenCalledTimes(2))
    page.getElementById('runner-cancel').click()
    await vi.waitFor(() => expect(page.getElementById('runner-send').disabled).toBe(false))
    expect(dom.window.fetch.mock.calls[1][1].signal.aborted).toBe(true)
    expect(page.getElementById('runner-status').textContent).toBe('请求已取消')
  })

  test('参数页签、Body 类型、表单增删、编辑器格式化与重置均实际生效', () => {
    const page = dom.window.document
    page.querySelector('[data-runner-tab="body"]').click()
    expect(page.getElementById('runner-body-panel').classList.contains('active')).toBe(true)
    expect(page.getElementById('runner-query-panel').classList.contains('active')).toBe(false)
    const body = page.getElementById('runner-body')
    body.value = '{"test":true}'
    body.dispatchEvent(new dom.window.Event('input', { bubbles: true }))
    page.querySelector('[data-editor-format="runner-body"]').click()
    expect(body.value).toBe('{\n  "test": true\n}')
    page.querySelector('[data-editor-reset="runner-body"]').click()
    expect(JSON.parse(body.value).params).toEqual({ customer: { age: 17 } })

    page.querySelector('[data-body-type="form-data"]').click()
    expect(page.querySelector('[data-body-panel="form-data"]').classList.contains('active')).toBe(true)
    const before = page.querySelectorAll('[data-form-row]').length
    page.getElementById('runner-form-data-add').click()
    expect(page.querySelectorAll('[data-form-row]')).toHaveLength(before + 1)
    page.querySelector('[data-form-row]:last-child [data-form-row-delete]').click()
    expect(page.querySelectorAll('[data-form-row]')).toHaveLength(before)
    page.querySelector('[data-body-type="none"]').click()
    expect(page.querySelector('[data-body-panel="none"]').classList.contains('active')).toBe(true)
    expect(errors).toEqual([])
  })

  test('复制 cURL 包含当前参数且不会发送请求，剪贴板受限时有明确提示', async () => {
    const page = dom.window.document
    page.getElementById('runner-use-page-origin').click()
    expect(page.getElementById('runner-base-url').value).toBe('https://docs.example.com')
    dom.window.ApiDocEditors.set('runner-query', 'customer=sample')
    page.getElementById('runner-copy-curl').click()
    await vi.waitFor(() => expect(dom.window.navigator.clipboard.writeText).toHaveBeenCalled())
    const command = dom.window.navigator.clipboard.writeText.mock.calls[0][0]
    expect(command).toContain('https://docs.example.com/api/rule/sync/execute/RISK?customer=sample')
    expect(command).toContain('--data-binary')
    expect(command).toContain('"age": 17')
    expect(dom.window.fetch).not.toHaveBeenCalled()

    dom.window.navigator.clipboard.writeText.mockRejectedValue(new Error('denied'))
    page.execCommand = vi.fn().mockReturnValue(false)
    page.getElementById('runner-copy-curl').click()
    await vi.waitFor(() => expect(page.getElementById('runner-status').textContent).toContain('请手动复制'))
    expect(page.execCommand).toHaveBeenCalledWith('copy')
    expect(page.querySelectorAll('textarea')).toHaveLength(3)
  })

  test('错误参数不会发请求且发送按钮恢复，网络失败不会残留旧响应', async () => {
    const page = dom.window.document
    dom.window.ApiDocEditors.set('runner-body', '{bad json')
    page.getElementById('runner-send').click()
    await vi.waitFor(() => expect(page.getElementById('runner-send').disabled).toBe(false))
    expect(page.getElementById('runner-status').textContent).toBe('请求 Body 不是有效 JSON')
    expect(dom.window.fetch).not.toHaveBeenCalled()
    dom.window.ApiDocEditors.set('runner-body', '{}')
    dom.window.fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    page.getElementById('runner-response-body').textContent = '旧响应'
    page.getElementById('runner-send').click()
    await vi.waitFor(() => expect(page.getElementById('runner-status').textContent).toContain('网络请求失败'))
    expect(page.getElementById('runner-response-body').textContent).toBe('—')
    expect(page.getElementById('runner-cancel').disabled).toBe(true)
  })

  test('超时中止请求并恢复按钮，超时上限与输入框约定一致', async () => {
    const page = dom.window.document
    let expire
    const timer = vi.spyOn(dom.window, 'setTimeout').mockImplementation(callback => { expire = callback; return 1 })
    dom.window.fetch.mockImplementationOnce((url, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new dom.window.DOMException('Timeout', 'AbortError')))
    }))
    page.getElementById('runner-timeout').value = '999999'
    page.getElementById('runner-send').click()
    await vi.waitFor(() => expect(dom.window.fetch).toHaveBeenCalled())
    expect(timer).toHaveBeenCalledWith(expect.any(Function), 180000)
    expire()
    await vi.waitFor(() => expect(page.getElementById('runner-send').disabled).toBe(false))
    expect(page.getElementById('runner-status').textContent).toBe('请求超时')
    expect(page.getElementById('runner-connection-help').textContent).toContain('请求超时')
    expect(page.getElementById('runner-cancel').disabled).toBe(true)
  })

  test('OpenAPI 按钮下载当前项目的 JSON 文件', async () => {
    const page = dom.window.document
    const createUrl = vi.fn().mockReturnValue('blob:api-doc')
    dom.window.URL.createObjectURL = createUrl
    dom.window.URL.revokeObjectURL = vi.fn()
    let download
    page.addEventListener('click', event => {
      if (event.target.tagName === 'A') {
        download = { name: event.target.download, href: event.target.href }
        event.preventDefault()
      }
    }, true)
    page.querySelector('[data-export-openapi]').click()
    expect(download).toEqual({ name: 'credit-openapi.json', href: 'blob:api-doc' })
    const reader = new dom.window.FileReader()
    const content = new Promise(resolve => { reader.onload = () => resolve(reader.result) })
    reader.readAsText(createUrl.mock.calls[0][0])
    const exported = JSON.parse(await content)
    expect(exported.openapi).toBe('3.1.0')
    expect(Object.keys(exported.paths)).toEqual(['/api/rule/sync/execute/RISK', '/api/rule/sync/execute/SECOND'])
    await vi.waitFor(() => expect(dom.window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:api-doc'))
    expect(errors).toEqual([])
  })
})
