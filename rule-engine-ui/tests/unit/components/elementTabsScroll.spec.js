import { mount } from '@vue/test-utils'
import { h, nextTick } from 'vue'
import { ElTabs, ElTabPane } from 'element-plus/es/components/tabs/index.mjs'

test('溢出页签以被动 touchstart 记录手势起点', async () => {
  const originalRect = Element.prototype.getBoundingClientRect
  const rectSpy = vi.spyOn(Element.prototype, 'getBoundingClientRect')
    .mockImplementation(function () {
      const rect = originalRect.call(this)
      if (this.classList.contains('el-tabs__nav')) return { ...rect, width: 600 }
      if (this.classList.contains('el-tabs__nav-scroll')) return { ...rect, width: 200 }
      return rect
    })
  const addListener = vi.spyOn(Element.prototype, 'addEventListener')
  const wrapper = mount(ElTabs, {
    global: { stubs: { ElTabPane: false, 'el-tab-pane': false } },
    props: { modelValue: 'first' },
    slots: { default: () => [
      h(ElTabPane, { name: 'first', label: '第一个页签' }),
      h(ElTabPane, { name: 'second', label: '第二个页签' }),
    ] },
  })
  try {
    await nextTick()
    const nav = wrapper.findComponent({ name: 'ElTabNav' })
    nav.vm.$forceUpdate()
    await vi.waitFor(() => {
      expect(wrapper.find('.el-tabs__nav-wrap.is-scrollable').exists()).toBe(true)
    })
    const tablist = wrapper.find('[role="tablist"]').element
    const startCalls = addListener.mock.calls.filter((args, index) =>
      addListener.mock.contexts[index] === tablist && args[0] === 'touchstart'
    )
    expect(startCalls).toHaveLength(1)
    expect(startCalls[0][2]).toEqual(expect.objectContaining({ passive: true }))
    for (const type of ['wheel', 'touchmove']) {
      const call = addListener.mock.calls.find((args, index) =>
        addListener.mock.contexts[index] === tablist && args[0] === type
      )
      expect(call[2]).toEqual(expect.objectContaining({ passive: false }))
    }
    await wrapper.find('#tab-second').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['second']])
  } finally {
    wrapper.unmount()
    addListener.mockRestore()
    rectSpy.mockRestore()
  }
})
