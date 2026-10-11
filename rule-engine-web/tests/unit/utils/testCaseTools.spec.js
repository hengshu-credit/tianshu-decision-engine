import { diffTestResults, filterTraceTree, scenarioParams } from '@/utils/testCaseTools'
import { stringifyExactJson } from '@/utils/exactJson'

describe('testCaseTools', () => {
  test('旧快照深调试追踪不影响业务Diff，同名业务traces仍比较', () => {
    let debug = { leaf: true }
    for (let i = 0; i < 160; i++) debug = { next: debug }
    const original = JSON.stringify({ data: { success: true, result: { traces: ['business'], score: 88 }, traces: [debug] } })
    expect(diffTestResults(original, '{"data":{"success":true,"result":{"traces":["business"],"score":88}}}')).toEqual([])
    expect(diffTestResults(original, '{"data":{"success":true,"result":{"traces":["changed"],"score":88}}}')).toEqual([{ path: '$.result.traces[0]', expected: 'business', actual: 'changed' }])
  })

  test('固定用例参数只从 params 对象读取', () => {
    expect(stringifyExactJson(scenarioParams('{"clientAppName":"rule-test","params":{"age":18}}'))).toBe('{"age":18}')
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

  test('相邻的大整数JSON结果不能因为JS舍入而被判相等', () => {
    const diffs = diffTestResults('{"success":true,"result":{"value":9007199254740992}}',
      '{"success":true,"result":{"value":9007199254740993}}')
    expect(diffs).toHaveLength(1)
    expect(diffs[0].path).toBe('$.result.value')
  })

  test('数组中的对象仅键序变化不应产生差异', () => {
    expect(diffTestResults('{"success":true,"result":[{"code":"A","amount":3}]}',
      '{"success":true,"result":[{"amount":3,"code":"A"}]}')).toEqual([])
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
