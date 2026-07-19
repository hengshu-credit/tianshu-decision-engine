import { diffTestResults, filterTraceTree, scenarioParams } from '@/utils/testCaseTools'

describe('testCaseTools', () => {
  test('固定用例参数只从 params 对象读取', () => {
    expect(scenarioParams('{"clientAppName":"rule-test","params":{"age":18}}')).toEqual({ age: 18 })
    expect(scenarioParams('{"age":18}')).toEqual({})
  })

  test('结果 diff 忽略耗时和追踪等不稳定字段', () => {
    const expected = { code: 200, data: { success: true, result: { level: 'A' }, executeTimeMs: 10, traces: [{ id: 1 }] } }
    const actual = { code: 200, data: { success: true, result: { level: 'A' }, executeTimeMs: 99, traces: [{ id: 2 }] } }
    expect(diffTestResults(expected, actual)).toEqual([])
  })

  test('结果 diff 输出具体 JSON 路径', () => {
    const diffs = diffTestResults(
      { code: 200, data: { success: true, result: { level: 'A' } } },
      { code: 200, data: { success: true, result: { level: 'B' } } }
    )
    expect(diffs).toEqual([{ path: '$.result.level', expected: 'A', actual: 'B' }])
  })

  test('追踪筛选保留命中子节点及其祖先', () => {
    const trace = {
      status: 'SUCCESS',
      children: [
        { status: 'FAILED', ruleCode: 'R1', children: [] },
        { status: 'SUCCESS', ruleCode: 'R2', children: [] }
      ]
    }
    expect(filterTraceTree(trace, { status: 'FAILED', keyword: 'R1' })).toEqual({
      status: 'SUCCESS',
      children: [{ status: 'FAILED', ruleCode: 'R1', children: [] }]
    })
  })
})
