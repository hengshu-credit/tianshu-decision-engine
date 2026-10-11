import { shallowMount } from '@test-utils'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import * as api from '@/api/governance'

afterEach(() => vi.restoreAllMocks())

test('返回项目管理后不沿用上个项目范围隐藏其他项目的草稿', async () => {
  const list = vi.spyOn(api, 'listWorkspaceDrafts').mockResolvedValue({ data: { records: [], total: 0 } })
  const wrapper = shallowMount(GovernanceWorkspaceToolbar, {
    props: { resourceType: 'PROJECT' },
    mocks: { $route: { path: '/project', query: {} }, $store: { state: { currentProject: { id: 19 } } } },
  })
  await wrapper.vm.load()
  expect(list).toHaveBeenCalledWith(expect.objectContaining({ resourceType: 'PROJECT', projectId: undefined }))
  wrapper.unmount()
})

test('草稿与审批入口作为页面操作按钮，不再输出独立顶部提示栏', () => {
  const wrapper = shallowMount(GovernanceWorkspaceToolbar, {
    props: { resourceType: 'PROJECT' },
    mocks: { $route: { path: '/project', query: {} } },
  })
  expect(wrapper.text()).toContain('草稿与审批')
  expect(wrapper.find('.workspace-release-bar').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('保存为草稿，可继续编辑')
  wrapper.unmount()
})

test('计费配置按自身类型和项目分页查看驳回申请，不混入项目审批', async () => {
  const list = vi.spyOn(api, 'listWorkspaceDrafts').mockResolvedValue({ data: { records: [{ id: 9, status: 'REJECTED' }], total: 1 } })
  const wrapper = shallowMount(GovernanceWorkspaceToolbar, { props: { resourceType: 'BILLING_CONFIG', projectId: 3 }, mocks: { $route: { path: '/billing', query: {} } } })
  await wrapper.setData({ status: 'REJECTED', page: 2 })
  await wrapper.vm.load()
  expect(list).toHaveBeenCalledWith({ resourceType: 'BILLING_CONFIG', projectId: 3, status: 'REJECTED', pageNum: 2, pageSize: 20 })
  expect(wrapper.vm.statusLabel(wrapper.vm.rows[0].status)).toBe('已驳回')
  expect(wrapper.vm.title({ resourceType: 'BILLING_CONFIG', draftSnapshotJson: '{"projectName":"所属项目","billingName":"计费项"}' })).toBe('计费项')
  wrapper.unmount()
})

test('切换状态后迟到的草稿列表不能覆盖待审批列表', async () => {
  let finish
  vi.spyOn(api, 'listWorkspaceDrafts').mockImplementationOnce(() => new Promise(resolve => { finish = resolve })).mockResolvedValue({ data: { records: [{ id: 8, status: 'PENDING' }], total: 1 } })
  const wrapper = shallowMount(GovernanceWorkspaceToolbar, { props: { resourceType: 'PROJECT' }, mocks: { $route: { path: '/project', query: {} } } })
  const first = wrapper.vm.load()
  await wrapper.setData({ status: 'PENDING' })
  await wrapper.vm.search()
  finish({ data: { records: [{ id: 7, status: 'EDITING' }], total: 1 } })
  await first
  expect(wrapper.vm.rows.map(row => row.id)).toEqual([8])
  expect(wrapper.vm.loading).toBe(false)
  wrapper.unmount()
})
