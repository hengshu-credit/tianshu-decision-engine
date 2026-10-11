import { shallowMount } from '@test-utils'
import ExpressionFormulaPreview from '@/components/expression/ExpressionFormulaPreview.vue'

const vars = [{ _varId: 10, _refType: 'VARIABLE', varCode: 'age', varLabel: '年龄', varType: 'NUMBER' }]

function mountPreview(operand = { kind: 'PATH', value: 'request.age', code: 'request.age' }) {
  return shallowMount(ExpressionFormulaPreview, {
    props: { operand, vars, functions: [] },
    stubs: {
      MonacoEditor: { name: 'MonacoEditor', template: '<textarea />' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' }
    }
  })
}

describe('ExpressionFormulaPreview', () => {
  test('公式和完整只读脚本在同一面板展示，无需展开第二层面板', () => {
    const wrapper = mountPreview({ kind: 'REFERENCE', refId: 10, refType: 'VARIABLE', code: 'age', value: 'age', label: '年龄', resolved: true })

    expect(wrapper.find('.expression-formula-preview__business').text()).toContain('年龄 age')
    const script = wrapper.get('.expression-formula-preview__code')
    expect(script.text()).toBe('age')
    expect(script.element.tagName).toBe('PRE')
    const formula = wrapper.find('.expression-formula-preview__business')
    expect(formula.element.compareDocumentPosition(script.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    wrapper.unmount()
  })

  test('修改表达式实时同步公式和脚本，清空后不残留旧代码', async () => {
    const wrapper = mountPreview({ kind: 'LITERAL', value: '1', valueType: 'NUMBER' })
    await wrapper.setProps({ operand: { kind: 'LITERAL', value: 'false', valueType: 'BOOLEAN' } })
    expect(wrapper.find('.expression-formula-preview__business').text()).toContain('false')
    expect(wrapper.get('.expression-formula-preview__code').text()).toBe('false')
    await wrapper.setProps({ operand: null })
    expect(wrapper.get('.expression-formula-preview__code').text()).toBe('配置表达式后生成脚本')
    wrapper.unmount()
  })

  test('双击进入编辑，取消恢复原脚本并回到预览', async() => {
    const wrapper = mountPreview()
    await wrapper.find('.expression-formula-preview__read').trigger('dblclick')
    wrapper.setData({ editScript: 'changed' })
    wrapper.vm.cancelEditing()

    expect(wrapper.vm.editing).toBe(false)
    expect(wrapper.vm.editScript).toBe('request.age')
  })

  test('确认脚本后发出结构化 Operand', () => {
    const wrapper = mountPreview()
    wrapper.vm.startEditing()
    wrapper.setData({ editScript: 'age + 1' })
    wrapper.vm.confirmEditing()

    expect(wrapper.emitted().confirm[0][0].terms[0].operand).toMatchObject({ refId: 10, refType: 'VARIABLE' })
    expect(wrapper.vm.editing).toBe(false)
  })

  test('解析错误保持编辑态并显示行列', () => {
    const wrapper = mountPreview()
    wrapper.vm.startEditing()
    wrapper.setData({ editScript: 'age = 1' })
    wrapper.vm.confirmEditing()

    expect(wrapper.emitted().confirm).toBeUndefined()
    expect(wrapper.vm.editing).toBe(true)
    expect(wrapper.vm.parseError).toContain('第 1 行，第 5 列')
  })
})
