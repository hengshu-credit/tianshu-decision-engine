import { mount } from '@test-utils'
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'

describe('RuleValidationReport', () => {
  test('字段引用错误提供修复说明并把原诊断交给定位动作', async () => {
    const issue = { code: 'REFERENCE_DANGLING_REFERENCE', path: '$.nodes[0].properties.condition', message: '字段已停用', resourceId: 7, refType: 'VARIABLE' }
    const wrapper = mount(RuleValidationReport, { props: { locatable: true, report: { valid: false, errors: [issue] } } })
    expect(wrapper.text()).toContain('重新选择')
    await wrapper.get('[data-action="locate-issue"]').trigger('click')
    expect(wrapper.emitted('locate')).toEqual([[issue]])
    wrapper.unmount()
  })
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
