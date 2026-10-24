import { mount } from '@test-utils'
import RuleSetConditionTraceNode from '@/components/common/RuleSetConditionTraceNode.vue'

describe('RuleSetConditionTraceNode', () => {
  test('一元判断的追踪不展示不存在的右侧阈值', () => {
    const wrapper = mount(RuleSetConditionTraceNode, { props: { node: { kind: 'condition', contentOperand: { kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'name', label: '姓名', valueType: 'STRING' }, hasThreshold: false, thresholdOperand: null, thresholdText: '为空', operatorText: '为空', result: true } } })
    expect(wrapper.findAll('.content-ref__source').map(part => part.text())).toEqual(['变量'])
    expect(wrapper.text()).not.toContain('阈值')
    wrapper.unmount()
  })
  test('输入回退值不伪装成已执行，缺失追踪与真正跳过明确区分', async () => {
    const node = { kind: 'condition', varCode: 'age', varName: '年龄', actualText: '32',
      actualSource: 'input', operatorText: '大于', thresholdText: '18', result: null, traceStatus: 'skipped' }
    const wrapper = mount(RuleSetConditionTraceNode, { props: { node } })
    expect(wrapper.text()).toContain('输入值 32')
    expect(wrapper.find('.rs-trace-leaf-result').text()).toBe('未执行')
    await wrapper.setProps({ node: { ...node, traceStatus: 'missing' } })
    expect(wrapper.find('.rs-trace-leaf-result').text()).toBe('未记录追踪')
    wrapper.unmount()
  })

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
