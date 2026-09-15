vi.mock('@/api/governance', () => ({
  getGovernanceRequest: vi.fn().mockResolvedValue({
    data: {
      request: {
        id: 12,
        requestNo: 'GOV-12',
        resourceType: 'DATABASE',
        resourceId: 7,
        action: 'UPDATE',
        status: 'PENDING',
        applicant: 'same-user',
        baseVersionNo: 2,
        submittedSnapshotJson: '{"host":"db-new","port":3306}'
      },
      diff: {
        summary: '共 2 项变更',
        fields: [
          {
            key: '$.host',
            label: 'host',
            leftValue: 'db-old',
            rightValue: 'db-new',
            changeType: 'MODIFIED',
            sensitive: false,
            changed: true
          },
          {
            key: '$.credentials/password',
            label: '凭据 /password',
            leftValue: '已配置',
            rightValue: '已配置',
            changeType: 'MODIFIED',
            sensitive: true,
            changed: true
          }
        ]
      },
      events: [{
        id: 1,
        action: 'SUBMIT',
        actor: 'same-user',
        toStatus: 'PENDING'
      }],
      dependencies: [],
      versions: [{
        id: 3,
        versionNo: 2,
        snapshotJson: '{"host":"db-old","port":3306}'
      }]
    }
  }),
  preflightGovernanceRequest: vi.fn(),
  submitGovernanceRequest: vi.fn(),
  approveGovernanceRequest: vi.fn(),
  rejectGovernanceRequest: vi.fn(),
  cancelGovernanceRequest: vi.fn(),
  restoreGovernanceVersion: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '12' } }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() })
}))

import { flushPromises, shallowMount } from '@test-utils'
import { ElMessage } from 'element-plus'
import * as definitionApi from '@/api/definition'
import { approveGovernanceRequest } from '@/api/governance'
import { clearCurrentUser, setCurrentUser } from '@/security/permissionState'
import ApprovalDetail from '@/views/approval/ApprovalDetail.vue'

describe('统一审批详情', () => {
  test('审批请求失败保留意见和对话框，不产生未处理拒绝', async () => {
    const wrapper = shallowMount(ApprovalDetail, { global: { directives: { permission: {} } } })
    await flushPromises()
    wrapper.vm.openAction('approve')
    wrapper.vm.actionComment = '审查意见'
    approveGovernanceRequest.mockRejectedValueOnce(Object.assign(new Error('发布写入失败'), { requestErrorNotified: true }))
    await expect(wrapper.vm.confirmAction()).resolves.toBeUndefined()
    expect(wrapper.vm.actionDialogVisible).toBe(true)
    expect(wrapper.vm.actionComment).toBe('审查意见')
    expect(wrapper.vm.actionLoading).toBe(false)
    expect(ElMessage.success).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    clearCurrentUser()
  })

  test('左右展示基准和提交版本差异', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('基准版本')
    expect(wrapper.text()).toContain('提交版本')
    expect(wrapper.text()).toContain('db-old')
    expect(wrapper.text()).toContain('db-new')
  })

  test('敏感字段只显示配置状态且审批历史保留', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('敏感内容受保护')
    expect(wrapper.text()).toContain('same-user')
    expect(wrapper.text()).not.toContain('top-secret')
  })

  test('非申请人不显示草稿预检提交和撤回操作', async() => {
    setCurrentUser({
      username: 'other-user',
      permissions: ['approval:submit', 'approval:approve']
    })
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      ...wrapper.vm.detail,
      request: {
        ...wrapper.vm.detail.request,
        status: 'EDITING'
      }
    }
    await wrapper.vm.$nextTick()
    const buttonLabels = wrapper.findAll('el-button-stub')
      .map(button => button.text().trim())

    expect(buttonLabels).not.toContain('依赖预检')
    expect(buttonLabels).not.toContain('提交审批')
    expect(buttonLabels).not.toContain('取消申请')
  })

  test('关联删除审批以移出项目描述且不显示资源 0', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      request: {
        id: 18,
        resourceType: 'RULE_PROJECT_BINDING',
        resourceId: 15,
        action: 'DELETE',
        status: 'EDITING',
        draftSnapshotJson: '{"id":15,"definitionId":30,"projectId":9,' +
          '"ruleName":"共享评分规则","projectName":"风控项目"}'
      },
      diff: { fields: [] }
    }
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.resourceTypeLabel('RULE_PROJECT_BINDING')).toBe(
      '项目规则关联'
    )
    expect(wrapper.vm.resourceTitle).toBe(
      '将规则“共享评分规则”移出项目“风控项目”'
    )
    expect(wrapper.text()).not.toContain('项目规则关联 #0')
  })

  test('返回审批详情来源页而不是固定跳审批管理页', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.goBack()

    expect(wrapper.vm.navigation.back).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.navigation.push).not.toHaveBeenCalled()
  })

  test('配置差异可选择历史生效版本与当前审批版本比较', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()

    wrapper.vm.compareGovernanceVersion(wrapper.vm.detail.versions[0])

    expect(wrapper.vm.compareLeftKey).toBe('VERSION:3')
    expect(wrapper.vm.compareRightKey).toBe('CURRENT')
    expect(wrapper.vm.compareOriginal).toBe('{"host":"db-old","port":3306}')
    expect(wrapper.vm.compareModified).toBe('{"host":"db-new","port":3306}')
  })

  test('血缘依赖区复用统一血缘图组件并启用完整画布能力', async() => {
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      ...wrapper.vm.detail,
      request: {
        ...wrapper.vm.detail.request,
        resourceType: 'RULE',
        resourceId: 27
      }
    }
    await wrapper.vm.$nextTick()

    const graph = wrapper.findComponent({ name: 'LineageGraph' })
    expect(graph.exists()).toBe(true)
    expect(graph.props()).toMatchObject({
      embedded: true,
      initialNodeType: 'RULE',
      initialNodeId: 27,
      initialDirection: 'ALL'
    })
  })

  test('规则审批可直接打开对应冻结修订设计', async() => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{
        id: 21,
        definitionId: 29,
        state: 'REVIEW',
        governanceRequestId: 12
      }]
    })
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.detail = {
      ...wrapper.vm.detail,
      request: {
        ...wrapper.vm.detail.request,
        resourceType: 'RULE',
        resourceId: 29,
        submittedSnapshotJson: JSON.stringify({
          id: 29,
          modelType: 'RULE_SET'
        })
      }
    }
    await wrapper.vm.$nextTick()

    const button = wrapper.findAll('el-button-stub')
      .find(item => item.text().trim() === '打开规则设计')
    expect(button).toBeDefined()
    await button.trigger('click')
    await flushPromises()

    expect(wrapper.vm.navigation.push).toHaveBeenCalledWith({
      name: 'RuleSet',
      params: { id: '29' },
      query: { sourceType: 'REVISION', sourceId: '21' }
    })
  })

  test('审批时发现内容漂移提示已生成可编辑草稿', async() => {
    approveGovernanceRequest.mockResolvedValueOnce({
      data: { status: 'CONFLICT' }
    })
    const wrapper = shallowMount(ApprovalDetail, {
      global: { directives: { permission: {} } }
    })
    await flushPromises()
    wrapper.vm.activeAction = 'approve'

    await wrapper.vm.confirmAction()

    expect(ElMessage.warning).toHaveBeenCalledWith(
      '审批内容或依赖已变化，已生成可编辑草稿，请重新提交审批'
    )
    expect(ElMessage.success).not.toHaveBeenCalled()
  })
})
