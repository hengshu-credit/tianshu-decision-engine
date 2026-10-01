vi.mock('@/api/governance', () => ({
  listGovernanceRequests: vi.fn().mockResolvedValue({
    data: {
      records: [{
        id: 12,
        requestNo: 'GOV-12',
        resourceType: 'RULE',
        resourceId: 7,
        action: 'UPDATE',
        status: 'PENDING',
        applicant: 'same-user',
        submittedSnapshotJson: '{"ruleName":"授信规则"}'
      }],
      total: 1
    }
  }),
  getGovernanceSummary: vi.fn().mockResolvedValue({
    data: { pendingCount: 4, myDraftCount: 2, myRequestCount: 7, completedCount: 11 }
  })
}))

import { flushPromises, shallowMount } from '@test-utils'
import ApprovalList from '@/views/approval/ApprovalList.vue'
import router from '@/router'
import * as governanceApi from '@/api/governance'

describe('统一审批列表', () => {
  test('默认从跨模块待处理任务进入并提供任务视角', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    expect(wrapper.vm.taskScopes.map(tab => tab.label)).toEqual([
      '待处理', '我的申请', '已结束', '全部记录'
    ])
    expect(wrapper.vm.activeScope).toBe('PENDING')
    expect(governanceApi.listGovernanceRequests).toHaveBeenCalledWith(
      expect.objectContaining({ taskScope: 'PENDING', tab: undefined })
    )
  })

  test('资源筛选覆盖名单和计费等全部审批对象', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    expect(wrapper.vm.resourceOptions.map(item => item.value)).toEqual(
      expect.arrayContaining(['LIST_LIBRARY', 'LIST_RECORD_BATCH', 'BILLING_CONFIG'])
    )
  })

  test('点击申请进入统一审批详情', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()

    wrapper.vm.openDetail({ id: 12 })

    expect(router.push).toHaveBeenCalledWith('/approval/12')
  })

  test('项目上下文进入详情时保留项目范围', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    wrapper.vm.contextProjectId = 9

    wrapper.vm.openDetail({ id: 12 })

    expect(router.push).toHaveBeenCalledWith({
      path: '/approval/12',
      query: { projectId: 9 }
    })
  })

  test('项目规则关联归入规则页签并显示业务动作标题', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    const request = {
      resourceType: 'RULE_PROJECT_BINDING',
      resourceId: 0,
      action: 'CREATE',
      draftSnapshotJson: '{"definitionId":30,"projectId":9,' +
        '"ruleName":"共享评分规则","projectName":"风控项目"}'
    }

    expect(wrapper.vm.resourceTypeLabel(request.resourceType)).toBe(
      '项目规则关联'
    )
    expect(wrapper.vm.resourceTitle(request)).toBe(
      '将规则“共享评分规则”加入项目“风控项目”'
    )
  })

  test('项目上下文只查询当前项目审批', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    governanceApi.listGovernanceRequests.mockClear()
    wrapper.vm.contextProjectId = 9

    await wrapper.vm.loadRequests()

    expect(governanceApi.listGovernanceRequests).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 9 })
    )
  })

  test('任务概览与列表使用同一项目范围', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    governanceApi.getGovernanceSummary.mockClear()
    wrapper.vm.contextProjectId = 9

    await wrapper.vm.loadSummary()

    expect(governanceApi.getGovernanceSummary).toHaveBeenCalledWith({
      projectId: 9
    })
  })

  test('切换到已结束任务时清理非终态筛选并只提供终态选项', async() => {
    const wrapper = shallowMount(ApprovalList)
    await flushPromises()
    wrapper.vm.activeScope = 'COMPLETED'
    wrapper.vm.filters.status = 'EDITING'

    wrapper.vm.changeScope()

    expect(wrapper.vm.filters.status).toBe('')
    expect(wrapper.vm.visibleStatusOptions.map(item => item.value)).toEqual([
      'APPROVED', 'REJECTED', 'CONFLICT', 'CANCELLED'
    ])
  })
})
