import { parseExperimentResponse, formatExperimentJson } from '@/utils/experimentResponse'
import ExperimentList from '@/views/experiment/ExperimentList.vue'

test('实验组和完整结果保留大整数、长小数及零值，不把原始文本放入常规元数据', () => {
  const text = '{"code":"000000","data":{"success":true,"productionGroup":{"ruleVersion":3,"result":{"id":9007199254740993,"amount":0.123456789012345678901}},"testGroups":[{"result":0},{"result":false}]}}'
  const parsed = parseExperimentResponse(text)
  expect(parsed.data.productionGroup.ruleVersion).toBe(3)
  const exact = parsed.data.productionGroup.resultJson
  expect(ExperimentList.methods.groupResultPreview(parsed.data.productionGroup.result, exact)).toBe('{"id":9007199254740993,"amount":0.123456789012345678901}')
  expect(parsed.data.testGroups[0].resultJson).toBe('0')
  expect(parsed.data.testGroups[1].resultJson).toBe('false')
  expect(Object.keys(parsed.data)).not.toContain('__rawExperimentJson')
  const formatted = formatExperimentJson(parsed.data)
  expect(formatted).toContain('9007199254740993'); expect(formatted).toContain('0.123456789012345678901')
})

test('普通错误响应和没有结果的组保持可用，非法JSON不能冒充执行结果', () => {
  expect(parseExperimentResponse('{"code":"400005","message":"failed"}')).toEqual({ code: '400005', message: 'failed' })
  const response = parseExperimentResponse('{"data":{"productionGroup":{"skipped":true}}}')
  expect(response.data.productionGroup.resultJson).toBeUndefined()
  expect(() => parseExperimentResponse('not json')).toThrow()
})
