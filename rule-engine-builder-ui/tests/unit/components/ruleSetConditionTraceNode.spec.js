import { mount } from '@test-utils'
import RuleSetConditionTraceNode from '@/components/common/RuleSetConditionTraceNode.vue'

describe('RuleSetConditionTraceNode', () => {
  test('递归渲染条件组层级、组运算符和叶子结果', () => {
    const wrapper = mount(RuleSetConditionTraceNode, {
      props: {
        node: {
          kind: 'group',
          operator: 'AND',
          result: true,
          children: [
            {
              kind: 'condition', varCode: 'age', varName: '年龄', actualText: '20',
              operatorText: '大于等于', thresholdText: '18', result: true
            },
            {
              kind: 'group', operator: 'OR', result: false,
              children: [{
                kind: 'condition', varCode: 'score', varName: '评分', actualText: '50',
                operatorText: '大于等于', thresholdText: '60', result: false
              }]
            }
          ]
        }
      }
    })

    expect(wrapper.findAll('.rs-trace-group')).toHaveLength(2)
    expect(wrapper.findAll('.rs-trace-leaf')).toHaveLength(2)
    expect(wrapper.findAll('.rs-trace-group-op').at(0).text()).toBe('且')
    expect(wrapper.findAll('.rs-trace-group-op').at(1).text()).toBe('或')
    expect(wrapper.text()).toContain('实际值 20')
    expect(wrapper.text()).toContain('阈值 60')
    expect(wrapper.find('.rs-trace-group.is-fail').exists()).toBe(true)
    expect(wrapper.find('.rs-trace-leaf.is-fail').exists()).toBe(true)
  })
})
