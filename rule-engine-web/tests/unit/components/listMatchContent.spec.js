import { mount } from '@test-utils'
import ListMatchContent from '@/components/common/ListMatchContent.vue'

describe('名单条件的匹配内容', () => {
  test.each([
    ['VARIABLE', '变量'], ['CONSTANT', '常量'], ['DATA_OBJECT', '对象'],
    ['DATA_OBJECT_ROOT', '对象'], ['MODEL_OUTPUT', '模型'], ['MODEL', '模型'],
  ])('%s 展示来源、数据类型、名称、编码、版本和实际值', (refType, source) => {
    const wrapper = mount(ListMatchContent, { props: {
      operand: { kind: 'REFERENCE', refType, refId: '101', valueType: 'STRING', label: '客户手机号', code: 'customer.Mobile', versionMode: 'FIXED', version: 3 },
      actualValue: 'test-mobile',
    } })
    expect(wrapper.find('.content-ref__source').text()).toBe(source)
    expect(wrapper.find('.content-ref__type').attributes('title')).toBe('字符串 · STRING')
    expect(wrapper.find('.content-ref__name').text()).toBe('客户手机号')
    expect(wrapper.find('.content-ref__code').text()).toBe('customer.Mobile')
    expect(wrapper.find('.content-ref__version').text()).toBe('固定 v3')
    expect(wrapper.find('.list-match-value').text()).toBe('test-mobile')
    wrapper.unmount()
  })

  test.each([0, false, '', null, { a: '<script>hello</script>' }])('阈值只展示来源与本次实际值：%s', actualValue => {
    const wrapper = mount(ListMatchContent, { props: {
      operand: { kind: 'LITERAL', label: '不应显示', valueType: 'NUMBER', versionMode: 'FIXED', version: 1, value: '不能替代实际值' }, actualValue,
    } })
    expect(wrapper.find('.content-ref__source').text()).toBe('阈值')
    expect(wrapper.find('.content-ref__type').text()).toBe('N')
    expect(wrapper.find('.content-ref__name').exists()).toBe(false)
    expect(wrapper.find('.content-ref__code').exists()).toBe(false)
    expect(wrapper.find('.content-ref__version').exists()).toBe(false)
    expect(wrapper.find('.content-ref__value').text()).toBe(actualValue === null ? '空值（null）'
      : actualValue === '' ? '空字符串' : typeof actualValue === 'object' ? JSON.stringify(actualValue) : String(actualValue))
    expect(wrapper.find('script').exists()).toBe(false)
    wrapper.unmount()
  })

  test('最新绑定不把选择时保存的版本号当成执行版本', () => {
    const wrapper = mount(ListMatchContent, { props: {
      operand: { kind: 'REFERENCE', refType: 'VARIABLE', valueType: 'NUMBER', label: '年龄', code: 'Age', versionMode: 'LATEST', version: 2 }, actualValue: 0,
    } })
    expect(wrapper.find('.content-ref__version').text()).toBe('跟随最新')
    expect(wrapper.find('.content-ref__version').attributes('title')).toContain('未记录解析后的版本号')
    expect(wrapper.find('.content-ref__type').attributes('title')).toBe('数值 · NUMBER')
    expect(wrapper.find('.list-match-value').text()).toBe('0')
    wrapper.unmount()
  })

  test('历史缺少来源的日志不猜成阈值，不从当前目录补历史信息', () => {
    const wrapper = mount(ListMatchContent, { props: { actualValue: false } })
    expect(wrapper.text()).toContain('来源未记录')
    expect(wrapper.find('.list-match-value').text()).toBe('false')
    expect(wrapper.find('.content-ref__version').exists()).toBe(false)
    wrapper.unmount()
  })
})
