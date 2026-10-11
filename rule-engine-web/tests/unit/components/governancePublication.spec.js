import { mount, flushPromises } from '@test-utils'
import Status from '@/components/GovernanceResourceStatus.vue'
import Publish from '@/components/GovernancePublishAction.vue'
import * as api from '@/api/governance'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'

vi.mock('@/utils/workspaceDraftState', () => ({ watchResourceState: vi.fn((type, id, listener) => { listener({ ready: true }); return vi.fn() }) }))
afterEach(() => { vi.restoreAllMocks(); clearCurrentUser() })

test('生产仍生效的修改审批合并为一个状态，保留生产生效信息', async () => {
  const wrapper = mount(Status, { props: { resourceType: 'VARIABLE', resourceId: 7, status: 1 } })
  await wrapper.setData({ state: { ready: true, effectiveStatus: 'ACTIVE', requestStatus: 'PENDING', action: 'UPDATE' } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe('已发布·审批中')
  expect(wrapper.vm.summary.detail).toContain('生产版本仍然生效')
  wrapper.unmount()
})

test('未发布内容被驳回不会被误标为生产，读取失败明确显示未知', async () => {
  const wrapper = mount(Status, { props: { resourceType: 'PROJECT', resourceId: 7, status: 0 } })
  await wrapper.setData({ state: { ready: true, effectiveStatus: 'DRAFT', requestStatus: 'REJECTED' } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe('草稿·需修改')
  expect(wrapper.vm.summary.detail).toContain('尚未发布')
  await wrapper.setData({ state: { ready: false, error: true } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe('状态未知')
  expect(wrapper.get('.governance-resource-status > [title]').text()).not.toContain('已发布')
  wrapper.unmount()
})

test.each([
  ['DRAFT', 'EDITING', 'CREATE', '草稿'],
  ['DRAFT', 'PENDING', 'CREATE', '审批中'],
  ['DRAFT', 'CONFLICT', 'CREATE', '草稿·需修改'],
  ['DRAFT', 'CANCELLED', 'CREATE', '草稿'],
  ['ACTIVE', 'APPROVED', 'UPDATE', '已发布'],
  ['ACTIVE', 'EDITING', 'UPDATE', '已发布·待更新'],
  ['ACTIVE', 'EDITING', 'DELETE', '已发布·待更新'],
  ['ACTIVE', 'PENDING', 'DISABLE', '已发布·审批中'],
  ['ACTIVE', 'REJECTED', 'DELETE', '已发布·需修改'],
  ['ACTIVE', 'CONFLICT', 'UPDATE', '已发布·需修改'],
  ['ACTIVE', 'CANCELLED', 'UPDATE', '已发布'],
  ['DISABLED', 'APPROVED', 'DISABLE', '已停用'],
  ['DISABLED', 'PENDING', 'ENABLE', '已停用·审批中'],
  ['DELETED', 'APPROVED', 'DELETE', '已删除'],
])('%s / %s / %s 合并为 %s', async (effectiveStatus, requestStatus, action, label) => {
  const wrapper = mount(Status, { props: { resourceType: 'VARIABLE', resourceId: 7 } })
  await wrapper.setData({ state: { ready: true, effectiveStatus, requestStatus, action } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe(label)
  if (action === 'DELETE' && requestStatus !== 'APPROVED') expect(wrapper.vm.summary.detail).toContain('删除')
  wrapper.unmount()
})

test('规则下线与资源停用使用同一名称；新的规则草稿不被历史驳回覆盖', async () => {
  const wrapper = mount(Status, { props: { resourceType: 'RULE', resourceId: 7, status: 2 } })
  await wrapper.setData({ state: { ready: true, effectiveStatus: 'ACTIVE', requestStatus: 'APPROVED' } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe('已停用')
  await wrapper.setProps({ status: 1 })
  await wrapper.setData({ state: { ready: true, hasRuleDraft: true, requestStatus: 'REJECTED', action: 'DELETE' } })
  expect(wrapper.get('.governance-resource-status > [title]').text()).toBe('已发布·待更新')
  expect(wrapper.vm.summary.detail).not.toContain('删除')
  wrapper.unmount()
})

test.each(['PROJECT', 'VARIABLE', 'DATA_OBJECT', 'FIELD_VALIDATION', 'LIST_LIBRARY', 'EXTERNAL_DATASOURCE', 'EXTERNAL_API', 'DATABASE', 'MODEL', 'FUNCTION', 'EXPERIMENT', 'BILLING_CONFIG'])('%s 的行内发布提交同一草稿及所见摘要', async resourceType => {
  const get = vi.spyOn(api, 'getWorkspaceDraft').mockResolvedValue({ data: { id: '9007199254740993', action: 'CREATE', snapshotDigest: 'shown' } })
  const submit = vi.spyOn(api, 'submitGovernanceRequest').mockResolvedValue({ data: { status: 'PENDING' } })
  const wrapper = mount(Publish, { props: { resourceType, resourceId: '9007199254740992' } })
  await wrapper.setData({ state: { ready: true, requestStatus: 'EDITING', action: 'CREATE', owned: true } })
  await wrapper.vm.publish()
  expect(get).toHaveBeenCalledWith(resourceType, '9007199254740992')
  expect(submit).toHaveBeenCalledWith('9007199254740993', '从列表提交发布审批', 'shown')
  wrapper.unmount()
})

test.each([
  { requestStatus: 'PENDING', action: 'CREATE', owned: true },
  { requestStatus: 'EDITING', action: 'UPDATE', owned: false },
  { requestStatus: 'EDITING', action: 'DELETE', owned: true },
  { requestStatus: 'EDITING', action: 'DISABLE', owned: true },
])('审批中、他人草稿和删除/停用申请不能误作发布: %j', async state => {
  const get = vi.spyOn(api, 'getWorkspaceDraft')
  const wrapper = mount(Publish, { props: { resourceType: 'PROJECT', resourceId: 7 } })
  await wrapper.setData({ state: { ready: true, ...state } })
  expect(wrapper.vm.disabled).toBe(true)
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(false)
  await wrapper.vm.publish()
  expect(get).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('缺少提交权限时不发起发布，规则使用已有修订选择流程', async () => {
  setCurrentUser({ permissions: ['rule:view', 'rule:edit'] })
  const push = vi.fn()
  const wrapper = mount(Publish, { props: { resourceType: 'RULE', resourceId: 7 }, mocks: { $router: { push } } })
  await wrapper.setData({ state: { ready: true, hasRuleDraft: true } })
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(false)
  await wrapper.vm.publish()
  expect(push).not.toHaveBeenCalled()
  setCurrentUser({ permissions: ['rule:view', 'rule:edit', 'rule:submit', 'approval:submit'] })
  await flushPromises()
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(true)
  await wrapper.vm.publish()
  expect(push).toHaveBeenCalledWith({ path: '/rule/7', query: { focus: 'lifecycle' } })
  wrapper.unmount()
})

test.each(['PROJECT', 'RULE', 'MODEL', 'VARIABLE'])('%s 已生效且无草稿时不显示发布', async resourceType => {
  const wrapper = mount(Publish, { props: { resourceType, resourceId: 7 } })
  await wrapper.setData({ state: { ready: true, effectiveStatus: 'ACTIVE', requestStatus: 'APPROVED', hasRuleDraft: false, hasApprovedRuleRevision: false } })
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(false)
  wrapper.unmount()
})

test('规则有已批准待发布修订时仍显示入口，提交期间不会隐藏按钮', async () => {
  setCurrentUser({ permissions: ['rule:view', 'rule:edit', 'rule:submit'] })
  const wrapper = mount(Publish, { props: { resourceType: 'RULE', resourceId: 7 } })
  await wrapper.setData({ state: { ready: true, hasApprovedRuleRevision: true, requestStatus: 'APPROVED' } })
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(true)
  await wrapper.setData({ busy: true })
  expect(wrapper.find('[data-action="publish"]').exists()).toBe(true)
  expect(wrapper.vm.disabled).toBe(true)
  wrapper.unmount()
})

test('状态读取失败或尚未读取完成时不显示发布', async () => {
  const wrapper = mount(Publish, { props: { resourceType: 'PROJECT', resourceId: 7 } })
  for (const state of [{ ready: false }, { ready: false, error: true }, { ready: true, effectiveStatus: 'DELETED', requestStatus: 'EDITING', action: 'CREATE', owned: true }]) {
    await wrapper.setData({ state })
    expect(wrapper.find('[data-action="publish"]').exists()).toBe(false)
  }
  wrapper.unmount()
})
