import { shallowMount } from '@vue/test-utils'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'

function mountEditor(propsData = {}) {
  return shallowMount(ExpressionEditorDialog, {
    propsData: { visible: true, value: null, vars: [], functions: [], ...propsData },
    stubs: {
      ExpressionPalette: true,
      ExpressionCanvas: true,
      ExpressionNodeInspector: true,
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-alert': true
    }
  })
}

describe('ExpressionEditorDialog', () => {
  test('点击函数插入当前槽并自动选中首个参数', () => {
    const wrapper = mountEditor()
    wrapper.vm.insertTemplate(wrapper.vm.functionTemplate({ id: 7, funcCode: 'max', paramsJson: '[{"name":"a","type":"NUMBER"},{"name":"b","type":"NUMBER"}]' }))

    expect(wrapper.vm.draft.functionCode).toBe('max')
    expect(wrapper.vm.draft.args).toHaveLength(2)
    expect(wrapper.vm.selectedPath).toEqual(['args', 0])
  })

  test('支持撤销重做且取消不改父值', () => {
    const source = { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    const wrapper = mountEditor({ value: source })
    wrapper.vm.insertTemplate({ kind: 'LITERAL', value: '200', valueType: 'NUMBER' })
    expect(wrapper.vm.draft.value).toBe('200')
    wrapper.vm.undo()
    expect(wrapper.vm.draft.value).toBe('100')
    wrapper.vm.redo()
    expect(wrapper.vm.draft.value).toBe('200')
    wrapper.vm.cancel()
    expect(source.value).toBe('100')
    expect(wrapper.emitted().cancel).toBeTruthy()
  })

  test('非法参数阻止应用，完整表达式一次性发出', () => {
    const wrapper = mountEditor()
    wrapper.vm.apply()
    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.validationErrors.length).toBeGreaterThan(0)

    wrapper.vm.insertTemplate({ kind: 'LITERAL', value: '600', valueType: 'NUMBER' })
    wrapper.vm.apply()
    expect(wrapper.emitted().input[0][0]).toEqual({ kind: 'LITERAL', value: '600', valueType: 'NUMBER' })
  })

  test('调用位置可收紧允许的节点类型', () => {
    const wrapper = mountEditor({ allowedKinds: ['REFERENCE'] })
    wrapper.vm.insertTemplate({ kind: 'LITERAL', value: '600', valueType: 'NUMBER' })
    wrapper.vm.apply()

    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.validationErrors[0].message).toContain('不支持')
  })
})
