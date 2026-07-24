import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { compileString } from 'sass'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const stylesDir = path.resolve(currentDir, '../../../src/styles')
const source = fs.readFileSync(
  path.join(stylesDir, 'element-override.scss'),
  'utf8'
)
const css = compileString(source, { loadPaths: [stylesDir] }).css

function declarationBlock(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))?.[1] || ''
}

test('小尺寸表单控件保持升级前的 32px 舒适高度', () => {
  expect(declarationBlock(':root')).toContain('--el-component-size-small: 32px')
  expect(declarationBlock('.el-button--small')).toContain(
    '--el-button-size: var(--el-component-size-small)'
  )
  expect(declarationBlock('.el-select--small .el-select__wrapper')).toContain(
    'min-height: var(--el-component-size-small)'
  )
})

test('小尺寸输入框、下拉框和按钮使用可读的 14px 字号', () => {
  expect(declarationBlock('.el-button--small')).toContain('font-size: 14px')
  expect(declarationBlock('.el-input--small')).toContain('font-size: 14px')
  expect(declarationBlock('.el-select--small .el-select__wrapper')).toContain(
    'font-size: 14px'
  )
  expect(declarationBlock('.el-range-editor--small .el-range-input')).toContain(
    'font-size: 14px'
  )
  expect(declarationBlock('.el-range-editor--small .el-range-separator')).toContain(
    'font-size: 14px'
  )
})

test('语义链接按钮保持透明背景并使用可读的业务颜色', () => {
  const linkButton = declarationBlock('.el-button.is-link')
  expect(linkButton).toContain('min-height: 28px')
  expect(linkButton).toContain('padding: 4px 6px')
  expect(linkButton).toContain('background-color: transparent !important')
  expect(declarationBlock('.el-button.is-link.el-button--primary').toLowerCase()).toContain(
    'color: var(--el-color-primary) !important'
  )
  expect(declarationBlock('.el-button.is-link.el-button--success').toLowerCase()).toContain(
    'color: #087a5d !important'
  )
  expect(declarationBlock('.el-button.is-link.el-button--warning').toLowerCase()).toContain(
    'color: #b45309 !important'
  )
  expect(declarationBlock('.el-button.is-link.el-button--info').toLowerCase()).toContain(
    'color: #475569 !important'
  )
  expect(declarationBlock('.el-button.is-link.el-button--danger').toLowerCase()).toContain(
    'color: #c93333 !important'
  )
})

test('语义按钮、标签和占位文字使用高对比度主题色', () => {
  const root = declarationBlock(':root').toLowerCase()
  expect(root).toContain('--el-text-color-placeholder: #64748b')

  expect(declarationBlock('.el-button--success').toLowerCase()).toContain(
    'background-color: #087a5d !important'
  )
  expect(declarationBlock('.el-button--warning').toLowerCase()).toContain(
    'background-color: #b45309 !important'
  )
  expect(declarationBlock('.el-button--danger').toLowerCase()).toContain(
    'background-color: #c93333 !important'
  )
  expect(declarationBlock('.el-tag--success').toLowerCase()).toContain(
    'color: #087a5d !important'
  )
  expect(declarationBlock('.el-tag--warning').toLowerCase()).toContain(
    'color: #92400e !important'
  )
  expect(declarationBlock('.el-tag--danger').toLowerCase()).toContain(
    'color: #b42318 !important'
  )
  expect(css).toContain('.el-input__inner::placeholder')
  expect(css).toContain('color: #64748b')
})

test('input borders are rendered only by the Element Plus wrapper', () => {
  const inputInner = declarationBlock('.el-input__inner')
  const wrapperHover = declarationBlock('.el-input__wrapper:hover')
  const wrapperFocus = declarationBlock('.el-input__wrapper.is-focus')

  expect(inputInner).toContain('border: 0')
  expect(inputInner).toContain('border-radius: 0')
  expect(inputInner).toContain('box-shadow: none')
  expect(inputInner).toContain('background: transparent')
  expect(wrapperHover).toContain('box-shadow:')
  expect(wrapperFocus).toContain('box-shadow:')
  expect(css).not.toContain('.el-input__inner:hover')
  expect(css).not.toContain('.el-input__inner:focus')
  expect(css).toContain('.el-textarea__inner:focus')
})
