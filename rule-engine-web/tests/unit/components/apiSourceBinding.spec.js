import { shallowMount, flushPromises } from '@test-utils'
import ApiSourceBinding from '@/components/common/ApiSourceBinding.vue'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'
import request from '@/api/request'

describe('API 入参覆盖与结果选择', () => {
  test('请求字段覆盖保留字段 ID 与取值引用 ID', async () => {
    request.get.mockResolvedValueOnce({ data: { bindingMode: 'FIELDS', apiName: '画像查询', requestFields: [{ id: 'phone', path: 'customer.mobile', location: 'JSON', overridable: true }], resultFields: [{ value: 'body.features.score', label: '评分', type: 'NUMBER' }] } })
    const wrapper = shallowMount(ApiSourceBinding, { props: { apiId: 7, resultPath: 'body.features.score', value: {} } })
    await flushPromises()
    expect(wrapper.vm.fields).toHaveLength(1)
    expect(wrapper.findComponent(ProtocolPathPicker).props('value')).toBe('body.features.score')
    wrapper.vm.set('phone', { kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993', code: 'phone' })
    expect(wrapper.emitted('update:value')[0][0].phone.refId).toBe('9007199254740993')
    expect(wrapper.emitted('update:inputOverrides')).toBeUndefined()
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
  test('开启覆盖时保留默认值 0 和 false', async () => {
    request.get.mockResolvedValueOnce({ data: { bindingMode: 'FIELDS', requestFields: [
      { id: 'zero', path: 'count', value: 0, overridable: true },
      { id: 'flag', path: 'enabled', value: false, overridable: true },
    ] } })
    const wrapper = shallowMount(ApiSourceBinding, { props: { apiId: 3 } })
    await flushPromises()
    wrapper.vm.toggle(wrapper.vm.fields[0], true)
    await wrapper.setProps({ value: { zero: 0 } })
    wrapper.vm.toggle(wrapper.vm.fields[1], true)
    const updates = wrapper.emitted('update:value')
    expect(updates.at(-1)[0]).toEqual({ zero: 0, flag: false })
    wrapper.unmount()
  })
})
