import { shallowMount } from '@test-utils'

vi.unmock('@/components/common/RuleExecutionSelector.vue')

const RuleExecutionSelector = (await vi.importActual('@/components/common/RuleExecutionSelector.vue')).default

const stubs = {
  'el-select': { template: '<div class="select-stub"><slot /></div>' },
  'el-option-group': { template: '<div class="option-group-stub"><slot /></div>' },
  'el-option': { template: '<div class="option-stub" />' },
  'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
  'el-alert': { template: '<div class="alert-stub" />' }
}

describe('RuleExecutionSelector', () => {
  const rules = [
    { id: 7, scope: 'PROJECT', ruleCode: 'flow_a', ruleName: '主流程', modelType: 'FLOW', status: 1,
      inputFields: [{ scriptName: 'CREDIT_AMOUNT' }], outputFields: [{ scriptName: 'result' }] },
    { id: 8, scope: 'GLOBAL', ruleCode: 'score_card', ruleName: '评分卡', modelType: 'SCORE', status: 1,
      inputFields: [{ scriptName: 'CREDIT_AMOUNT' }, { scriptName: 'age' }], outputFields: [{ scriptName: 'score' }] }
  ]

  test('字段选择器同时提供项目和全局规则并保留输入输出摘要', () => {
    const wrapper = shallowMount(RuleExecutionSelector, {
      props: { rules, ruleId: 8, ruleCode: 'score_card' },
      stubs
    })

    expect(wrapper.vm.pickerRules.map(item => item.id)).toEqual([7, 8])
    expect(wrapper.vm.pickerRules.map(item => item._ref.category)).toEqual(['rule', 'rule'])
    expect(wrapper.text()).toContain('输入 2')
    expect(wrapper.text()).toContain('输出 1')
  })

  test('输入输出字段摘要使用 Element 标签而不是自绘伪输入框', () => {
    const wrapper = shallowMount(RuleExecutionSelector, {
      props: { rules, ruleId: 8, ruleCode: 'score_card' },
      stubs
    })

    expect(wrapper.findAll('.field-summary .tag-stub')).toHaveLength(3)
  })

  test('选择时按稳定 ID 返回完整规则对象', () => {
    const wrapper = shallowMount(RuleExecutionSelector, { props: { rules }, stubs })

    wrapper.vm.onChange(8)

    expect(wrapper.emitted('select')[0][0]).toMatchObject({ id: 8, ruleCode: 'score_card' })
    expect(wrapper.emitted('input')[0][0]).toBe(8)
  })

  test('当前规则选项不可用', () => {
    const wrapper = shallowMount(RuleExecutionSelector, {
      props: { rules, currentRuleId: 7 },
      stubs
    })

    expect(wrapper.vm.isRuleDisabled(rules[0])).toBe(true)
    expect(wrapper.vm.isRuleDisabled(rules[1])).toBe(false)
  })

  test('分流等只调用最新版的入口展示当前发布版本，不误用设计版本', () => {
    const wrapper = shallowMount(RuleExecutionSelector, { props: { rules: [{ ...rules[0], currentVersion: 9, publishedVersion: 3 }] }, stubs })
    expect(wrapper.vm.versionLabel(wrapper.vm.pickerRules[0])).toBe('最新 · v3')
    wrapper.unmount()
  })

  test('字段行确认时携带指定版本的稳定绑定，目录变化清除过期选择', async () => {
    const wrapper = shallowMount(RuleExecutionSelector, { props: { rules, versioned: true, catalogKey: 'project-a' }, stubs })
    wrapper.vm.draftVersions['8'] = { versionMode: 'FIXED', versionBindingId: '9007199254740993' }
    wrapper.vm.onChange(8)
    expect(wrapper.emitted('select')[0][0]).toMatchObject({ id: 8, pickerVersion: { versionMode: 'FIXED', versionBindingId: '9007199254740993' } })
    await wrapper.setProps({ catalogKey: 'project-b' })
    expect(wrapper.vm.versionFor(8)).toEqual({ versionMode: 'LATEST', versionBindingId: null })
    wrapper.unmount()
  })
})
