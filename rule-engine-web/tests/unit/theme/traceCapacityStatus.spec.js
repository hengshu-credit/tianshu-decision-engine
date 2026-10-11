import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@vue/compiler-sfc'
import { compileString } from 'sass'
import { ACCENT_PRESETS, DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'
import { applyTheme } from '@/theme/themeRuntime'

const rootDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const components = ['components/common/TraceTree.vue', 'components/common/ExternalCallTrace.vue', 'views/test/RuleTest.vue', 'components/common/DesignerTestDialog.vue']
const componentCss = components.map(file => parse(fs.readFileSync(path.join(rootDirectory, 'src', file), 'utf8')).descriptor.styles
  .map(style => compileString(style.content).css).join('\n')).join('\n')
const globalCss = compileString('@use "theme-tokens"; @use "theme-components";', {
  loadPaths: [path.join(rootDirectory, 'src/styles')],
}).css

describe('容量受限状态的实际CSS与主题令牌', () => {
  let style
  let fixture
  let rootAttributes

  beforeAll(() => {
    rootAttributes = Array.from(document.documentElement.attributes).map(attribute => [attribute.name, attribute.value])
    style = document.createElement('style')
    style.textContent = globalCss + componentCss
    document.head.appendChild(style)
    fixture = document.createElement('div')
    fixture.innerHTML = `
      <div class="trace-wrap">
        <span data-capacity class="rule-trace-status is-throttled">容量受限</span>
        <div class="trace-wrap"><span data-capacity class="rule-trace-status is-throttled">容量受限</span></div>
        <div class="rule-trace-module"><span data-capacity class="rule-trace-module-status is-throttled">容量受限</span></div>
        <span data-success class="rule-trace-status is-success">成功</span>
        <span data-failed class="rule-trace-status is-failed">失败</span>
        <div class="external-call-trace"><span data-capacity class="external-call-trace-status is-throttled">容量受限</span></div>
      </div>
      <div class="rule-test-page">
        <div data-capacity class="result-section-title is-throttled">容量提示</div>
        <pre data-capacity class="result-pre is-throttled">执行资源暂时不足</pre>
      </div>
      <div class="designer-test-dialog"><pre data-capacity class="result-pre capacity-pre">模型资源暂时不足</pre></div>`
    document.body.appendChild(fixture)
  })

  afterAll(() => {
    fixture.remove()
    style.remove()
    for (const attribute of Array.from(document.documentElement.attributes)) document.documentElement.removeAttribute(attribute.name)
    for (const [name, value] of rootAttributes) document.documentElement.setAttribute(name, value)
  })

  test.each(['LIGHT', 'DARK'])('%s及所有品牌主题的容量状态始终使用warning语义', colorScheme => {
    for (const preset of ACCENT_PRESETS) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentPreset: preset.id })
      for (const status of fixture.querySelectorAll('[data-capacity]')) {
        // jsdom保留var表达式；同时验证实际CSS级联和运行时令牌归属。
        expect(getComputedStyle(status).color, `${preset.id}: ${status.className}`).toBe('var(--tianshu-warning-text)')
      }
      const palette = document.documentElement.style
      expect(palette.getPropertyValue('--tianshu-warning-text')).toBe(palette.getPropertyValue('--tianshu-status-warning-text'))
      expect(palette.getPropertyValue('--tianshu-warning-text')).not.toBe(palette.getPropertyValue('--tianshu-danger-text'))
      expect(getComputedStyle(fixture.querySelector('[data-success]')).color).toBe('var(--tianshu-success-text)')
      expect(getComputedStyle(fixture.querySelector('[data-failed]')).color).toBe('var(--tianshu-danger-text)')
      const details = getComputedStyle(fixture.querySelector('.result-pre.is-throttled'))
      expect(details.background).toBe('var(--tianshu-warning-bg)')
      // jsdom不展开含var的边框颜色，检查浏览器实际载入的CSS声明。
      const borderRule = Array.from(style.sheet.cssRules).find(rule => rule.selectorText === '.result-pre.is-throttled')
      expect(borderRule.style.getPropertyValue('border-color')).toBe('var(--tianshu-warning-border)')
    }
  })
})
