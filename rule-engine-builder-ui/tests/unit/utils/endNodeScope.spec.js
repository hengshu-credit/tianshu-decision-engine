import {
  END_SCOPE_CURRENT_RULE,
  END_SCOPE_ALL_RULES,
  normalizeEndScope,
  getEndNodeAppearance
} from '@/utils/endNodeScope'
import { getDefaultFlowData } from '@/components/flow/nodes'

describe('end node scope', () => {
  test('旧结束节点默认只跳出当前规则', () => {
    expect(normalizeEndScope()).toBe(END_SCOPE_CURRENT_RULE)
    expect(normalizeEndScope('UNKNOWN')).toBe(END_SCOPE_CURRENT_RULE)
  })

  test('两种结束范围使用不同文案和颜色', () => {
    expect(getEndNodeAppearance(END_SCOPE_CURRENT_RULE)).toMatchObject({
      text: '返回',
      fill: '#FA8C16',
      stroke: '#D46B08'
    })
    expect(getEndNodeAppearance(END_SCOPE_ALL_RULES)).toMatchObject({
      text: '终止',
      fill: '#FF4D4F',
      stroke: '#CF1322'
    })
  })

  test('初始化模板只包含开始节点', () => {
    const data = getDefaultFlowData()

    expect(data.nodes).toHaveLength(1)
    expect(data.nodes[0].type).toBe('start-event')
    expect(data.edges).toEqual([])
  })
})
