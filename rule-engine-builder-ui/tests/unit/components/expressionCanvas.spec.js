import { shallowMount } from '@vue/test-utils'
import ExpressionCanvas from '@/components/expression/ExpressionCanvas.vue'
import { createOperationOperand } from '@/utils/operand'

const focusManualInput = jest.fn()

const ElInputStub = {
  name: 'ElInput',
  template: '<input />',
  methods: { focus: focusManualInput }
}

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

  test('折叠按钮位于节点卡片之前', () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      propsData: { node, path: [], selectedPath: [] }
    })
    const children = wrapper.find('.canvas-node-row').element.children

    expect(children[0].classList.contains('canvas-collapse')).toBe(true)
    expect(children[1].classList.contains('canvas-node')).toBe(true)
  })

  test('选中阈值和路径时输入控件出现在画布节点内', async() => {
    focusManualInput.mockClear()
    const literal = shallowMount(ExpressionCanvas, {
      propsData: { node: { kind: 'LITERAL', value: '', valueType: 'NUMBER' }, selectedPath: [], path: [] },
      stubs: { 'el-input': ElInputStub, 'el-select': true, 'el-option': true }
    })
    await literal.vm.$nextTick()
    expect(literal.find('.canvas-inline-editor').exists()).toBe(true)
    expect(literal.findComponent({ name: 'ElSelect' }).attributes('popper-class')).toBe('expression-editor-select-popper')
    expect(focusManualInput).toHaveBeenCalled()

    focusManualInput.mockClear()
    const path = shallowMount(ExpressionCanvas, {
      propsData: { node: { kind: 'PATH', value: '', resolved: false }, selectedPath: [], path: [] },
      stubs: { 'el-input': ElInputStub, 'el-select': true, 'el-option': true }
    })
    await path.vm.$nextTick()
    expect(path.find('.canvas-path-editor').exists()).toBe(true)
    expect(focusManualInput).toHaveBeenCalled()
  })

  test('运算项显示项间运算符且保持同一子层', () => {
    const operation = createOperationOperand([
      { operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' } },
      { operator: '+', operand: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' } },
      { operator: '*', operand: { kind: 'LITERAL', value: '3', valueType: 'NUMBER' } }
    ])
    const wrapper = shallowMount(ExpressionCanvas, { propsData: { node: operation } })

    expect(wrapper.findAll('.canvas-child')).toHaveLength(3)
    expect(wrapper.findAll('.canvas-edge-operator').wrappers.map(item => item.text())).toEqual(['+', '*'])
  })
})
