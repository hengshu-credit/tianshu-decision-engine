import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

const OperandPicker = (await vi.importActual('../../../src/components/common/OperandPicker.vue')).default

function mountRange(value = '18,60', valueType = 'NUMBER') {
  return shallowMount(OperandPicker, {
    props: { value: { kind: 'LITERAL', value, valueType }, expectedType: valueType, rangeOperator: 'between' },
    stubs: {
      ConditionRangeInput: false,
      'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
    },
  })
}

describe('区间双边界输入', () => {
  test('旧逗号值分为两个输入，修改后保持原Operand存储且不自动写QL', async () => {
    const wrapper = mountRange()
    try {
      expect(wrapper.find('[data-testid="condition-range-lower"]').exists()).toBe(true)
      const lower = wrapper.findComponent('[data-testid="condition-range-lower"]')
      const upper = wrapper.findComponent('[data-testid="condition-range-upper"]')
      expect(lower.props('modelValue')).toBe('18')
      expect(upper.props('modelValue')).toBe('60')
      lower.vm.$emit('update:modelValue', '21')
      await nextTick()
      expect(wrapper.emitted().input.at(-1)[0]).toMatchObject({ kind: 'LITERAL', value: '21,60', valueType: 'NUMBER' })
      expect(wrapper.find('.expression-button').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('残缺区间显示具体错误但仍能继续输入，精确小数按字符串原样保留', async () => {
    const wrapper = mountRange('9007199254740993.123456789,')
    try {
      expect(wrapper.find('[role="alert"]').text()).toContain('上界')
      wrapper.findComponent('[data-testid="condition-range-upper"]').vm.$emit('update:modelValue', '9007199254740993.123456790')
      await nextTick()
      const value = wrapper.emitted().input.at(-1)[0]
      expect(value.value).toBe('9007199254740993.123456789,9007199254740993.123456790')
      await wrapper.setProps({ value })
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('切换旧规则或日期值后输入同步，不携带上次区间', async () => {
    const wrapper = mountRange()
    try {
      await wrapper.setProps({ value: { kind: 'LITERAL', value: '2026/01/01,2026/12/31', valueType: 'DATE' }, expectedType: 'DATE' })
      expect(wrapper.findComponent('[data-testid="condition-range-lower"]').props('modelValue')).toBe('2026/01/01')
      expect(wrapper.findComponent('[data-testid="condition-range-upper"]').props('modelValue')).toBe('2026/12/31')
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })
})
