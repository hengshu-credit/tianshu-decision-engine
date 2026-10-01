import path from 'node:path'
import { fileURLToPath } from 'node:url'
import fs from 'node:fs'
import { compile } from 'sass'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(currentDir, '../../..')
const stylesDir = path.join(uiRoot, 'src/styles')

function compiled(file) {
  return compile(path.join(stylesDir, file), {
    loadPaths: [stylesDir],
  }).css
}

function declarationBlock(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))?.[1] || ''
}

function collectStyleSources(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectStyleSources(entryPath)
    if (!/\.(?:css|scss|vue)$/.test(entry.name)) return []
    return [{
      file: path.relative(uiRoot, entryPath).replace(/\\/g, '/'),
      source: fs.readFileSync(entryPath, 'utf8'),
    }]
  })
}

describe('全局日夜主题样式契约', () => {
  test('白天模式声明完整的天枢语义表面令牌', () => {
    const css = compiled('theme-tokens.scss')
    const root = declarationBlock(css, ':root')

    for (const token of [
      '--tianshu-bg-page',
      '--tianshu-bg-surface',
      '--tianshu-bg-elevated',
      '--tianshu-bg-muted',
      '--tianshu-text-primary',
      '--tianshu-text-secondary',
      '--tianshu-text-tertiary',
      '--tianshu-border',
      '--tianshu-border-subtle',
      '--tianshu-shadow-small',
      '--tianshu-shadow-medium',
      '--tianshu-shadow-large',
    ]) expect(root, token).toContain(token)
  })

  test('夜间模式同步覆盖 Element Plus 表面文字边框和遮罩变量', () => {
    const css = compiled('theme-tokens.scss')
    const dark = declarationBlock(css, '[data-theme=dark]')

    for (const token of [
      '--el-bg-color',
      '--el-bg-color-page',
      '--el-bg-color-overlay',
      '--el-fill-color-blank',
      '--el-fill-color-light',
      '--el-text-color-primary',
      '--el-text-color-regular',
      '--el-border-color',
      '--el-border-color-light',
      '--el-mask-color',
    ]) expect(dark, token).toContain(token)
  })

  test('设计器强调表面和流程节点提供随主题切换的语义令牌', () => {
    const css = compiled('theme-tokens.scss')
    const root = declarationBlock(css, ':root')
    const dark = declarationBlock(css, '[data-theme=dark]')

    for (const token of [
      '--tianshu-designer-accent-bg',
      '--tianshu-designer-accent-border',
      '--tianshu-designer-alt-bg',
      '--tianshu-flow-node-bg',
      '--tianshu-flow-node-text',
    ]) {
      expect(root, token).toContain(token)
      expect(dark, token).toContain(token)
    }
  })

  test('全局组件层支持渐变主按钮、定宽内容和文档滚动侧栏', () => {
    const css = compiled('theme-components.scss')

    expect(css).toContain('background-image: var(--tianshu-brand-gradient)')
    expect(css).toContain('max-width: 1440px')
    expect(css).not.toContain('.theme-sidebar--static')
    expect(css).toContain('.layout-main .el-loading-mask')
    expect(css).toContain('[data-theme=dark]')
    expect(css).toContain('.el-dialog')
    expect(css).toContain('.el-table')
    expect(css).toContain('.lf-graph')
  })

  test('业务组件的中性表面不再固定为浅色背景', () => {
    const allowed = new Set([
      'src/components/theme/ThemeSettingsDrawer.vue',
      'src/styles/theme-tokens.scss',
      'src/styles/theme-components.scss',
    ])
    const fixedLightSurface = /background(?:-color)?\s*:\s*(?:white|#fff(?:fff)?|#f3f4f6|#f5f7fa|#f8fafc|#fafafa)\b/i
    const offenders = collectStyleSources(path.join(uiRoot, 'src'))
      .filter(({ file }) => !allowed.has(file))
      .filter(({ source }) => fixedLightSurface.test(source))
      .map(({ file }) => file)

    expect(offenders).toEqual([])
  })

  test('业务组件的中性文字和边框使用日夜语义令牌', () => {
    const allowed = new Set([
      'src/components/theme/ThemeSettingsDrawer.vue',
      'src/styles/theme-tokens.scss',
      'src/styles/theme-components.scss',
    ])
    const fixedText = /(?:^|[;{\r\n]\s*)color\s*:\s*(?:#303133|#606266|#333(?:333)?|#1e293b|#0f172a|#172554|#475569|#64748b|#94a3b8)\b/im
    const fixedBorder = /(?:^|[;{\r\n]\s*)border(?:-top|-right|-bottom|-left|-color)?\s*:[^;\r\n]*(?:#dcdfe6|#e4e7ed|#e8e8e8|#ebeef5|#e2e8f0|#dbe3f0|#d9d9d9|#f0f0f0|#cbd5e1)\b/im
    const offenders = collectStyleSources(path.join(uiRoot, 'src'))
      .filter(({ file }) => !allowed.has(file))
      .filter(({ source }) => fixedText.test(source) || fixedBorder.test(source))
      .map(({ file }) => file)

    expect(offenders).toEqual([])
  })

  test('表达式追踪树的夜间状态使用主题语义色', () => {
    const css = compiled('theme-components.scss')

    expect(css).toContain('[data-theme=dark] .trace-wrap')
    expect(css).toContain('var(--tianshu-bg-surface)')
    expect(css).toContain('var(--tianshu-success-bg)')
    expect(css).toContain('var(--tianshu-danger-bg)')
    expect(css).toContain('var(--tianshu-text-secondary)')
  })
})
