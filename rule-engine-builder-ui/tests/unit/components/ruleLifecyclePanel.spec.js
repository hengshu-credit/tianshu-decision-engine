import { mount } from '@test-utils'
import RuleLifecyclePanel from '@/components/rule/RuleLifecyclePanel.vue'

describe('RuleLifecyclePanel', () => {
  test.each([
    ['DRAFT', ['view-design', 'preflight', 'submit']],
    ['REVIEW', ['view-design', 'reject', 'approve']],
    ['REJECTED', ['view-design']],
    ['APPROVED', ['view-design', 'publish', 'edit-design', 'download', 'migration-download']],
    ['PUBLISHED', ['view-design', 'edit-design', 'download', 'migration-download', 'offline']],
    ['OFFLINE', ['view-design', 'edit-design', 'download', 'migration-download']],
  ])('%s 只显示合法动作', (state, expected) => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: {
        lifecycleRevision: {
          id: 9,
          state,
          revisionNo: 4,
          artifactId: state === 'DRAFT' || state === 'REVIEW' ? null : 7,
        },
      },
    })
    expect(
      wrapper.findAll('[data-testid]').map((button) => button.attributes('data-testid'))
    ).toEqual(expected)
  })

  test.each([
    ['DRAFT', 'view-design', '进入设计'],
    ['REVIEW', 'view-design', '查看设计'],
    ['APPROVED', 'edit-design', '基于此修订编辑'],
    ['PUBLISHED', 'edit-design', '基于此修订编辑'],
    ['OFFLINE', 'edit-design', '基于此修订编辑'],
  ])('%s design entry emits %s', async (state, testId, label) => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: { lifecycleRevision: { id: 9, state, revisionNo: 4, artifactId: 7 } },
    })

    const button = wrapper.find(`[data-testid="${testId}"]`)
    expect(button.text()).toBe(label)
    await button.trigger('click')
    expect(wrapper.emitted('action')).toContainEqual([
      expect.objectContaining({ action: testId }),
    ])
  })

  test('破坏性 Schema 未填写原因时禁止批准', async () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: {
        lifecycleRevision: { state: 'REVIEW' },
        validationReport: { breakingSchemaChange: true },
      },
    })
    expect(wrapper.vm.approvalReasonMissing).toBe(true)
    wrapper.vm.forceReason = '调用方已确认兼容窗口'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.approvalReasonMissing).toBe(false)
  })

  test('已绑定统一审批的评审修订只提供审批详情入口', () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: {
        lifecycleRevision: {
          id: 9,
          state: 'REVIEW',
          revisionNo: 4,
        },
        approvalRequestId: 41,
      },
    })

    expect(wrapper.find('[data-testid="go-approval"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reject"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="approve"]').exists()).toBe(false)
  })

  test('生命周期状态对业务人员显示中文', () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: { lifecycleRevision: { state: 'APPROVED', revisionNo: 4 } },
    })
    expect(wrapper.vm.stateLabel).toBe('已批准')
  })

  test('发布按钮明确显示被发布的修订号', () => {
    const wrapper = mount(RuleLifecyclePanel, {
      props: { lifecycleRevision: { state: 'APPROVED', revisionNo: 4 } },
    })

    expect(wrapper.find('[data-testid="publish"]').text()).toBe('发布修订 v4')
  })
})
