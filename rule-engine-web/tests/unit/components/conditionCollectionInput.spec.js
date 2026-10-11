import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'

const OperandPicker = (await vi.importActual('../../../src/components/common/OperandPicker.vue')).default
const textItem = value => ({ kind: 'LITERAL', valueType: 'STRING', value })
const numberItem = value => ({ kind: 'CAST', targetType: 'NUMBER', operand: textItem(value) })
const list = (...items) => ({ kind: 'ARRAY', valueType: 'LIST', items })

function mountCollection(value, expectedType = 'STRING') {
  return shallowMount(OperandPicker, {
    props: { value, expectedType, conditionOperator: 'in' },
    stubs: {
      ConditionCollectionInput: false,
      'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
      'el-select': { props: ['modelValue'], template: '<select><slot /></select>' },
      'el-button': { template: '<button><slot /></button>' },
    },
  })
}

describe('条件集合逐项输入', () => {
  test('文本中的逗号、首尾空格和空串作为独立成员原样保留', async () => {
    const wrapper = mountCollection(list(textItem('A,B'), textItem(' padded '), textItem('')))
    try {
      expect(wrapper.findAll('.collection-member')).toHaveLength(3)
      expect(wrapper.findComponent('[data-testid="collection-value-0"]').props('modelValue')).toBe('A,B')
      expect(wrapper.findComponent('[data-testid="collection-value-1"]').props('modelValue')).toBe(' padded ')
      expect(wrapper.find('.collection-member').text()).not.toContain('QL')
      expect(wrapper.text()).toContain('不会因此命中集合')
      wrapper.findComponent('[data-testid="collection-value-0"]').vm.$emit('update:modelValue', ' comma, value ')
      await nextTick()
      expect(wrapper.emitted().input.at(-1)[0].items.map(item => item.value)).toEqual([' comma, value ', ' padded ', ''])
    } finally { wrapper.unmount() }
  })

  test('精确数字编辑保持十进制文本与CAST类型，不转JS Number', async () => {
    const wrapper = mountCollection(list(numberItem('9007199254740993.123456789')), 'NUMBER')
    try {
      wrapper.findComponent('[data-testid="collection-value-0"]').vm.$emit('update:modelValue', '9007199254740993.123456790')
      await nextTick()
      expect(wrapper.emitted().input.at(-1)[0].items[0]).toEqual(numberItem('9007199254740993.123456790'))
    } finally { wrapper.unmount() }
  })

  test('旧CSV打开不迁移，明确提示影响并确认后才替换', async () => {
    const old = { kind: 'LITERAL', valueType: 'STRING', value: '1, "A,B", true' }
    const wrapper = mountCollection(old)
    try {
      expect(wrapper.emitted().input).toBeUndefined()
      expect(wrapper.find('[data-testid="collection-legacy"]').text()).toContain(old.value)
      await wrapper.find('[data-testid="collection-replace-legacy"]').trigger('click')
      expect(wrapper.find('[data-testid="collection-replace-impact"]').text()).toContain('不会自动拆分')
      expect(wrapper.emitted().input).toBeUndefined()
      await wrapper.find('[data-testid="collection-confirm-replace"]').trigger('click')
      expect(wrapper.emitted().input.at(-1)[0]).toEqual(list())
      expect(old.value).toBe('1, "A,B", true')
    } finally { wrapper.unmount() }
  })

  test('原字段引用和旧数字字面量不被其它项编辑改写，替换引用明确提示影响', async () => {
    const reference = { kind: 'REFERENCE', refId: 19, refType: 'VARIABLE', code: 'allowed', label: '允许值字段' }
    const rawNumber = { kind: 'LITERAL', valueType: 'NUMBER', value: '1.234567890123456789' }
    const wrapper = mountCollection(list(reference, rawNumber, textItem('before')))
    try {
      expect(wrapper.findAll('.collection-original')).toHaveLength(2)
      expect(wrapper.text()).toContain('允许值字段')
      expect(wrapper.emitted().input).toBeUndefined()
      wrapper.findComponent('[data-testid="collection-value-2"]').vm.$emit('update:modelValue', 'after')
      await nextTick()
      expect(wrapper.emitted().input.at(-1)[0].items.slice(0, 2)).toEqual([reference, rawNumber])
      await wrapper.find('[data-testid="collection-replace-0"]').trigger('click')
      expect(wrapper.find('[data-testid="collection-replace-impact"]').text()).toContain('移除原字段或计算引用')
    } finally { wrapper.unmount() }
  })

  test('空集合和非法精确数字显示错误，添加成员不用手写分隔符', async () => {
    const wrapper = mountCollection(list(), 'NUMBER')
    try {
      expect(wrapper.find('[role="alert"]').text()).toContain('至少')
      await wrapper.find('[data-testid="collection-add-member"]').trigger('click')
      const value = wrapper.emitted().input.at(-1)[0]
      expect(value.items).toEqual([numberItem('')])
      await wrapper.setProps({ value })
      expect(wrapper.find('[role="alert"]').text()).toContain('数字')
    } finally { wrapper.unmount() }
  })
})
