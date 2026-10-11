import { shallowMount } from '@test-utils'
import ResponseConditionTreeEditor from '@/components/common/ResponseConditionTreeEditor.vue'
import ApiValueEditor from '@/components/common/ApiValueEditor.vue'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'

describe('外数响应条件树', () => {
  test('旧版鉴权和响应条件用统一路径选择器，仍保存原有字符串协议', async () => {
    const child = { type: 'condition', path: 'body.code', operator: '==', value: 'OK' }
    const paths = [{ value: 'httpStatus', label: 'HTTP 状态码', type: 'INTEGER' }]
    const wrapper = shallowMount(ResponseConditionTreeEditor, { props: { group: { type: 'group', operator: 'AND', children: [child] }, pathOptions: paths } })
    const picker = wrapper.findComponent(ProtocolPathPicker)
    expect(picker.props('value')).toBe('body.code')
    expect(picker.props('paths')).toEqual(paths)
    picker.vm.$emit('update:value', 'headers.X-Status')
    await wrapper.vm.$nextTick()
    expect(child.path).toBe('headers.X-Status')
    expect(child.value).toBe('OK')
    expect(child.left).toBeUndefined()
    wrapper.unmount()
  })
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
