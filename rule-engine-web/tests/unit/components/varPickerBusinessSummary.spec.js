import { shallowMount } from '@test-utils'
import { compileOperand } from '@/utils/operand'

const VarPicker = (await vi.importActual('../../../src/components/common/VarPicker.vue')).default
const ref = { kind: 'REFERENCE', refId: 19, refType: 'VARIABLE', code: 'amount', label: '申请金额', valueType: 'NUMBER' }
const literal = value => ({ kind: 'LITERAL', value, valueType: 'STRING' })

test.each([
  [{ kind: 'CAST', targetType: 'NUMBER', operand: literal('9007199254740993.123456789') }, '转为数值（"9007199254740993.123456789"）', 'toNumberValue'],
  [{ kind: 'ACCESS', accessType: 'KEY', target: ref, accessor: literal('A,B') }, '字典取值(申请金额 amount, "A,B")', 'objGet'],
  [{ kind: 'FUNCTION', functionId: 28, functionCode: 'factor', label: '客户因子', args: [ref] }, '客户因子 factor(申请金额 amount)', null],
  [{ kind: 'ARRAY', valueType: 'LIST', items: [literal('A,B'), literal(''), { kind: 'CAST', targetType: 'NUMBER', operand: literal('9007199254740993') }] }, '["A,B", "", 转为数值（"9007199254740993"）]', 'toNumberValue'],
  [{ kind: 'OPERATION', terms: [{ operand: ref }, { operator: '+', operand: { kind: 'OPERATION', terms: [{ operand: ref }, { operator: '*', operand: { kind: 'LITERAL', value: '0.25', valueType: 'NUMBER' } }] } }] }, '申请金额 amount + (申请金额 amount * 0.25)', null],
  [{ kind: 'LIST_QUERY', listIds: ['9007199254740993'], itemTypes: ['MOBILE'], combinationMode: 'ALL_FIELDS_ANY_LIST', matchMode: 'IN_LIST' }, '全部内容各自命中任一名单', 'listQuery('],
  [{ kind: 'CUSTOM_QL', value: 'unregisteredRisk(amount)' }, '自定义表达式（请查看原配置）', 'unregisteredRisk'],
])('折叠表达式按已知结构解释并保留原值及ID：%j', (value, expected, forbidden) => {
  const original = JSON.stringify(value), script = compileOperand(value)
  const wrapper = shallowMount(VarPicker, {
    props: { operandMode: true, value },
    stubs: {
      'el-popover': { template: '<div><slot name="reference" /></div>' },
      'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
    },
  })
  try {
    const input = wrapper.find('.vp-reference')
    expect(input.element.value).toContain(expected)
    if (forbidden) expect(input.element.value).not.toContain(forbidden)
    expect(JSON.stringify(value)).toBe(original)
    expect(compileOperand(value)).toBe(script)
    expect(wrapper.emitted('input')).toBeUndefined()
  } finally { wrapper.unmount() }
})

test('普通常量折叠仍展示原名称编码和零值', () => {
  const value = { ...ref, refType: 'CONSTANT', constantValue: 0 }
  expect(VarPicker.computed.referenceInputValue.call({ operandMode: true, value })).toBe('申请金额 amount = 0')
})
