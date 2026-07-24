import { mount } from '@test-utils'
import RuleLifecyclePanel from '@/components/rule/RuleLifecyclePanel.vue'

describe('RuleLifecyclePanel', () => {
  test.each([
    ['DRAFT', ['preflight', 'submit']],
    ['REVIEW', ['return', 'approve']],
    ['APPROVED', ['publish', 'download']],
    ['PUBLISHED', ['download', 'offline']]
  ])('%s 只显示合法动作', (state, expected) => {
    const wrapper = mount(RuleLifecyclePanel, { props: { revision: { state, artifactId: state === 'DRAFT' || state === 'REVIEW' ? null : 7 } } })
    expected.forEach(action => expect(wrapper.find(`[data-testid="${action}"]`).exists()).toBe(true))
  })

  test('破坏性 Schema 未填写原因时禁止批准', async () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: { revision: { state: 'REVIEW' }, validationReport: { breakingSchemaChange: true } }
    })
    expect(wrapper.vm.approvalReasonMissing).toBe(true)
    wrapper.vm.forceReason = '调用方已确认兼容窗口'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.approvalReasonMissing).toBe(false)
  })

  test('生命周期状态对业务人员显示中文', () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: { revision: { state: 'APPROVED', revisionNo: 4 } }
    })
    expect(wrapper.vm.stateLabel).toBe('已批准')
  })
})
