import { config, flushPromises, mount } from '@test-utils'
import { ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElOption } from 'element-plus/es/components/select/index.mjs'
import { ElAutocomplete } from 'element-plus/es/components/autocomplete/index.mjs'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

const wrappers = []
function mountFilter(initialValue = '') {
  const fetchOptions = vi.fn().mockResolvedValue({ records: ['risk_main', 'risk_other'], total: 2 })
  const wrapper = mount({
    components: { RemoteFilterSelect },
    data: () => ({ value: initialValue, queries: [] }),
    methods: { fetchOptions, search() { this.queries.push(this.value) } },
    template: `<form @submit.prevent @keyup.enter="search">
      <remote-filter-select v-model:value="value" :fetch-options="fetchOptions" allow-free-input />
      <button type="button" @click="search">查询</button>
      <button type="button" class="reset" @click="value = ''">重置</button>
    </form>`,
  }, {
    attachTo: document.body,
    global: {
      components: { ElSelect, ElOption, ElAutocomplete },
      stubs: { ...Object.fromEntries(Object.keys(config.global.stubs).map(key => [key, false])), 'el-select': ElSelect, 'el-option': ElOption, 'el-autocomplete': ElAutocomplete },
    },
  })
  wrappers.push(wrapper)
  return { wrapper, input: wrapper.find('input'), fetchOptions }
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('筛选框真实输入交互', () => {
  test('输入后立即点查询，失焦及候选加载结束都保留原始条件', async () => {
    const { wrapper, input } = mountFilter()
    input.element.focus()
    await input.setValue('任意 Risk_% 文本')
    await input.trigger('blur')
    await wrapper.find('button').trigger('click')
    await new Promise(resolve => setTimeout(resolve, 350))
    await flushPromises()
    expect(wrapper.vm.queries).toEqual(['任意 Risk_% 文本'])
    expect(wrapper.vm.value).toBe('任意 Risk_% 文本')
    expect(input.element.value).toBe('任意 Risk_% 文本')
  })

  test('按回车只查询当前输入，不自动用第一个候选替换', async () => {
    const { wrapper, input } = mountFilter()
    input.element.focus()
    await input.setValue('risk')
    await new Promise(resolve => setTimeout(resolve, 350))
    await input.trigger('keydown', { key: 'Enter', code: 'Enter' })
    await input.trigger('keyup', { key: 'Enter', code: 'Enter' })
    await flushPromises()
    expect(wrapper.vm.queries).toEqual(['risk'])
    expect(wrapper.vm.value).toBe('risk')
    expect(input.element.value).toBe('risk')
  })

  test('重置后重新输入立即查询，仍然同步最新条件', async () => {
    const { wrapper } = mountFilter('old_value')
    await wrapper.find('.reset').trigger('click')
    const input = wrapper.find('input')
    await input.setValue('new_value')
    await wrapper.find('button').trigger('click')
    expect(wrapper.vm.queries).toEqual(['new_value'])
    expect(input.element.value).toBe('new_value')
  })

  test('聚焦加载候选，鼠标选中后仍可局部修改并回车查询', async () => {
    const { wrapper, input, fetchOptions } = mountFilter()
    await flushPromises()
    input.element.focus()
    await vi.waitFor(() => expect(fetchOptions).toHaveBeenCalledWith({ query: '', pageNum: 1, pageSize: 20 }))
    await flushPromises()
    const option = document.querySelector('.el-autocomplete-suggestion li')
    expect(option.textContent).toBe('risk_main')
    option.click()
    await flushPromises()
    expect(input.element.value).toBe('risk_main')
    expect(wrapper.vm.value).toBe('risk_main')
    await input.setValue('risk_')
    await input.trigger('keyup', { key: 'Enter' })
    expect(wrapper.vm.queries).toEqual(['risk_'])
  })

  test('回车查询后再次点击同一输入框可重新打开建议', async () => {
    const { wrapper, input } = mountFilter('risk')
    input.element.focus()
    await new Promise(resolve => setTimeout(resolve, 250))
    await input.trigger('keyup', { key: 'Enter' })
    expect(wrapper.vm.queries).toEqual(['risk'])
    await input.trigger('mousedown')
    await input.trigger('click')
    await flushPromises()
    expect(document.querySelector('.el-autocomplete').getAttribute('aria-expanded')).toBe('true')
  })

  test('中文输入法确认用的回车不触发页面查询', async () => {
    const { wrapper, input } = mountFilter()
    await input.trigger('compositionstart')
    input.element.value = '风控'
    await input.trigger('input')
    await input.trigger('keydown', { key: 'Enter', isComposing: true })
    await input.trigger('compositionend', { data: '风控' })
    await input.trigger('input')
    await input.trigger('keyup', { key: 'Enter' })
    expect(wrapper.vm.queries).toEqual([])
    await input.trigger('keydown', { key: 'Enter' })
    await input.trigger('keyup', { key: 'Enter' })
    expect(wrapper.vm.queries).toEqual(['风控'])
  })
})
