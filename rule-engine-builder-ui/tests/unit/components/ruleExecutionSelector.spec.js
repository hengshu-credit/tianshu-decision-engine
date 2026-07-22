import { shallowMount } from '@test-utils'

jest.unmock('@/components/common/RuleExecutionSelector.vue')

const RuleExecutionSelector = require('@/components/common/RuleExecutionSelector.vue').default

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

  test('按项目和全局作用域分组展示', () => {
    const wrapper = shallowMount(RuleExecutionSelector, {
      props: { rules, ruleId: 8, ruleCode: 'score_card' },
      stubs
    })

    expect(wrapper.findAll('.rule-scope-group')).toHaveLength(2)
    expect(wrapper.text()).toContain('输入 2')
    expect(wrapper.text()).toContain('输出 1')
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
})
