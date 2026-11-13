import { mount } from '@vue/test-utils'
import fs from 'node:fs'
import path from 'node:path'
import ConfigLayerGuide from '@/components/common/ConfigLayerGuide.vue'
import { clearCurrentUser, setCurrentUser } from '@/security/permissionState'
import { restoreGuidance } from '@/utils/pageGuidance'

describe('配置步骤导航', () => {
  const items = [
    { key: 'scope', label: '作用范围', help: '已选择项目', status: 'READY' },
    { key: 'connection', label: '连接参数', help: '填写连接地址', status: 'PENDING' },
    { key: 'test', label: '连接测试', help: '校验连接', status: 'PENDING' },
  ]

  test('就绪数和当前步骤随真实配置状态更新', async () => {
    const wrapper = mount(ConfigLayerGuide, { props: { guideId: 'test.steps', items, showProgress: true } })
    expect(wrapper.get('[role="status"]').text()).toBe('1 / 3 已就绪')
    expect(wrapper.get('[aria-current="step"]').text()).toContain('连接参数')
    await wrapper.setProps({ items: items.map(item => ({ ...item, status: 'READY' })) })
    expect(wrapper.get('[role="status"]').text()).toBe('3 / 3 已就绪')
    expect(wrapper.find('[aria-current="step"]').exists()).toBe(false)
  })

  test('可导航步骤通过按钮和键盘语义向页面传递原步骤', async () => {
    const wrapper = mount(ConfigLayerGuide, { props: { guideId: 'test.steps', items, interactive: true, itemTestIdPrefix: 'database-config-check' } })
    const button = wrapper.get('[data-testid="database-config-check-connection"]')
    expect(button.element.tagName).toBe('BUTTON')
    expect(button.attributes('type')).toBe('button')
    await button.trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual([items[1], 1])
  })

  test('说明型流程不伪装成可点击操作，兼容原 detail 和 ready 数据', () => {
    const wrapper = mount(ConfigLayerGuide, { props: { guideId: 'test.steps', items: [
      { label: '配置', detail: '选择来源', ready: true },
      { label: '测试', detail: '核对结果', ready: false },
    ], showProgress: true } })
    expect(wrapper.find('.config-layer-guide__items button').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toBe('1 / 2 已就绪')
    expect(wrapper.text()).toContain('核对结果')
  })

  test('完成态使用主题语义色并允许自定义进度文案', () => {
    const wrapper = mount(ConfigLayerGuide, { props: {
      guideId: 'test.steps',
      items: [{ key: 'base', label: '基础信息', status: 'READY' }],
      showProgress: true,
      progressLabel: '已完成',
      readyStateLabel: '已完成',
    } })
    expect(wrapper.get('[role="status"]').text()).toBe('1 / 1 已完成')
    expect(wrapper.get('.config-layer-guide__state').text()).toBe('已完成')
    expect(wrapper.get('.config-layer-guide__item').classes()).toContain('is-ready')
  })

  test('关闭操作按账号隔离，切回原账号可恢复引导', async () => {
    setCurrentUser({ userId: 12, username: 'first' })
    const wrapper = mount(ConfigLayerGuide, { props: { guideId: 'test.steps', title: '配置步骤', items, interactive: true } })
    try {
      await wrapper.get('button[aria-label="关闭配置步骤"]').trigger('click')
      expect(wrapper.find('section').exists()).toBe(false)
      expect(wrapper.emitted('select')).toBeUndefined()
      setCurrentUser({ userId: 13, username: 'second' })
      await wrapper.vm.$nextTick()
      expect(wrapper.find('section').exists()).toBe(true)
      restoreGuidance()
      setCurrentUser({ userId: 12, username: 'renamed' })
      await wrapper.vm.$nextTick()
      expect(wrapper.find('section').exists()).toBe(false)
      restoreGuidance()
      await wrapper.vm.$nextTick()
      expect(wrapper.find('section').exists()).toBe(true)
    } finally {
      wrapper.unmount()
      clearCurrentUser()
    }
  })

  test('完成态圆标使用主题主色，夜间主题不会回退到固定成功绿', () => {
    const source = fs.readFileSync(path.resolve('src/components/common/ConfigLayerGuide.vue'), 'utf8')
    expect(source).toContain('var(--el-color-primary)')
    expect(source).toContain('var(--tianshu-brand-foreground)')
    expect(source).toContain('var(--tianshu-primary-text)')
    expect(source).not.toContain('var(--tianshu-status-success-solid)')
  })
})
