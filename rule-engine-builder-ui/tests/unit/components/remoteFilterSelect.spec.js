import { mount } from '@test-utils'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

const ElSelectStub = {
  name: 'ElSelect',
  props: {
    teleported: { type: Boolean, default: true }
  },
  mounted() {
    this.popperElm = this.$refs.popper
  },
  template: `
    <div class="el-select-stub">
      <input class="filter-input" readonly />
      <div ref="popper" class="el-select-dropdown">
        <button class="dropdown-option">选项</button>
      </div>
    </div>
  `
}

function mountSelect(propsData = {}) {
  return mount(RemoteFilterSelect, {
    props: {
      fetchOptions: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
      ...propsData
    },
    stubs: {
      'el-select': ElSelectStub,
      'el-option': true
    },
    attachTo: document.body
  })
}

describe('RemoteFilterSelect', () => {
  test('自由输入按 Enter 时先同步当前值再让父级查询', async () => {
    const Parent = {
      components: { RemoteFilterSelect },
      data() {
        return {
          filterValue: '',
          queriedValue: null
        }
      },
      methods: {
        fetchOptions() {
          return Promise.resolve({ data: { records: [], total: 0 } })
        },
        handleQuery() {
          this.queriedValue = this.filterValue
        }
      },
      template: `
        <form @keyup.enter="handleQuery">
          <remote-filter-select
            v-model:value="filterValue"
            :fetch-options="fetchOptions"
            allow-free-input
          />
        </form>
      `
    }
    const wrapper = mount(Parent, {
      stubs: {
        'el-select': ElSelectStub,
        'el-option': true
      }
    })
    const input = wrapper.find('.filter-input')

    await input.setValue('NO_MATCH_ENTER_RULE')
    await input.trigger('keyup', { key: 'Enter', keyCode: 13 })

    expect(wrapper.vm.filterValue).toBe('NO_MATCH_ENTER_RULE')
    expect(wrapper.vm.queriedValue).toBe('NO_MATCH_ENTER_RULE')
    wrapper.unmount()
  })

  test('挂载后保持原生筛选输入框可直接输入', async () => {
    const wrapper = mountSelect()
    const input = wrapper.find('.filter-input').element
    const popper = wrapper.find('.el-select-dropdown').element

    await wrapper.vm.$nextTick()
    expect(input.hasAttribute('readonly')).toBe(false)
    expect(popper.hasAttribute('inert')).toBe(true)
    expect(popper.getAttribute('aria-hidden')).toBe('true')

    input.setAttribute('readonly', 'readonly')
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(input.hasAttribute('readonly')).toBe(false)
    wrapper.unmount()
  })

  test('关闭下拉时隔离隐藏选项并仅保留已选项', async () => {
    const wrapper = mountSelect({ value: 'selected' })
    const input = wrapper.find('.filter-input').element
    const popper = wrapper.find('.el-select-dropdown').element
    await wrapper.vm.$nextTick()
    popper.setAttribute('inert', '')
    popper.setAttribute('aria-hidden', 'true')

    wrapper.vm.handleVisibleChange(true)
    await wrapper.vm.$nextTick()
    expect(popper.hasAttribute('inert')).toBe(false)
    expect(popper.hasAttribute('aria-hidden')).toBe(false)

    wrapper.vm.options = [
      { label: '已选', value: 'selected' },
      { label: '其他', value: 'other' }
    ]
    wrapper.find('.dropdown-option').element.focus()
    wrapper.vm.handleVisibleChange(false)
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(input)
    expect(popper.hasAttribute('inert')).toBe(true)
    expect(popper.getAttribute('aria-hidden')).toBe('true')
    expect(wrapper.vm.options).toEqual([{ label: '已选', value: 'selected' }])
    wrapper.unmount()
  })

  test('无已选值关闭下拉时清空远程选项且下拉不挂到 body', async () => {
    const wrapper = mountSelect()
    wrapper.vm.options = [{ label: '其他', value: 'other' }]

    wrapper.vm.handleVisibleChange(false)
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.options).toEqual([])
    expect(wrapper.findComponent(ElSelectStub).props('teleported')).toBe(false)
    wrapper.unmount()
  })

  test('关闭后忽略尚未返回的远程选项', async () => {
    let resolveFetch
    const fetchOptions = vi.fn(() => new Promise(resolve => { resolveFetch = resolve }))
    const wrapper = mountSelect({ fetchOptions })

    wrapper.vm.handleVisibleChange(true)
    wrapper.vm.handleVisibleChange(false)
    resolveFetch({ data: { records: [{ label: '延迟选项', value: 'late' }], total: 1 } })
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(wrapper.vm.options).toHaveLength(0)
    wrapper.unmount()
  })
})
