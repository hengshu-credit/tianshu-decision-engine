import { shallowMount } from '@vue/test-utils'
import Vue from 'vue'

const OperandPicker = jest.requireActual('../../../src/components/common/VarPicker.vue').default

function mountPicker(propsData = {}) {
  return shallowMount(OperandPicker, {
    propsData: {
      vars: [],
      operandMode: true,
      allowedKinds: ['LITERAL', 'PATH', 'REFERENCE'],
      ...propsData
    },
    stubs: {
      'el-popover': { template: '<div><slot /><slot name="reference" /></div>' },
      'el-input': { template: '<input />', props: ['value', 'readonly'] },
      'el-button': { template: '<button><slot /></button>' },
      'el-tooltip': { template: '<span><slot /></span>' },
      'el-pagination': { template: '<div />' },
      'el-radio-group': { template: '<div><slot /></div>' },
      'el-radio-button': { template: '<button><slot /></button>' },
      'el-alert': { template: '<div><slot /></div>' },
      'el-tag': { template: '<span><slot /></span>' }
    }
  })
}

const references = [
  {
    varCode: 'amount',
    varLabel: '申请金额',
    varType: 'NUMBER',
    _varId: 1,
    _refType: 'VARIABLE',
    _ref: { category: 'standalone' }
  },
  {
    varCode: 'request.age',
    varLabel: '客户年龄',
    varType: 'INTEGER',
    _varId: 8,
    _refType: 'DATA_OBJECT',
    _ref: { category: 'object', objectCode: 'request', objectLabel: '请求对象' }
  },
  {
    varCode: 'MAX_SCORE',
    varLabel: '最高分',
    varType: 'NUMBER',
    _varId: 3,
    _refType: 'CONSTANT',
    _ref: { category: 'constant' }
  }
]

describe('OperandPicker', () => {
  test('手动输入分类始终位于最前方', async () => {
    const wrapper = mountPicker({ vars: references })
    await Vue.nextTick()

    expect(wrapper.vm.categoryList.map(item => item.key)).toEqual([
      'manual', 'standalone', 'constant', 'object'
    ])
    expect(wrapper.vm.activeCategory).toBe('manual')
  })

  test('手输阈值发出显式 LITERAL 操作数', () => {
    const wrapper = mountPicker({ vars: references, expectedType: 'NUMBER' })
    wrapper.vm.selectManualKind('LITERAL')
    wrapper.vm.manualValue = '600'
    wrapper.vm.confirmManual()

    expect(wrapper.emitted().input[0][0]).toEqual({
      kind: 'LITERAL', value: '600', valueType: 'NUMBER'
    })
  })

  test('手输路径唯一命中后发出带 ID 的 PATH', () => {
    const wrapper = mountPicker({ vars: references })
    wrapper.vm.selectManualKind('PATH')
    wrapper.vm.manualValue = 'request.age'
    wrapper.vm.confirmManual()

    expect(wrapper.emitted().input[0][0]).toMatchObject({
      kind: 'PATH',
      value: 'request.age',
      refId: 8,
      refType: 'DATA_OBJECT',
      resolved: true
    })
  })

  test('多项命中时不能确认并展示候选项', () => {
    const wrapper = mountPicker({
      vars: [
        { ...references[0], varCode: 'score' },
        { ...references[1], varCode: 'score' }
      ]
    })
    wrapper.vm.selectManualKind('PATH')
    wrapper.vm.manualValue = 'score'
    wrapper.vm.confirmManual()

    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.pathCandidates).toHaveLength(2)
  })

  test('从变量分类选择时发出 REFERENCE 操作数', () => {
    const wrapper = mountPicker({ vars: references })
    wrapper.vm.onItemClick(references[0])

    expect(wrapper.emitted().input[0][0]).toMatchObject({
      kind: 'REFERENCE',
      code: 'amount',
      refId: 1,
      refType: 'VARIABLE'
    })
  })

  test('只读触发器聚焦或点击时都会打开面板', () => {
    const wrapper = mountPicker({ vars: references })
    wrapper.vm.popoverVisible = false
    wrapper.vm.onInputFocus()
    expect(wrapper.vm.popoverVisible).toBe(true)
    wrapper.vm.popoverVisible = false
    wrapper.vm.onInputClick()
    expect(wrapper.vm.popoverVisible).toBe(true)
  })
})
