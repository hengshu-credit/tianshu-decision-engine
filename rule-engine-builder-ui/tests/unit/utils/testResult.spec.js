import { formatTestOutput, normalizeTestResult } from '@/utils/testResult'

describe('testResult', () => {
  test.each([
    [{ success: true, result: 0 }, 0],
    [{ success: true, result: false }, false],
    [{ success: true, result: null }, null],
    [{ success: true, result: {} }, {}],
    [{ success: true, result: [] }, []],
    [{ success: true, outputs: { score: 350 } }, { score: 350 }],
    [{ success: true, value: 'PASS' }, 'PASS']
  ])('归一化规则、模型和变量输出 %#', (payload, expected) => {
    const result = normalizeTestResult(payload)
    expect(result.hasOutput).toBe(true)
    expect(result.output).toEqual(expected)
  })

  test('没有结果字段时不伪造输出', () => {
    expect(normalizeTestResult({ success: false, errorMessage: 'failed' })).toMatchObject({
      hasOutput: false,
      errorMessage: 'failed'
    })
  })

  test('null 和 false 都有明确展示文本', () => {
    expect(formatTestOutput(null)).toBe('null')
    expect(formatTestOutput(false)).toBe('false')
  })
})
