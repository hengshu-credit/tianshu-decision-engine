import { mount } from '@test-utils'
import DashboardSection from '@/components/dashboard/DashboardSection.vue'

describe('DashboardSection', () => {
  test('首次请求和刷新期间一直展示真实内容，错误仍可重试', async () => {
    const wrapper = mount(DashboardSection, {
      props: { title: '进件与决策', loading: true },
      slots: { default: '<div data-testid="content">进件数</div>' }
    })
    expect(wrapper.get('[data-testid="content"]').isVisible()).toBe(true)
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).not.toContain('正在加载')
    await wrapper.setProps({ loading: false })
    expect(wrapper.get('[data-testid="content"]').isVisible()).toBe(true)
    await wrapper.setProps({ loading: true })
    expect(wrapper.get('[data-testid="content"]').isVisible()).toBe(true)
    await wrapper.setProps({ loading: false, error: '统计暂不可用' })
    expect(wrapper.get('el-alert-stub').attributes('title')).toBe('统计暂不可用')
    await wrapper.get('el-button-stub').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    wrapper.unmount()
  })
})
