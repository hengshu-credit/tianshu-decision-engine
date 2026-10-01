import { shallowMount } from '@test-utils'
import ResponseConditionTreeEditor from '@/components/common/ResponseConditionTreeEditor.vue'
import ApiValueEditor from '@/components/common/ApiValueEditor.vue'

describe('外数响应条件树', () => {
  test('切换为路径缺失后清理历史右操作数且隐藏比较值', async () => {
    const child = { type: 'condition', left: { kind: 'PATH', value: 'response.body.score' }, operator: '==', right: { kind: 'LITERAL', value: '1' }, value: '1' }
    const wrapper = shallowMount(ResponseConditionTreeEditor, { props: { operandMode: true, group: { type: 'group', operator: 'AND', children: [child] } } })
    expect(wrapper.findAllComponents(ApiValueEditor)).toHaveLength(2)
    child.operator = 'missing'
    wrapper.vm.onOperatorChange(child)
    await wrapper.vm.$forceUpdate()
    expect(child.right).toBeUndefined()
    expect(child.value).toBeUndefined()
    expect(wrapper.findAllComponents(ApiValueEditor)).toHaveLength(1)
    wrapper.unmount()
  })
})
