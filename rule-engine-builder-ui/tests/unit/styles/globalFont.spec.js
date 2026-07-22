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

  test('console warning filter accepts non-string warning payloads', () => {
    const script = indexHtml.match(/<script>([\s\S]*?)<\/script>/)[1]
    const forwarded = []
    const mockConsole = {
      warn: (...args) => forwarded.push(args),
    }

    Function('console', script)(mockConsole)

    expect(() => mockConsole.warn({ type: 'deprecation' }, 'detail')).not.toThrow()
    expect(forwarded).toEqual([[{ type: 'deprecation' }, 'detail']])
    mockConsole.warn('Added non-passive event listener to touchmove')
    expect(forwarded).toHaveLength(1)
  })
})
