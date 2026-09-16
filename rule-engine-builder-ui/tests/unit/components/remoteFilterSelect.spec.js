import { shallowMount, flushPromises } from '@test-utils'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

const AutocompleteStub = {
  name: 'ElAutocomplete',
  props: ['modelValue', 'fetchSuggestions', 'teleported', 'highlightFirstItem'],
  methods: { close: vi.fn() },
  template: '<input :value="modelValue" />',
}

function mountSelect(props = {}) {
  return shallowMount(RemoteFilterSelect, {
    props: {
      fetchOptions: vi.fn().mockResolvedValue({ records: [], total: 0 }),
      allowFreeInput: true,
      ...props,
    },
    stubs: { 'el-autocomplete': AutocompleteStub },
  })
}

describe('RemoteFilterSelect', () => {
  test('候选层传送至 body，回车不默认选中首项', () => {
    const wrapper = mountSelect()
    const autocomplete = wrapper.findComponent(AutocompleteStub)
    expect(autocomplete.props('teleported')).toBe(true)
    expect(autocomplete.props('highlightFirstItem')).toBe(false)
    wrapper.unmount()
  })

  test('数据源及展示、筛选字段可配置，建议加载不修改条件', async () => {
    const fetchOptions = vi.fn().mockResolvedValue({
      data: { records: [{ id: 7, title: '风险规则' }], total: 1 },
    })
    const wrapper = mountSelect({ value: 'risk', fetchOptions, optionLabelKey: 'title', optionValueKey: 'id' })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('risk', callback)
    await flushPromises()
    expect(fetchOptions).toHaveBeenCalledWith({ query: 'risk', pageNum: 1, pageSize: 20 })
    expect(callback).toHaveBeenLastCalledWith([{ label: '风险规则', value: 7 }])
    expect(wrapper.emitted('update:value')).toBeUndefined()
    wrapper.unmount()
  })

  test('关闭候选与外部重置均丢弃未完成的旧响应', async () => {
    let resolve
    const wrapper = mountSelect({
      value: 'old',
      fetchOptions: vi.fn(() => new Promise(done => { resolve = done })),
    })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('old', callback)
    await wrapper.setProps({ value: '' })
    resolve({ records: ['old'], total: 1 })
    await flushPromises()
    expect(callback).toHaveBeenLastCalledWith([])
    expect(wrapper.vm.options).toEqual([])
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.emitted('update:value')).toBeUndefined()
    wrapper.unmount()
  })

  test('连续输入时旧请求不覆盖新候选，含防抖等待阶段', async () => {
    const pending = []
    const wrapper = mountSelect({
      value: 'r',
      fetchOptions: vi.fn(() => new Promise(resolve => pending.push(resolve))),
    })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('r', callback)
    wrapper.vm.updateValue('risk')
    pending[0]({ records: ['旧候选'], total: 1 })
    await flushPromises()
    expect(callback).toHaveBeenLastCalledWith([])
    await wrapper.setProps({ value: 'risk' })
    wrapper.vm.fetchSuggestions('risk', callback)
    pending[1]({ records: ['risk_main'], total: 1 })
    await flushPromises()
    expect(callback).toHaveBeenLastCalledWith([{ label: 'risk_main', value: 'risk_main' }])
    expect(wrapper.emitted('update:value')).toEqual([['risk']])
    wrapper.unmount()
  })

  test('重置后排队的防抖任务不再请求旧条件', async () => {
    const fetchOptions = vi.fn()
    const wrapper = mountSelect({ fetchOptions })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('old', callback)
    expect(fetchOptions).not.toHaveBeenCalled()
    expect(callback).toHaveBeenCalledWith([])
    wrapper.unmount()
  })

  test('分页追加去重，按服务端记录数判断结束，保留数值 0', async () => {
    const fetchOptions = vi.fn()
      .mockResolvedValueOnce({ records: [0, 'risk'], total: 4 })
      .mockResolvedValueOnce({ records: ['risk', 'other'], total: 4 })
    const wrapper = mountSelect({ fetchOptions, pageSize: 2 })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('', callback)
    await flushPromises()
    await wrapper.vm.loadOptions(false)
    expect(fetchOptions).toHaveBeenLastCalledWith({ query: '', pageNum: 2, pageSize: 2 })
    expect(callback).toHaveBeenLastCalledWith([
      { label: 0, value: 0 }, { label: 'risk', value: 'risk' }, { label: 'other', value: 'other' },
    ])
    expect(wrapper.vm.hasMore).toBe(false)
    await wrapper.vm.loadOptions(false)
    expect(fetchOptions).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  test('候选请求失败不丢失输入且可再次加载', async () => {
    const error = new Error('offline')
    const fetchOptions = vi.fn().mockRejectedValueOnce(error).mockResolvedValueOnce(['risk'])
    const wrapper = mountSelect({ value: 'risk', fetchOptions })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('risk', callback)
    await flushPromises()
    expect(wrapper.vm.loading).toBe(false)
    expect(callback).toHaveBeenLastCalledWith([])
    expect(wrapper.emitted('load-error')).toEqual([[error]])
    expect(wrapper.emitted('update:value')).toBeUndefined()
    wrapper.vm.fetchSuggestions('risk', callback)
    await flushPromises()
    expect(callback).toHaveBeenLastCalledWith([{ label: 'risk', value: 'risk' }])
    wrapper.unmount()
  })

  test('关键字候选支持多个字段，分页按原始记录计数', async () => {
    const fetchOptions = vi.fn()
      .mockResolvedValueOnce({ records: [{ code: 'risk_1', name: '风控' }], total: 2 })
      .mockResolvedValueOnce({ records: [{ code: 'risk_2', name: '风控' }], total: 2 })
    const wrapper = mountSelect({ fetchOptions, pageSize: 1, optionFields: ['code', 'name'] })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('', callback)
    await flushPromises()
    expect(wrapper.vm.hasMore).toBe(true)
    await wrapper.vm.loadOptions(false)
    expect(callback).toHaveBeenLastCalledWith([
      { label: 'risk_1', value: 'risk_1' }, { label: '风控', value: '风控' }, { label: 'risk_2', value: 'risk_2' },
    ])
    expect(wrapper.vm.hasMore).toBe(false)
    wrapper.unmount()
  })

  test('多字段关键字只展示匹配当前文字的候选', async () => {
    const wrapper = mountSelect({
      value: '风控',
      optionFields: ['code', 'name'],
      fetchOptions: vi.fn().mockResolvedValue({ records: [{ code: 'risk', name: '风控规则' }], total: 1 }),
    })
    const callback = vi.fn()
    wrapper.vm.fetchSuggestions('风控', callback)
    await flushPromises()
    expect(callback).toHaveBeenLastCalledWith([{ label: '风控规则', value: '风控规则' }])
    wrapper.unmount()
  })

  test('只允许选项模式的搜索不把文字当作关联 ID', async () => {
    const wrapper = mountSelect({ allowFreeInput: false, value: 7 })
    wrapper.vm.handleRemote('风险')
    await flushPromises()
    expect(wrapper.emitted('update:value')).toBeUndefined()
    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(true)
    wrapper.unmount()
  })
})
