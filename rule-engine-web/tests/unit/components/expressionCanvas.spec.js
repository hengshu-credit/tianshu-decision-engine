import { shallowMount } from '@test-utils'
import ExpressionCanvas from '@/components/expression/ExpressionCanvas.vue'
import { createOperationOperand } from '@/utils/operand'

const focusManualInput = vi.fn()

const ElInputStub = {
  name: 'ElInput',
  template: '<input />',
  methods: { focus: focusManualInput }
}

describe('ExpressionCanvas', () => {
  test('方法节点显示名称、编码、版本和返回类型图标', () => {
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: { kind: 'FUNCTION', functionId: 7, functionCode: 'arrGet', label: '数组取值', valueType: 'OBJECT', versionMode: 'FIXED', version: 3, args: [] } } })
    expect(wrapper.get('.content-ref__source').text()).toBe('方法')
    expect(wrapper.get('.content-ref__type').text()).toBe('{}')
    expect(wrapper.get('.content-ref__name').text()).toBe('数组取值')
    wrapper.unmount()
  })
  test('内容节点同时展示来源、具体数据类型、名称编码和版本', () => {
    const node = { kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'age', label: '年龄', valueType: 'INTEGER', versionMode: 'FIXED', version: 3 }
    const wrapper = shallowMount(ExpressionCanvas, { props: { node, fieldPickerEnabled: true } })
    expect(wrapper.get('.content-ref__source').text()).toBe('变量')
    expect(wrapper.get('.content-ref__type').text()).toBe('123')
    expect(wrapper.get('.content-ref__type').attributes('title')).toContain('INTEGER')
    expect(wrapper.get('.content-ref__version').text()).toBe('v3')
    wrapper.unmount()
  })
  test('单目操作符独立保留一行但不渲染右侧节点', () => {
    const node = { kind: 'OPERATION', terms: [{ operand: { kind: 'LITERAL', value: '', valueType: 'STRING' } }, { operator: 'is_empty' }] }
    const wrapper = shallowMount(ExpressionCanvas, { props: { node, fieldPickerEnabled: true } })
    expect(wrapper.findAllComponents(ExpressionCanvas)).toHaveLength(1)
    expect(wrapper.get('.canvas-edge-operator').text()).toContain('无需右值')
    expect(wrapper.vm.operatorOptions(wrapper.vm.children[1]).map(item => item.value)).not.toContain('-')
    wrapper.unmount()
  })
  test.each(['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION', 'ACCESS', 'CAST', 'ARRAY', 'OPERATION'])('内嵌 %s 行点击打开选择器，未选中也能在同一边框内删除', async kind => {
    const path = ['args', 0]
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: { kind, value: '1', valueType: 'NUMBER' }, fieldPickerEnabled: true, path } })
    await wrapper.get('.canvas-node').trigger('click')
    expect(wrapper.emitted('requestField')).toEqual([[path]])
    expect(wrapper.find('.canvas-inline-editor').exists()).toBe(false)
    await wrapper.get('.canvas-node .canvas-node-delete').trigger('click')
    expect(wrapper.emitted('remove')).toEqual([[path]])
    expect(wrapper.emitted('requestField')).toHaveLength(1)
    wrapper.unmount()
  })
  test('嵌套运算块和空列表底部都能追加，并传递准确块路径', async () => {
    const path = ['args', 0]
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: { kind: 'ARRAY', items: [] }, fieldPickerEnabled: true, path } })
    await wrapper.get('.canvas-node-append').trigger('click')
    expect(wrapper.emitted('appendRow')).toEqual([[path]])
    await wrapper.setProps({ node: createOperationOperand([{ operand: null }]) })
    await wrapper.get('.canvas-node-append').trigger('click')
    expect(wrapper.emitted('appendRow')).toEqual([[path], [path]])
    await wrapper.setProps({ node: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' }, appendExpression: true })
    await wrapper.get('.canvas-node-append').trigger('click')
    expect(wrapper.emitted('appendRow').at(-1)).toEqual([path])
    wrapper.unmount()
  })
  test('取值和转换选项固定在节点中，字段更新保留节点路径与子字段', async () => {
    const node = { kind: 'ACCESS', accessType: 'KEY', target: { kind: 'REFERENCE', refType: 'VARIABLE', refId: 1 }, accessor: null }
    const wrapper = shallowMount(ExpressionCanvas, { props: { node, fieldPickerEnabled: true, path: ['args', 0] } })
    wrapper.findComponent({ name: 'ExpressionNodeOptions' }).vm.$emit('patch', { accessType: 'INDEX' })
    expect(wrapper.emitted('patchNode')[0][0]).toEqual({ path: ['args', 0], fields: { accessType: 'INDEX' } })
    expect(node.target.refId).toBe(1)
    wrapper.unmount()
  })
  test('操作区仅保留删除；箭头导航、Alt 加箭头排序和前向删除各自分开', async () => {
    const path = ['terms', 1, 'operand']
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: { kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'age' }, path, selectedPath: path } })
    expect(wrapper.findAll('.canvas-node-actions button')).toHaveLength(1)
    const card = wrapper.get('.canvas-node')
    await card.trigger('keydown', { key: 'ArrowUp' })
    await card.trigger('keydown', { key: 'ArrowDown', altKey: true })
    await card.trigger('keydown', { key: 'Delete' })
    await card.trigger('keydown', { key: 'Backspace' })
    expect(wrapper.emitted('navigate')).toEqual([[{ path, offset: -1 }]])
    expect(wrapper.emitted('move')).toEqual([[{ path, offset: 1 }]])
    expect(wrapper.emitted('deleteKey').map(args => args[0].key)).toEqual(['Delete', 'Backspace'])
    wrapper.unmount()
  })
  test('内嵌模式点击空位或字段打开子选择器，并向子节点传递能力', async () => {
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: null, fieldPickerEnabled: true, path: ['args', 0] } })
    await wrapper.get('.canvas-node').trigger('click')
    expect(wrapper.emitted('requestField')).toEqual([[['args', 0]]])
    await wrapper.setProps({ node: { kind: 'ARRAY', items: [null] } })
    expect(wrapper.findComponent(ExpressionCanvas).props('fieldPickerEnabled')).toBe(true)
    wrapper.unmount()
  })
  test('空项显示删除入口并支持 Delete、Tab 和操作符键', async () => {
    const path = ['terms', 1, 'operand']
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: null, path, selectedPath: path } })
    await wrapper.get('[aria-label="删除节点"]').trigger('click')
    await wrapper.get('.canvas-node').trigger('keydown', { key: 'Delete' })
    await wrapper.get('.canvas-node').trigger('keydown', { key: 'Tab' })
    await wrapper.get('.canvas-node').trigger('keydown', { key: 'Tab', shiftKey: true })
    for (const key of ['+', '-', '*', '/', '|', '&']) {
      await wrapper.get('.canvas-node').trigger('keydown', { key })
    }
    expect(wrapper.emitted().remove).toEqual([[path]])
    expect(wrapper.emitted().deleteKey).toEqual([[{ path, key: 'Delete', operator: false }]])
    expect(wrapper.emitted().indent).toEqual([[path]])
    expect(wrapper.emitted().outdent).toEqual([[path]])
    expect(wrapper.emitted().operatorKey.map(args => args[0].key)).toEqual(['+', '-', '*', '/', '|', '&'])
  })

  test('输入框的运算符、删除和输入法按键不修改表达式结构，Esc 返回卡片', async () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      attachTo: document.body,
      props: { node: { kind: 'LITERAL', value: 'a+b', valueType: 'STRING' } },
      stubs: { 'el-input': ElInputStub }
    })
    const input = wrapper.get('input')
    for (const key of ['+', '-', '*', '/', '|', '&', 'Backspace', 'Delete', 'Tab']) {
      await input.trigger('keydown', { key })
    }
    await wrapper.get('.canvas-node').trigger('keydown', { key: '+', isComposing: true })
    await wrapper.get('.canvas-node').trigger('keydown', { key: '+', ctrlKey: true })
    expect(wrapper.emitted().operatorKey).toBeUndefined()
    expect(wrapper.emitted().remove).toBeUndefined()
    expect(wrapper.emitted().indent).toBeUndefined()
    await input.trigger('keydown', { key: 'Escape' })
    expect(document.activeElement).toBe(wrapper.get('.canvas-node').element)
    wrapper.unmount()
  })

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
      props: { node, path: [], selectedPath: [], collapsedPathKeys: ['$'] }
    })

    expect(wrapper.find('.canvas-children').exists()).toBe(false)
    expect(wrapper.find('.canvas-collapse__count').text()).toBe('2')
  })

  test('折叠按钮只发出当前路径且不改变表达式数据', async() => {
    const wrapper = shallowMount(ExpressionCanvas, {
      props: { node, path: [], selectedPath: [], collapsedPathKeys: [] }
    })
    await wrapper.find('.canvas-collapse').trigger('click')

    expect(wrapper.emitted().toggleCollapse[0][0]).toEqual([])
    expect(node.args).toHaveLength(2)
  })

  test('折叠按钮位于节点卡片之前', () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      props: { node, path: [], selectedPath: [] }
    })
    const children = wrapper.find('.canvas-node-row').element.children

    expect(children[0].classList.contains('canvas-collapse')).toBe(true)
    expect(children[1].classList.contains('canvas-node')).toBe(true)
  })

  test('选中阈值和路径时输入控件出现在画布节点内', async() => {
    focusManualInput.mockClear()
    const literal = shallowMount(ExpressionCanvas, {
      props: { node: { kind: 'LITERAL', value: '', valueType: 'NUMBER' }, selectedPath: [], path: [] },
      stubs: { 'el-input': ElInputStub, 'el-select': true, 'el-option': true }
    })
    await literal.vm.$nextTick()
    expect(literal.find('.canvas-inline-editor').exists()).toBe(true)
    expect(literal.find('.canvas-node .canvas-inline-editor').exists()).toBe(true)
    expect(literal.findComponent({ name: 'ElSelect' }).attributes('popper-class')).toBe('expression-editor-select-popper')
    expect(focusManualInput).toHaveBeenCalled()

    focusManualInput.mockClear()
    const path = shallowMount(ExpressionCanvas, {
      props: { node: { kind: 'PATH', value: '', resolved: false }, selectedPath: [], path: [] },
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
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: operation } })

    expect(wrapper.findAll('.canvas-child')).toHaveLength(3)
    expect(wrapper.findAll('.canvas-edge-operator code').map(item => item.text())).toEqual(['+', '*'])
  })

  test('根运算只展示子项，不再生成与公式预览重复的根卡片', () => {
    const operation = createOperationOperand([
      { operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' } },
      { operator: '+', operand: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' } }
    ])
    const wrapper = shallowMount(ExpressionCanvas, { props: { node: operation, path: [] } })

    expect(wrapper.find('.canvas-node-row').exists()).toBe(false)
    expect(wrapper.findAll('.canvas-child')).toHaveLength(2)
  })

  test('Tab、Shift+Tab 与调序快捷键发出当前路径', () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      props: {
        node: { kind: 'PATH', value: 'score', code: 'score' },
        path: ['terms', 1, 'operand'],
        selectedPath: ['terms', 1, 'operand']
      },
      stubs: { 'el-input': true }
    })
    const preventDefault = vi.fn()
    const stopPropagation = vi.fn()

    wrapper.vm.handleTab({ shiftKey: false, preventDefault, stopPropagation })
    wrapper.vm.handleTab({ shiftKey: true, preventDefault, stopPropagation })
    wrapper.vm.handleNodeKeydown({ key: 'ArrowUp', altKey: true, target: wrapper.get('.canvas-node').element, preventDefault, stopPropagation })

    expect(wrapper.emitted().indent[0][0]).toEqual(['terms', 1, 'operand'])
    expect(wrapper.emitted().outdent[0][0]).toEqual(['terms', 1, 'operand'])
    expect(wrapper.emitted().move[0][0]).toEqual({ path: ['terms', 1, 'operand'], offset: -1 })
  })

  test('原生拖拽通过路径载荷请求移动节点', () => {
    const wrapper = shallowMount(ExpressionCanvas, {
      props: { node: null, path: ['args', 1], selectedPath: [] }
    })
    const event = {
      dataTransfer: {
        getData: vi.fn(() => '["args",0]'),
        setData: vi.fn()
      }
    }

    wrapper.vm.onDrop(event)

    expect(wrapper.emitted().moveNode[0][0]).toEqual({ fromPath: ['args', 0], toPath: ['args', 1] })
  })
})
