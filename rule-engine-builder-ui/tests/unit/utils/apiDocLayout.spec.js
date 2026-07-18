import { renderLayoutScript, renderResizeHandles } from '@/utils/apiDoc/layout'
import { apiDocStyles } from '@/utils/apiDoc/styles'

describe('API 文档三栏布局', () => {
  test('输出左右两个可访问拖拽手柄', () => {
    const handles = renderResizeHandles()

    expect(handles.nav).toContain('data-resize="nav"')
    expect(handles.nav).toContain('role="separator"')
    expect(handles.nav).toContain('aria-label="调整接口菜单宽度"')
    expect(handles.runner).toContain('data-resize="runner"')
    expect(handles.runner).toContain('aria-label="调整调试栏宽度"')
  })

  test('拖拽脚本限制宽度、支持双击复位且不持久化', () => {
    const source = renderLayoutScript()

    expect(source).toContain("addEventListener('pointerdown'")
    expect(source).toContain("addEventListener('dblclick'")
    expect(source).toContain("setProperty('--nav-width'")
    expect(source).toContain("setProperty('--runner-width'")
    expect(source).toContain('window.innerWidth * 0.2')
    expect(source).toContain('Math.max(200')
    expect(source).toContain('window.innerWidth * 0.4')
    expect(source).toContain('Math.max(360')
    expect(source).toContain("matchMedia('(max-width: 1000px)')")
    expect(source).not.toContain('localStorage')
    expect(source).not.toContain('sessionStorage')
  })

  test('接口切换只显示当前接口并同步右侧选择器', () => {
    const source = renderLayoutScript()

    expect(source).toContain("querySelectorAll('.endpoint-panel')")
    expect(source).toContain("querySelectorAll('[data-endpoint-nav]')")
    expect(source).toContain('scrollIntoView')
    expect(source).toContain("dispatchEvent(new Event('change'" )
  })

  test('常见桌面宽度仍显示左右拖拽栏', () => {
    expect(apiDocStyles).toContain('minmax(280px,1fr)')
    expect(apiDocStyles).toContain('@media(min-width:1001px)')
    expect(apiDocStyles).toContain('@media(max-width:1000px)')
    expect(apiDocStyles).toContain('.resize-handle{display:none}')
  })

  test('三栏固定在视口内并拥有相互独立的纵向滚动容器', () => {
    expect(apiDocStyles).toMatch(/body\{[^}]*overflow:hidden/)
    expect(apiDocStyles).toMatch(/\.app\{[^}]*height:100vh[^}]*overflow:hidden/)
    expect(apiDocStyles).toMatch(/\.nav\{[^}]*overflow-y:auto/)
    expect(apiDocStyles).toMatch(/\.content\{[^}]*height:100vh[^}]*overflow-y:auto/)
    expect(apiDocStyles).toMatch(/\.runner\{[^}]*overflow-y:auto/)
    expect(apiDocStyles).toContain('max-width:20vw')
    expect(apiDocStyles).toContain('max-width:40vw')
  })
})
