import { assertConsoleMessages } from '../consoleGate'

describe('console 输出门禁', () => {
  test('未声明的 console.error 会使断言失败', () => {
    expect(() => assertConsoleMessages([
      { source: 'console.error', message: 'request failed' }
    ], [])).toThrow('request failed')
  })

  test('未声明的 Vue warning 会使断言失败', () => {
    expect(() => assertConsoleMessages([
      { source: 'Vue warning', message: 'Failed to resolve directive: loading' }
    ], [])).toThrow('Failed to resolve directive: loading')
  })

  test('显式声明的消息会被消费', () => {
    expect(() => assertConsoleMessages([
      { source: 'console.error', message: 'expected failure: invalid json' }
    ], [/invalid json/])).not.toThrow()
  })

  test('声明了但没有发生的预期消息也会失败', () => {
    expect(() => assertConsoleMessages([], [/must happen/])).toThrow('must happen')
  })
})
