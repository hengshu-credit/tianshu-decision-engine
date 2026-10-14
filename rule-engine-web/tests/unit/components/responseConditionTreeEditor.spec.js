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
  test('列表条件恢复旧 values 数组到阈值入口，保留前导零和成员边界', () => {
    const child = { type: 'condition', left: { kind: 'PATH', value: 'response.body.code' }, operator: 'in', values: ['0000', 'B0013', '含,逗号'] }
    const wrapper = shallowMount(ResponseConditionTreeEditor, { props: { operandMode: true, group: { type: 'group', operator: 'AND', children: [child] } } })
    const right = wrapper.findAllComponents(ApiValueEditor)[1]
    expect(right.props('value')).toEqual({ kind: 'LITERAL', valueType: 'LIST', value: '["0000","B0013","含,逗号"]' })
    expect(child.right).toBeUndefined()
    right.vm.$emit('update:value', { kind: 'LITERAL', valueType: 'LIST', value: '["0000"]' })
    expect(child).toMatchObject({ right: { value: '["0000"]' } })
    expect(child.values).toBeUndefined()
    wrapper.unmount()
  })
  test('条件树暴露后端支持的类型、区间、集合和布尔运算符', () => {
    const wrapper = shallowMount(ResponseConditionTreeEditor, { props: { group: { type: 'group', operator: 'AND', children: [] } } })
    const operators = wrapper.vm.operatorOptions.map(item => item.value)
    expect(operators).toEqual(expect.arrayContaining([
      'not_null', 'blank', 'not_blank', 'type_not', 'is_true', 'is_false',
      'between', 'not_between', 'contains_any', 'contains_all', 'has_key', 'not_has_key',
    ]))
    expect(wrapper.vm.operatorHasNoValue('is_true')).toBe(true)
    expect(wrapper.vm.isMultiValue('contains_all')).toBe(true)
    wrapper.unmount()
  })
})
