import { shallowMount } from '@test-utils'
import ExpressionFormulaPreview from '@/components/expression/ExpressionFormulaPreview.vue'

const ScriptPanel = (await vi.importActual('@/components/common/ScriptPanel.vue')).default

const vars = [{ _varId: 10, _refType: 'VARIABLE', varCode: 'age', varLabel: '年龄', varType: 'NUMBER' }]

function mountPreview(operand = { kind: 'PATH', value: 'request.age', code: 'request.age' }) {
  return shallowMount(ExpressionFormulaPreview, {
    props: { operand, vars, functions: [] },
    stubs: {
      ScriptPanel,
      MonacoEditor: { name: 'MonacoEditor', template: '<textarea />' },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' }
    }
  })
}

describe('ExpressionFormulaPreview', () => {
  test('公式显示在复用的只读脚本预览上方', () => {
    const wrapper = mountPreview({ kind: 'REFERENCE', refId: 10, refType: 'VARIABLE', code: 'age', value: 'age', label: '年龄', resolved: true })

    expect(wrapper.find('.expression-formula-preview__business').text()).toContain('年龄 age')
    const script = wrapper.findComponent(ScriptPanel)
    expect(script.vm.editScript).toBe('age')
    expect(script.find('textarea').element.readOnly).toBe(true)
    expect(script.props('guidance')).toBe('根据当前表达式实时生成')
    const formula = wrapper.find('.expression-formula-preview__business')
    expect(formula.element.compareDocumentPosition(script.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    wrapper.unmount()
  })

  test('修改表达式实时同步公式和脚本，清空后不残留旧代码', async () => {
    const wrapper = mountPreview({ kind: 'LITERAL', value: '1', valueType: 'NUMBER' })
    await wrapper.setProps({ operand: { kind: 'LITERAL', value: 'false', valueType: 'BOOLEAN' } })
    expect(wrapper.find('.expression-formula-preview__business').text()).toContain('false')
    expect(wrapper.findComponent(ScriptPanel).vm.editScript).toBe('false')
    await wrapper.setProps({ operand: null })
    expect(wrapper.findComponent(ScriptPanel).vm.editScript).toBe('')
    expect(wrapper.findComponent(ScriptPanel).props('compileResult')).toBeNull()
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
