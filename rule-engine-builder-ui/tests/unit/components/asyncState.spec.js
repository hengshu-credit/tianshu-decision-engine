import { shallowMount } from '@vue/test-utils'
import AsyncState from '@/components/common/AsyncState.vue'

describe('AsyncState', () => {
  test('错误状态提供统一重试事件', async () => {
    const wrapper = shallowMount(AsyncState, {
      propsData: { error: '加载失败' },
      stubs: { 'el-button': true, 'el-empty': true }
    })
    wrapper.vm.retry()
    expect(wrapper.emitted().retry).toHaveLength(1)
  })

  test('只有正常状态才渲染业务内容', () => {
    const wrapper = shallowMount(AsyncState, {
      propsData: { loading: false, error: '', empty: false },
      slots: { default: '<span class="content">内容</span>' },
      stubs: { 'el-button': true, 'el-empty': true }
    })
    expect(wrapper.find('.content').exists()).toBe(true)
  })
})
