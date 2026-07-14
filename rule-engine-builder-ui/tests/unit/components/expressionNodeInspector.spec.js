import { shallowMount } from '@vue/test-utils'
import ExpressionNodeInspector from '@/components/expression/ExpressionNodeInspector.vue'

describe('ExpressionNodeInspector', () => {
  test('函数参数支持添加和删减', async () => {
    const wrapper = shallowMount(ExpressionNodeInspector, {
      propsData: { node: { kind: 'FUNCTION', functionCode: 'max', args: [null, null] } },
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
      propsData: { node: { kind: 'ACCESS', target: null, accessor: null, accessType: 'KEY' } },
      stubs: ['el-input', 'el-select', 'el-option', 'el-button', 'el-radio-group', 'el-radio-button']
    })
    wrapper.vm.patch({ accessType: 'INDEX' })
    expect(wrapper.emitted().input[0][0].accessType).toBe('INDEX')
  })
})
