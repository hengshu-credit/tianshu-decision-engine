import { mount } from '@test-utils'
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'

describe('RuleValidationReport', () => {
  test('展示阻断项、提醒和破坏性 Schema 风险', () => {
    const wrapper = mount(RuleValidationReport, { props: { report: {
      valid: false,
      breakingSchemaChange: true,
      errors: [{ code: 'MODEL_MISSING', message: '模型不存在', path: '$.nodes[0]' }],
      warnings: [{ code: 'BREAKING_SCHEMA', message: '删除输出字段' }]
    } } })

    expect(wrapper.text()).toContain('MODEL_MISSING')
    expect(wrapper.text()).toContain('删除输出字段')
    expect(wrapper.find('el-alert-stub').attributes('title')).toContain('风险接受原因')
  })
})
