import { mount } from '@test-utils'
import VariableSourceSelector from '@/views/variable/components/VariableSourceSelector.vue'

describe('VariableSourceSelector', () => {
  test('以业务语言展示七种取值方式和当前范围可用数量', () => {
    const wrapper = mount(VariableSourceSelector, {
      props: {
        modelValue: 'INPUT',
        counts: { API: 2, DB: 1, LIST: 3 },
      },
    })

    expect(wrapper.attributes('role')).toBe('radiogroup')
    expect(wrapper.findAll('button')).toHaveLength(7)
    expect(wrapper.text()).toContain('衍生变量')
    expect(wrapper.text()).toContain('请求输入')
    expect(wrapper.text()).toContain('外数接口')
    expect(wrapper.text()).toContain('2 个可用')
    expect(wrapper.text()).toContain('数据库查询')
    expect(wrapper.text()).toContain('名单匹配')
  })

  test('点击卡片同时更新双向值并通知来源变化', async () => {
    const wrapper = mount(VariableSourceSelector, {
      props: { modelValue: 'INPUT' },
    })

    await wrapper.findAll('button')[1].trigger('click')

    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['API'])
    expect(wrapper.emitted('change')[0]).toEqual(['API'])
  })

  test('常量创建锁定后不能切换到其他取值方式', async () => {
    const wrapper = mount(VariableSourceSelector, {
      props: { modelValue: 'CONSTANT', disabled: true },
    })

    await wrapper.findAll('button')[0].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
