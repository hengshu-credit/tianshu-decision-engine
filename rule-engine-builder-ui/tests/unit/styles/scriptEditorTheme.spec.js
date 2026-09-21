import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { parse } from '@vue/compiler-sfc'
import { compileString } from 'sass'

function styles(relativePath) {
  const file = fileURLToPath(new URL(relativePath, import.meta.url))
  const { descriptor } = parse(fs.readFileSync(file, 'utf8'))
  return compileString(descriptor.styles.map(style => style.content).join('\n')).css
}

function block(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return [...css.matchAll(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`, 'g'))]
    .map(match => match[1]).join('\n')
}

describe('脚本编辑和预览表面主题', () => {
  test('QL 编辑区、状态栏、底栏与应用语义颜色一致', () => {
    const css = styles('../../../src/views/designer/ScriptEditor.vue')
    expect(block(css, '.se-editor-container')).toContain('background: var(--tianshu-bg-soft)')
    for (const selector of ['.se-statusbar', '.se-footer']) {
      expect(block(css, selector)).toContain('background: var(--tianshu-bg-muted)')
      expect(block(css, selector)).toContain('var(--tianshu-border-subtle)')
    }
    expect(block(css, '.se-editor-container')).toContain('min-height: 0')
    expect(block(css, '.se-editor-container :deep(.monaco-editor-container)')).toContain('inset: 0')
  })

  test('共用脚本预览的文本、行号与复制操作不使用固定深色或蓝色', () => {
    const css = styles('../../../src/components/common/ScriptPanel.vue')
    expect(block(css, '.sp-editor')).toContain('background: var(--tianshu-bg-soft)')
    expect(block(css, '.sp-editor')).toContain('color: var(--tianshu-text-primary)')
    for (const selector of ['.sp-statusbar', '.sp-footer', '.sp-line-numbers']) {
      expect(block(css, selector)).toContain('background: var(--tianshu-bg-muted)')
    }
    expect(block(css, '.sp-statusbar :deep(.el-button)')).toContain('color: var(--tianshu-info-text)')
  })
})
