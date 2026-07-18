import { renderCodeEditor, renderCodeEditorScript } from '@/utils/apiDoc/editor'
import { apiDocStyles } from '@/utils/apiDoc/styles'

describe('API 文档离线代码编辑器', () => {
  test('渲染行号、高亮层、格式化、重置和错误提示', () => {
    const html = renderCodeEditor({ id: 'runner-body', mode: 'json', value: '{"name":"<demo>"}' })

    expect(html).toContain('data-code-editor="runner-body"')
    expect(html).toContain('code-editor-lines')
    expect(html).toContain('code-editor-highlight')
    expect(html).toContain('data-editor-format="runner-body"')
    expect(html).toContain('data-editor-reset="runner-body"')
    expect(html).toContain('id="runner-body-error"')
    expect(html).toContain('&lt;demo&gt;')
  })

  test('运行时支持 JSON 与 name=value 校验、Tab 缩进和格式化', () => {
    const source = renderCodeEditorScript()

    expect(source).toContain('window.ApiDocEditors')
    expect(source).toContain('JSON.parse')
    expect(source).toContain("event.key === 'Tab'")
    expect(source).toContain("event.key === 'Enter'")
    expect(source).toContain('缺少 =')
    expect(source).toContain('JSON 格式错误')
    expect(source).toContain('code-token-key')
    expect(source).toContain('(?=\\s*:)')
    expect(source).toContain('\\b(true|false)\\b')
    expect(source).toContain('/^\\s*/')
    expect(source).toContain("split(/\\r?\\n/)")
  })

  test('编辑器完全离线且不持久化输入', () => {
    const source = renderCodeEditorScript()

    expect(source).not.toContain('monaco')
    expect(source).not.toContain('localStorage')
    expect(source).not.toContain('sessionStorage')
    expect(source).not.toContain('fetch(')
  })

  test('编辑器样式使用统一字体并同步行号与输入区域', () => {
    expect(apiDocStyles).toContain('.code-editor-lines')
    expect(apiDocStyles).toContain('.code-editor-highlight')
    expect(apiDocStyles).toContain('caret-color')
    expect(apiDocStyles).toContain('.code-token-key')
    expect(apiDocStyles).toMatch(/\.code-editor-input\{[^}]*min-height:0/)
  })

  test('编辑器右下角支持纵向调整高度', () => {
    expect(apiDocStyles).toMatch(/\.code-editor-frame\{[^}]*resize:vertical/)
    expect(apiDocStyles).toMatch(/\.code-editor-frame\{[^}]*min-height:132px/)
    expect(apiDocStyles).toMatch(/\.code-editor-frame\{[^}]*max-height:70vh/)
  })
})
