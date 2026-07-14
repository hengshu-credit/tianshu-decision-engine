import { shallowMount } from '@vue/test-utils'
import OperandPicker from '@/components/common/OperandPicker.vue'

function mountPicker(propsData = {}) {
  return shallowMount(OperandPicker, {
    propsData: { value: null, vars: [], functions: [], allowedKinds: ['LITERAL', 'REFERENCE', 'FUNCTION', 'OPERATION'], ...propsData },
    stubs: {
      VarPicker: { name: 'VarPicker', template: '<div />' },
      ExpressionEditorDialog: { name: 'ExpressionEditorDialog', template: '<div />' },
      'el-tooltip': { template: '<span><slot /></span>' }
    }
  })
}

describe('统一 OperandPicker', () => {
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

  test('手输阈值和路径以空节点打开统一表达式画布', () => {
    const wrapper = mountPicker({ expectedType: 'NUMBER' })

    wrapper.vm.openManualEditor('LITERAL')
    expect(wrapper.vm.editorValue).toEqual({ kind: 'LITERAL', value: '', valueType: 'NUMBER' })
    expect(wrapper.vm.editorVisible).toBe(true)

    wrapper.vm.openManualEditor('PATH')
    expect(wrapper.vm.editorValue).toMatchObject({ kind: 'PATH', value: '', resolved: false })
  })
})
