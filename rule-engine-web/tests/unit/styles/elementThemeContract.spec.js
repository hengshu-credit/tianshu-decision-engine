import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { compileString } from 'sass'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(currentDir, '../../..')
const stylesDir = path.join(uiRoot, 'src/styles')
const sourceRoot = path.join(uiRoot, 'src')
const overrideSource = fs.readFileSync(
  path.join(stylesDir, 'element-override.scss'),
  'utf8'
)
const css = compileString(overrideSource, { loadPaths: [stylesDir] }).css
const themeCss = compileString(`
  @use "element-override";
  @use "theme-tokens";
  @use "theme-components";
`, { loadPaths: [stylesDir] }).css

function declarationBlock(selector) {
  const matches = Array.from(css.matchAll(/([^{}]+)\{([^}]*)\}/g))
    .filter(match => match[1]
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .split(',')
      .some(item => item.trim() === selector))
  return matches.at(-1)?.[2] || ''
}

function themeRuleBlock(selector) {
  const matches = Array.from(themeCss.matchAll(/([^{}]+)\{([^}]*)\}/g))
    .filter(match => match[1]
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .split(',')
      .some(item => item.trim() === selector))
  return matches.at(-1)?.[2] || ''
}

function collectThemeSources(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectThemeSources(entryPath)
    if (!/\.(?:css|js|scss|vue)$/.test(entry.name)) return []
    return [{ path: entryPath, source: fs.readFileSync(entryPath, 'utf8') }]
  })
}

test('Element Plus 运行时主色变量使用完整的天枢色阶', () => {
  const root = declarationBlock(':root').toLowerCase()

  expect(root).toContain('--el-color-primary-rgb: 38, 57, 233')
  expect(root).toContain('--el-color-primary: #2639e9')
  expect(root).toContain('--el-color-primary-light-1: #3c4deb')
  expect(root).toContain('--el-color-primary-light-2: #5161ed')
  expect(root).toContain('--el-color-primary-light-3: #6774f0')
  expect(root).toContain('--el-color-primary-light-4: #7d88f2')
  expect(root).toContain('--el-color-primary-light-5: #939cf4')
  expect(root).toContain('--el-color-primary-light-6: #a8b0f6')
  expect(root).toContain('--el-color-primary-light-7: #bec4f8')
  expect(root).toContain('--el-color-primary-light-8: #d4d7fb')
  expect(root).toContain('--el-color-primary-light-9: #e9ebfd')
  expect(root).toContain('--el-color-primary-dark-1: #2233d2')
  expect(root).toContain('--el-color-primary-dark-2: #1e2eba')
})

test('输入框和下拉框只在 Element Plus 外层 wrapper 绘制主题焦点框', () => {
  const input = declarationBlock('.el-input')
  const inputInner = declarationBlock('.el-input__inner')
  const inputFocus = declarationBlock('.el-input__wrapper.is-focus')
  const selectFocus = declarationBlock('.el-select__wrapper.is-focused')

  expect(input).toContain(
    '--el-input-focus-border-color: var(--el-color-primary)'
  )
  expect(inputInner).toContain('box-shadow: none')
  expect(inputInner).toContain('background: transparent')
  expect(inputFocus).toContain('var(--el-input-focus-border-color)')
  expect(inputFocus).toContain('var(--tianshu-focus-ring)')
  expect(selectFocus).toContain('var(--el-color-primary)')
  expect(selectFocus).toContain('var(--tianshu-focus-ring)')
  expect(css).not.toContain('.el-input__inner:focus')
})

test('业务状态色不被主色变量覆盖', () => {
  const root = declarationBlock(':root')

  expect(root).not.toMatch(
    /--el-color-(?:success|warning|danger|error)(?:\s|:)/
  )
})

test('前端源码不再残留 Element 旧默认蓝色', () => {
  const legacyBlue =
    /#(?:409eff|1890ff|2878ff|e6f7ff|ecf5ff|d0e8ff|bae7ff|096dd9|a0cfff|79bbff|66b1ff|3a8ee6)|rgba\(\s*64\s*,\s*158\s*,\s*255/i
  const offenders = collectThemeSources(sourceRoot)
    .filter(({ source }) => legacyBlue.test(source))
    .map(({ path: filePath }) => path.relative(uiRoot, filePath))

  expect(offenders).toEqual([])
})

test('字段选择器和表达式控件显式读取统一主题变量', () => {
  const controls = [
    'src/components/common/VarPicker.vue',
    'src/components/common/OperandPicker.vue',
    'src/components/expression/ExpressionPalette.vue',
    'src/components/expression/ExpressionCanvas.vue'
  ]

  for (const control of controls) {
    const source = fs.readFileSync(path.join(uiRoot, control), 'utf8')
    expect(source, control).toContain('var(--el-color-primary)')
  }
})

test('主按钮变体保持背景和文字的可读性契约', () => {
  const solid = themeRuleBlock(
    '.el-button--primary:not(.is-link):not(.is-text):not(.is-plain)'
  )
  const plain = themeRuleBlock('.el-button--primary.is-plain')

  expect(solid).toContain('color: var(--tianshu-brand-foreground)')
  expect(solid).toContain('background-color: var(--el-color-primary)')
  expect(plain).toContain('color: var(--tianshu-text-primary)')
  expect(plain).toContain('background-color: var(--el-color-primary-light-9)')
  expect(themeCss).not.toMatch(
    /\.el-button--primary\s*\{[^}]*background-color:\s*var\(--el-color-primary\)/
  )
})

test('文字拖选和组件选中态统一读取当前主题色', () => {
  const selection = themeRuleBlock('::selection')
  const option = themeRuleBlock('.el-select-dropdown__item.is-selected')
  const tab = themeRuleBlock('.el-tabs__item.is-active')
  const tableRow = themeRuleBlock(
    '.el-table__body tr.current-row > td.el-table__cell'
  )
  const treeNode = themeRuleBlock(
    '.el-tree-node.is-current > .el-tree-node__content'
  )

  expect(themeCss).toContain('--tianshu-selection-bg:')
  expect(themeCss).toContain('--tianshu-selection-foreground:')
  expect(selection).toContain('background: var(--tianshu-selection-bg)')
  expect(selection).toContain('color: var(--tianshu-selection-foreground)')
  for (const block of [option, tab, tableRow, treeNode]) {
    expect(block).toContain('var(--el-color-primary)')
  }
})

test('页面提示容器统一读取当前主题衍生提示色', () => {
  const moduleHint = themeRuleBlock('.module-hint')
  const linkageHint = themeRuleBlock('.linkage-hint')

  for (const block of [moduleHint, linkageHint]) {
    expect(block).toContain('color: var(--tianshu-info-text)')
    expect(block).toContain('background: var(--tianshu-info-bg)')
    expect(block).toContain('border-color: var(--tianshu-info-border)')
  }
})

test('表格操作按钮按 data-action 使用独立主题色和悬停背景', () => {
  expect(themeCss).toContain('.el-table .el-button[data-action]')
  expect(themeCss).toContain('color: var(--tianshu-action-color)')
  expect(themeCss).toContain('rgba(var(--tianshu-action-rgb), 0.12)')

  for (const role of ['edit', 'detail', 'execute', 'inspect', 'configure', 'delete']) {
    expect(themeCss).toContain(`[data-action=${role}]`)
    expect(themeCss).toContain(`--tianshu-action-${role},`)
    expect(themeCss).toContain(`--tianshu-action-${role}-rgb,`)
  }
})
