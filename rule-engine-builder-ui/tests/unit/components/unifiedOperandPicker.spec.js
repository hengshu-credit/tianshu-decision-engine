import { shallowMount } from '@vue/test-utils'
import OperandPicker from '@/components/common/OperandPicker.vue'

const focusManualInput = jest.fn()

function mountPicker(propsData = {}) {
  return shallowMount(OperandPicker, {
    propsData: { value: null, vars: [], functions: [], allowedKinds: ['LITERAL', 'REFERENCE', 'FUNCTION', 'OPERATION'], ...propsData },
    stubs: {
      VarPicker: { name: 'VarPicker', template: '<div />' },
      ExpressionEditorDialog: { name: 'ExpressionEditorDialog', template: '<div />' },
      'el-input': {
        name: 'ElInput',
        template: '<input />',
        methods: { focus: focusManualInput }
      },
      'el-select': { name: 'ElSelect', template: '<select><slot /></select>' },
      'el-option': { name: 'ElOption', template: '<option />' },
      'el-tooltip': { template: '<span><slot /></span>' }
    }
  })
}

describe('统一 OperandPicker', () => {
  beforeEach(() => {
    focusManualInput.mockClear()
  })

  test('选择带参数函数时先进入表达式编辑器，应用后才更新外部值', () => {
    const fn = { id: 9, funcCode: 'max', paramsJson: '[{"name":"a","type":"NUMBER"},{"name":"b","type":"NUMBER"}]' }
    const wrapper = mountPicker({ functions: [fn] })

    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 9, functionCode: 'max', args: [] })
    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.editorVisible).toBe(true)
    expect(wrapper.vm.editorValue.args).toHaveLength(2)

    wrapper.vm.onEditorApply(wrapper.vm.editorValue)
    expect(wrapper.emitted().input[0][0].functionCode).toBe('max')
  })

  test('无参数函数和普通字段保持一次点击完成', () => {
    const wrapper = mountPicker({ functions: [{ id: 10, funcCode: 'currentDate', paramsJson: '[]' }] })
    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 10, functionCode: 'currentDate', args: [] })
    wrapper.vm.onQuickInput({ kind: 'REFERENCE', refId: 2, refType: 'VARIABLE', code: 'amount' })

    expect(wrapper.emitted().input).toHaveLength(2)
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('已有值点击公式按钮时以深拷贝打开，不直接改父值', () => {
    const source = { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    const wrapper = mountPicker({ value: source })
    wrapper.vm.openEditor()
    wrapper.vm.editorValue.value = '200'

    expect(source.value).toBe('100')
    expect(wrapper.vm.editorVisible).toBe(true)
  })

  test('手输阈值在当前选择框切换为输入并支持修改类型', async() => {
    const wrapper = mountPicker({ expectedType: 'NUMBER' })

    wrapper.vm.openManualInput('LITERAL')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.operand-manual-editor').exists()).toBe(true)
    expect(wrapper.vm.editorVisible).toBe(false)
    expect(wrapper.vm.manualOperand).toEqual({ kind: 'LITERAL', value: '', valueType: 'NUMBER' })
    expect(focusManualInput).toHaveBeenCalled()

    wrapper.vm.patchManualOperand({ valueType: 'BOOLEAN' })
    wrapper.vm.patchManualOperand({ value: 'true' })

    const emitted = wrapper.emitted().input
    expect(emitted[emitted.length - 1][0]).toEqual({ kind: 'LITERAL', value: 'true', valueType: 'BOOLEAN' })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('手输路径在当前选择框录入并按稳定 ID 解析', () => {
    const wrapper = mountPicker({
      allowedKinds: ['PATH', 'REFERENCE'],
      vars: [{
        varCode: 'request.age',
        varLabel: '客户年龄',
        varType: 'INTEGER',
        _varId: 8,
        _refType: 'DATA_OBJECT',
        _ref: { category: 'object' }
      }]
    })

    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('request.age')
    wrapper.vm.resolveManualPath()

    const emitted = wrapper.emitted().input
    expect(emitted[emitted.length - 1][0]).toMatchObject({
      kind: 'PATH',
      value: 'request.age',
      refId: 8,
      refType: 'DATA_OBJECT',
      resolved: true
    })
    expect(wrapper.vm.editorVisible).toBe(false)
  })
})
