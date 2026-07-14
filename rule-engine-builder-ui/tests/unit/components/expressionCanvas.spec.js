import { shallowMount } from '@vue/test-utils'
import ExpressionCanvas from '@/components/expression/ExpressionCanvas.vue'

describe('ExpressionCanvas', () => {
  const node = {
    kind: 'FUNCTION',
    functionCode: 'max',
    args: [
      { kind: 'LITERAL', value: '1', valueType: 'NUMBER' },
      { kind: 'LITERAL', value: '2', valueType: 'NUMBER' }
    ]
  }

  test('折叠节点隐藏子树并显示隐藏节点数量', () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      propsData: { node, path: [], selectedPath: [], collapsedPathKeys: ['$'] }
    })

    expect(wrapper.find('.canvas-children').exists()).toBe(false)
    expect(wrapper.find('.canvas-collapse__count').text()).toBe('2')
  })

  test('折叠按钮只发出当前路径且不改变表达式数据', async() => {
    const wrapper = shallowMount(ExpressionCanvas, {
      propsData: { node, path: [], selectedPath: [], collapsedPathKeys: [] }
    })
    await wrapper.find('.canvas-collapse').trigger('click')

    expect(wrapper.emitted().toggleCollapse[0][0]).toEqual([])
    expect(node.args).toHaveLength(2)
  })
})
