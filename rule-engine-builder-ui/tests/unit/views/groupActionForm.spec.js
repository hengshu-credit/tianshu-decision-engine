import { shallowMount } from '@test-utils'

const GroupActionForm = require('@/views/experiment/GroupActionForm.vue').default

describe('GroupActionForm', () => {
  test('选择规则时同时写入稳定 ID 和编码快照', () => {
    const row = { ruleId: null, ruleCode: '' }
    const wrapper = shallowMount(GroupActionForm, {
      props: { row, rulesForProject: [] },
      stubs: {
        'el-row': true,
        'el-col': true,
        'el-select': true,
        'el-option': true,
        'el-input': true,
        'el-input-number': true,
        'el-switch': true,
        'el-button': true,
        'rule-execution-selector': true
      }
    })

    wrapper.vm.onRuleSelect({ id: 8, ruleCode: 'score_card' })

    expect(row.ruleId).toBe(8)
    expect(row.ruleCode).toBe('score_card')
  })

  test('清空选择时同时清空 ID 和编码快照', () => {
    const row = { ruleId: 8, ruleCode: 'score_card' }
    const wrapper = shallowMount(GroupActionForm, {
      props: { row, rulesForProject: [] },
      stubs: ['el-row', 'el-col', 'el-select', 'el-option', 'el-input', 'el-input-number', 'el-switch', 'el-button', 'rule-execution-selector']
    })

    wrapper.vm.onRuleSelect(null)

    expect(row.ruleId).toBeNull()
    expect(row.ruleCode).toBe('')
  })
})
