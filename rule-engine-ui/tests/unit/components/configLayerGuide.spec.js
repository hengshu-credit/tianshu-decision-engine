import { mount } from '@vue/test-utils'
import ConfigLayerGuide from '@/components/common/ConfigLayerGuide.vue'

describe('配置步骤导航', () => {
  const items = [
    { key: 'scope', label: '作用范围', help: '已选择项目', status: 'READY' },
    { key: 'connection', label: '连接参数', help: '填写连接地址', status: 'PENDING' },
    { key: 'test', label: '连接测试', help: '校验连接', status: 'PENDING' },
  ]

  test('就绪数和当前步骤随真实配置状态更新', async () => {
    const wrapper = mount(ConfigLayerGuide, { props: { items, showProgress: true } })
    expect(wrapper.get('[role="status"]').text()).toBe('1 / 3 已就绪')
    expect(wrapper.get('[aria-current="step"]').text()).toContain('连接参数')
    await wrapper.setProps({ items: items.map(item => ({ ...item, status: 'READY' })) })
    expect(wrapper.get('[role="status"]').text()).toBe('3 / 3 已就绪')
    expect(wrapper.find('[aria-current="step"]').exists()).toBe(false)
  })

  test('可导航步骤通过按钮和键盘语义向页面传递原步骤', async () => {
    const wrapper = mount(ConfigLayerGuide, { props: { items, interactive: true, itemTestIdPrefix: 'database-config-check' } })
    const button = wrapper.get('[data-testid="database-config-check-connection"]')
    expect(button.element.tagName).toBe('BUTTON')
    expect(button.attributes('type')).toBe('button')
    await button.trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual([items[1], 1])
  })

  test('说明型流程不伪装成可点击操作，兼容原 detail 和 ready 数据', () => {
    const wrapper = mount(ConfigLayerGuide, { props: { items: [
      { label: '配置', detail: '选择来源', ready: true },
      { label: '测试', detail: '核对结果', ready: false },
    ], showProgress: true } })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toBe('1 / 2 已就绪')
    expect(wrapper.text()).toContain('核对结果')
  })
})
