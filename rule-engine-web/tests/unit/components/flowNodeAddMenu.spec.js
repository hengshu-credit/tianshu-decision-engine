import { shallowMount } from '@test-utils'
import FlowNodeAddMenu from '@/components/flow/FlowNodeAddMenu.vue'

const options = [
  { type: 'script-task', label: '执行动作', icon: 'Document', color: '#2F54EB' },
  { type: 'end-event', label: '结束', icon: 'Remove', color: '#ff4d4f' }
]

describe('FlowNodeAddMenu', () => {
  test('仅在 visible 时显示并使用画布相对坐标定位', async () => {
    const wrapper = shallowMount(FlowNodeAddMenu, {
      props: { visible: false, x: 120, y: 80, options }
    })

    expect(wrapper.find('.flow-node-add-menu').exists()).toBe(false)
    await wrapper.setProps({ visible: true })
    expect(wrapper.find('.flow-node-add-menu').attributes('style')).toContain('left: 120px')
    expect(wrapper.findAll('.flow-node-add-menu__item')).toHaveLength(2)
  })

  test('点击节点类型回传完整选项', async () => {
    const wrapper = shallowMount(FlowNodeAddMenu, {
      props: { visible: true, x: 0, y: 0, options }
    })

    await wrapper.findAll('.flow-node-add-menu__item').at(0).trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual([options[0]])
  })

  test('按 Esc 请求关闭菜单', () => {
    const wrapper = shallowMount(FlowNodeAddMenu, {
      props: { visible: true, x: 0, y: 0, options }
    })

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
