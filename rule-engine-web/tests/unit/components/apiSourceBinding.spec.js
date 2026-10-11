import { shallowMount, flushPromises } from '@test-utils'
import ApiSourceBinding from '@/components/common/ApiSourceBinding.vue'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'
import request from '@/api/request'

describe('API 入参覆盖与结果选择', () => {
  test('旧请求映射中的调用入参也可以通过字段选择器覆盖', async () => {
    request.get.mockResolvedValueOnce({ data: { bindingMode: 'INPUT', apiName: '画像查询', inputFields: [{ id: 'customer.mobile', path: 'customer.mobile', location: 'INPUT', overridable: true }], resultFields: [{ value: 'body.features.score', label: '评分', type: 'NUMBER' }] } })
    const wrapper = shallowMount(ApiSourceBinding, { props: { apiId: 7, resultPath: 'body.features.score', inputOverrides: {} } })
    await flushPromises()
    expect(wrapper.vm.fields).toHaveLength(1)
    expect(wrapper.findComponent(ProtocolPathPicker).props('value')).toBe('body.features.score')
    wrapper.vm.set('customer.mobile', { kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993', code: 'phone' })
    expect(wrapper.emitted('update:inputOverrides')[0][0]['customer.mobile'].refId).toBe('9007199254740993')
    expect(wrapper.emitted('update:value')).toBeUndefined()
    wrapper.unmount()
  })
  test('未开放参数仍显示原因，不能勾选；旧请求报错不会覆盖新接口的状态', async () => {
    let rejectFirst
    request.get.mockImplementationOnce(() => new Promise((resolve, reject) => { rejectFirst = reject }))
    request.get.mockResolvedValueOnce({ data: { bindingMode: 'FIELDS', requestFields: [{ id: 'locked', path: 'name', overridable: false }, { id: 'open', path: 'score', overridable: true }] } })
    const wrapper = shallowMount(ApiSourceBinding, { props: { apiId: 1 } })
    await wrapper.setProps({ apiId: 2 })
    await flushPromises()
    rejectFirst(new Error('旧接口加载失败'))
    await flushPromises()
    expect(wrapper.vm.fields).toHaveLength(2)
    expect(wrapper.vm.error).toBe('')
    wrapper.vm.toggle(wrapper.vm.fields[0], true)
    expect(wrapper.emitted('update:value')).toBeUndefined()
    wrapper.unmount()
  })
})
