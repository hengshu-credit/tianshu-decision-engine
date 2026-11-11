import { mount } from '@test-utils'
import OperandValueDisplay from '@/components/common/OperandValueDisplay.vue'

describe('版本中的紧凑内容展示', () => {
  test('缺少历史名称时仍可识别精确引用 ID', () => {
    const wrapper = mount(OperandValueDisplay, { props: { compact: true, operand: { kind: 'REFERENCE', refId: '9007199254740993', refType: 'VARIABLE' } } })
    expect(wrapper.text()).toContain('引用 #9007199254740993')
    expect(wrapper.attributes('title')).toContain('VARIABLE #9007199254740993')
  })
  test('同名同编码只展示一次，固定版本和业务对象内容保留', async () => {
    const wrapper = mount(OperandValueDisplay, { props: { compact: true, operand: { kind: 'REFERENCE', refId: '1', label: 'age', code: 'age', versionMode: 'FIXED', version: 3 } } })
    expect(wrapper.text().match(/age/g)).toHaveLength(1)
    expect(wrapper.text()).toContain('3')
    const value = { id: 'business-id', createTime: '2026-01-01', zero: 0, flag: false }
    await wrapper.setProps({ operand: { kind: 'LITERAL', valueType: 'OBJECT', value } })
    expect(wrapper.text()).toBe(JSON.stringify(value))
  })
})
