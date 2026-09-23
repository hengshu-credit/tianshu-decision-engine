import { mount } from '@test-utils'
import DerivedVariableEditor from '@/views/variable/components/DerivedVariableEditor.vue'
import { createDerivedConfig } from '@/utils/derivedVariable'

describe('DerivedVariableEditor', () => {
  test('历史字段只提供请求、外数及显式记录的结果，本次计算仍可选全部变量', () => {
    const vars = [
      { id: 1, _refType: 'VARIABLE', varSource: 'INPUT' },
      { id: 2, _refType: 'VARIABLE', varSource: 'API' },
      { id: 3, _refType: 'VARIABLE', varSource: 'DERIVED', recordResult: false },
      { id: 4, _refType: 'VARIABLE', varSource: 'LIST', recordResult: true },
      { id: 5, _refType: 'MODEL_OUTPUT', recordResult: false },
    ]
    const wrapper = mount(DerivedVariableEditor, { props: { vars, modelValue: { ...createDerivedConfig(), mode: 'HISTORY' } } })
    expect(wrapper.vm.historyVars.map(item => item.id)).toEqual([1, 2, 4])
    expect(wrapper.props('vars')).toHaveLength(5)
  })
  test('关联对始终按同一位置维护，并只从当前层开始删除', async () => {
    const config = { ...createDerivedConfig(), mode: 'HISTORY', steps: [{ inputs: [null], fromFields: [null], fields: [null], filters: [] }] }
    const wrapper = mount(DerivedVariableEditor, { props: { modelValue: config } })
    wrapper.vm.addPair(0)
    let next = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(next.steps[0].inputs).toEqual([null, null])
    expect(next.steps[0].fields).toEqual([null, null])
    await wrapper.setProps({ modelValue: next })
    wrapper.vm.addStep()
    next = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(next.steps).toHaveLength(2)
    await wrapper.setProps({ modelValue: next })
    wrapper.vm.removeStep(1)
    expect(wrapper.emitted('update:modelValue').at(-1)[0].steps).toHaveLength(1)
  })

  test('切换为文本属性时移除不适用的数值聚合', () => {
    const wrapper = mount(DerivedVariableEditor, { props: { modelValue: { ...createDerivedConfig(), mode: 'HISTORY', aggregate: 'SUM' },
      vars: [{ _refType: 'VARIABLE', _varId: 7, varType: 'STRING', varCode: 'identity', varLabel: '标识' }] } })
    wrapper.vm.setValueField({ kind: 'REFERENCE', refType: 'VARIABLE', refId: 7, valueType: 'STRING' })
    expect(wrapper.emitted('update:modelValue').at(-1)[0].aggregate).toBe('COUNT')
    wrapper.vm.setSubjects(['VARIABLE:7'])
    expect(wrapper.emitted('update:modelValue').at(-1)[0].subjectFields[0]).toMatchObject({ refType: 'VARIABLE', refId: 7 })
  })
})
