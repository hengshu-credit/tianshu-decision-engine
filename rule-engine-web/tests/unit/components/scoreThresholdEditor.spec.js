import { shallowMount, flushPromises } from '@test-utils'
import { toRaw } from 'vue'
import ScoreThresholdEditor from '@/components/decision/ScoreThresholdEditor.vue'
import Scorecard from '@/views/designer/Scorecard.vue'
import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'

const RangeInput = {
  name: 'RangeInput', props: ['modelValue', 'min'], emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
}

test('区间输入保持原对象并上报范围变化，删除准确指向所选行', async () => {
  const thresholds = [{ min: 0, max: 50, result: 'A' }, { min: 50, max: 100, result: 'B' }]
  const wrapper = shallowMount(ScoreThresholdEditor, {
    props: { thresholds }, stubs: { 'el-input-number': RangeInput },
  })
  try {
    const inputs = wrapper.findAllComponents(RangeInput)
    expect(inputs.map(input => input.props('min'))).toEqual([0, 0, 0, 50])
    await inputs[2].find('input').setValue('60')
    expect(toRaw(wrapper.emitted('change-min')[0][0])).toBe(thresholds[1])
    expect(wrapper.emitted('change-min')[0][1]).toBe(60)
    expect(thresholds[1].min).toBe(50)
    await wrapper.get('[aria-label="删除等级 2"]').trigger('click')
    expect(wrapper.emitted('remove')).toEqual([[1]])
    expect(thresholds).toHaveLength(2)
  } finally { wrapper.unmount() }
})

test('空区间显示调用方空态，新增区间后恢复原有序号及等级结果', async () => {
  const wrapper = shallowMount(ScoreThresholdEditor, {
    props: { thresholds: [] }, slots: { empty: '<p>尚未配置等级</p>' },
  })
  try {
    expect(wrapper.text()).toContain('尚未配置等级')
    await wrapper.setProps({ thresholds: [{ min: 0, max: 20, result: '' }, { min: 20, max: 40, result: 'B' }] })
    expect(wrapper.text()).not.toContain('尚未配置等级')
    expect(wrapper.findAll('.thresh-badge').map(row => row.text())).toEqual(['等级 1', 'B'])
  } finally { wrapper.unmount() }
})

test.each([
  [Scorecard, '低风险', 0, 'STRING'],
  [AdvancedScorecard, '"低风险"', '0', ''],
])('$name 通过公共区间组件编辑后保留本模型的值转换、ID及删除语义', async (component, textResult, zeroResult, expectedType) => {
  const wrapper = shallowMount({ ...component, methods: { ...component.methods, loadContent() {} } }, {
    mocks: { $route: { params: {} }, $router: { back() {} } },
    stubs: { ScoreThresholdEditor: false, 'el-input-number': RangeInput },
  })
  try {
    await flushPromises()
    wrapper.vm.addThreshold()
    wrapper.vm.addThreshold()
    await flushPromises()
    const editor = wrapper.getComponent(ScoreThresholdEditor)
    const threshold = wrapper.vm.model.thresholds[0]
    const rangeInputs = editor.findAllComponents(RangeInput)
    await rangeInputs[0].find('input').setValue('5')
    expect(rangeInputs[1].props('min')).toBe(5)
    await rangeInputs[0].find('input').setValue('0')
    await rangeInputs[1].find('input').setValue('45')
    expect(wrapper.vm.model.thresholds[0]).toBe(threshold)
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).thresholds[0]).toMatchObject({ min: 0, max: 45 })
    const picker = editor.findAllComponents('operand-picker-stub')[0]
    expect(picker.props('expectedType')).toBe(expectedType)
    picker.vm.$emit('input', { kind: 'LITERAL', value: '低风险', valueType: 'STRING' })
    await flushPromises()
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).thresholds[0].result).toBe(textResult)
    picker.vm.$emit('input', { kind: 'LITERAL', value: 0, valueType: 'NUMBER' })
    await flushPromises()
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).thresholds[0].result).toBe(zeroResult)
    const reference = { kind: 'REFERENCE', refId: '9007199254740993', refType: 'VARIABLE', code: 'Raw_Grade', valueType: 'STRING' }
    picker.vm.$emit('input', reference)
    await flushPromises()
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).thresholds[0].resultOperand).toEqual(reference)
    picker.vm.$emit('input', null)
    await flushPromises()
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).thresholds[0]).toMatchObject({ resultOperand: null, result: '' })
    const kept = wrapper.vm.model.thresholds[1]
    await editor.get('[aria-label="删除等级 1"]').trigger('click')
    expect(wrapper.vm.model.thresholds).toHaveLength(1)
    expect(wrapper.vm.model.thresholds[0]).toBe(kept)
    await editor.get('[aria-label="删除等级 1"]').trigger('click')
    expect(editor.text()).toContain('暂未配置等级')
  } finally { wrapper.unmount() }
})
