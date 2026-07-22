import { shallowMount } from '@test-utils'
import ExpressionNodeInspector from '@/components/expression/ExpressionNodeInspector.vue'
import { createLiteralOperand, createOperationOperand } from '@/utils/operand'

const literal = value => createLiteralOperand(value, 'NUMBER')

function mountInspector(node) {
  return shallowMount(ExpressionNodeInspector, {
    props: { node },
    stubs: ['el-input', 'el-select', 'el-option', 'el-button', 'el-radio-group', 'el-radio-button']
  })
}

describe('ExpressionNodeInspector', () => {
  test('阈值只在画布当前卡片编辑，检查器不再重复生成输入行', () => {
    const wrapper = mountInspector(createLiteralOperand('1', 'NUMBER'))

    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(false)
    expect(wrapper.find('.inline-edit-tip').text()).toContain('当前卡片')
  })

  test('函数参数支持添加和删减', async () => {
    const wrapper = shallowMount(ExpressionNodeInspector, {
      props: { node: { kind: 'FUNCTION', functionCode: 'max', args: [null, null] } },
      stubs: ['el-input', 'el-select', 'el-option', 'el-button', 'el-radio-group', 'el-radio-button']
    })

    wrapper.vm.removeArgument(0)
    expect(wrapper.emitted().input[0][0].args).toHaveLength(1)
    await wrapper.setProps({ node: wrapper.emitted().input[0][0] })
    wrapper.vm.addArgument()
    expect(wrapper.emitted().input[1][0].args).toHaveLength(2)
  })

  test('访问器和类型转换配置会发出新节点', () => {
    const wrapper = shallowMount(ExpressionNodeInspector, {
      props: { node: { kind: 'ACCESS', target: null, accessor: null, accessType: 'KEY' } },
      stubs: ['el-input', 'el-select', 'el-option', 'el-button', 'el-radio-group', 'el-radio-button']
    })
    wrapper.vm.patch({ accessType: 'INDEX' })
    expect(wrapper.emitted().input[0][0].accessType).toBe('INDEX')
  })

  test('名单查询展示组合含义并只配置正向匹配语义', () => {
    const wrapper = shallowMount(ExpressionNodeInspector, {
      props: {
        node: {
          kind: 'LIST_QUERY', listIds: [1], itemTypes: [],
          combinationMode: 'ALL_FIELDS_ALL_LISTS', matchMode: 'IN_LIST'
        },
        listOptions: [{ id: 1, listName: '黑名单' }]
      },
      stubs: ['el-input', 'el-select', 'el-option', 'el-button', 'el-radio-group', 'el-radio-button']
    })

    expect(wrapper.text()).toContain('限制最严格')
    expect(wrapper.vm.listMatchModes.map(item => item.value)).toEqual(['IN_LIST', 'CONTAINED_IN_LIST'])
    expect(wrapper.vm.listItemTypes.length).toBeGreaterThan(1)
  })

  test('运算检查器修改项间运算符并正确删除首项', async () => {
    const node = createOperationOperand([
      { operand: literal('1') },
      { operator: '+', operand: literal('2') },
      { operator: '*', operand: literal('3') }
    ])
    const wrapper = mountInspector(node)

    wrapper.vm.patchTermOperator(2, '-')
    expect(wrapper.emitted().input[0][0].terms[2].operator).toBe('-')

    await wrapper.setProps({ node: wrapper.emitted().input[0][0] })
    wrapper.vm.removeOperationTerm(0)
    expect(wrapper.emitted().input[1][0].terms).toHaveLength(2)
    expect(wrapper.emitted().input[1][0].terms[0].operator).toBeUndefined()
  })

  test('运算检查器追加带默认加号的空项', () => {
    const wrapper = mountInspector(createOperationOperand([
      { operand: literal('1') },
      { operator: '+', operand: literal('2') }
    ]))

    wrapper.vm.addOperationTerm()
    expect(wrapper.emitted().input[0][0].terms[2]).toEqual({ operator: '+', operand: null })
  })
})
