const fs = require('fs')
const path = require('path')

const globalStyle = fs.readFileSync(
  path.resolve(__dirname, '../../../src/styles/index.scss'),
  'utf8'
)
const indexHtml = fs.readFileSync(
  path.resolve(__dirname, '../../../index.html'),
  'utf8'
)

describe('global font resources', () => {
  test('does not load font styles from an external URL', () => {
    expect(globalStyle).not.toMatch(/@import\s+url\(['"]?https?:\/\//)
  })

  test('body uses the bundled font family defined in index.html', () => {
    expect(indexHtml).toContain("font-family: 'AlimamaFangYuanTiVF'")
    expect(indexHtml).toContain("src: url('./fonts/font.ttf')")
    expect(globalStyle).toMatch(
      /body\s*\{[\s\S]*?font-family:\s*"AlimamaFangYuanTiVF"/
    )
  })

  test('does not suppress runtime warnings by replacing console.warn', () => {
    expect(indexHtml).not.toMatch(/console\.warn\s*=/)
    expect(indexHtml).not.toContain('non-passive')
  })
})
