import { mount } from '@test-utils'
import TraceNode from '@/components/common/TraceNode.vue'

describe('TraceNode', () => {
  test('变量节点缺少追踪值时展示执行输入快照', () => {
    const wrapper = mount(TraceNode, {
      props: {
        node: {
          type: 'OPERATOR',
          token: '+',
          evaluated: true,
          value: 22,
          children: [
            { type: 'VARIABLE', token: 'age', evaluated: true },
            { type: 'VALUE', token: '2', value: 2, evaluated: true }
          ]
        },
        varMap: { age: '年龄' },
        inputParams: { age: 20 }
      }
    })

    expect(wrapper.text()).toContain('年龄 = 20')
    wrapper.unmount()
  })
})
