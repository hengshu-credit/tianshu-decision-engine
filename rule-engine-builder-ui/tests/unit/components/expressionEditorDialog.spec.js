import { shallowMount } from '@vue/test-utils'
import ExpressionEditorDialog from '@/components/expression/ExpressionEditorDialog.vue'
import { createOperationOperand } from '@/utils/operand'

function variable(id, code, category = 'standalone', refType = 'VARIABLE') {
  return {
    id,
    _varId: id,
    _refType: refType,
    varCode: code,
    varLabel: code,
    varType: 'NUMBER',
    _ref: { category, refType }
  }
}

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

  test('复杂表达式默认折叠到两层并支持一键展开', () => {
    const value = {
      kind: 'FUNCTION',
      functionCode: 'outer',
      args: [{
        kind: 'OPERATION',
        terms: [
          { operand: { kind: 'FUNCTION', functionCode: 'inner', args: [{ kind: 'LITERAL', value: '1', valueType: 'NUMBER' }] } },
          { operator: '+', operand: null }
        ]
      }]
    }
    const wrapper = mountEditor({ value })

    expect(wrapper.vm.collapsedPathKeys).toContain('args.0')
    wrapper.vm.expandAll()
    expect(wrapper.vm.collapsedPathKeys).toEqual([])
  })

  test('选择折叠子树中的位置会自动展开全部祖先', () => {
    const value = {
      kind: 'FUNCTION',
      functionCode: 'outer',
      args: [{ kind: 'FUNCTION', functionCode: 'inner', args: [{ kind: 'LITERAL', value: '1', valueType: 'NUMBER' }] }]
    }
    const wrapper = mountEditor({ value })
    wrapper.setData({ collapsedPathKeys: ['$', 'args.0'] })

    wrapper.vm.selectPath(['args', 0, 'args', 0])

    expect(wrapper.vm.collapsedPathKeys).toEqual([])
  })

  test('在运算项后插入运算符后自动选中新同级项', () => {
    const value = createOperationOperand([
      { operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' } },
      { operator: '+', operand: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' } }
    ])
    const wrapper = mountEditor({ value })
    wrapper.vm.selectedPath = ['terms', 0, 'operand']
    wrapper.vm.insertTemplate(createOperationOperand([{ operand: null }, { operator: '*', operand: null }]))

    expect(wrapper.vm.draft.terms.map(term => term.operator || '')).toEqual(['', '*', '+'])
    expect(wrapper.vm.selectedPath).toEqual(['terms', 1, 'operand'])
  })

  test('检查器追加运算项后编辑器自动选中新空项', () => {
    const value = createOperationOperand([
      { operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' } },
      { operator: '+', operand: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' } }
    ])
    const wrapper = mountEditor({ value })
    wrapper.vm.selectedPath = []
    wrapper.vm.updateSelected({
      ...value,
      terms: value.terms.concat([{ operator: '+', operand: null }])
    })

    expect(wrapper.vm.selectedPath).toEqual(['terms', 2, 'operand'])
  })

  test('画布路径输入支持唯一匹配、多候选和无匹配', () => {
    const wrapper = mountEditor({ vars: [variable(1, 'score'), variable(2, 'score', 'model', 'MODEL_OUTPUT')] })
    wrapper.vm.draft = { kind: 'PATH', value: '', resolved: false }
    wrapper.vm.updateManualPath({ path: [], value: 'score' })
    wrapper.vm.resolveManualPath([])
    expect(wrapper.vm.pathCandidates).toHaveLength(2)

    wrapper.vm.selectPathCandidate({ path: [], candidate: wrapper.vm.pathCandidates[1] })
    expect(wrapper.vm.draft).toMatchObject({ kind: 'PATH', refId: 2, refType: 'MODEL_OUTPUT', resolved: true })
  })
})
