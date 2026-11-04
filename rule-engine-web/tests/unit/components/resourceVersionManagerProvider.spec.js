import { shallowMount, flushPromises } from '@test-utils'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'

const workspace = {
  latestVersionId: '22', actorUserId: '3', canEdit: true, articleVersion: 4, scope: 'PROJECT', projectId: '1',
  versions: [{ versionId: '22', version: 2, revision: 1, latest: true }, { versionId: '21', version: 1, revision: 1 }],
  history: [{ id: '22', versionNo: 2, revisionNo: 1, snapshotJson: '{"body":"第二版"}' }, { id: '21', versionNo: 1, revisionNo: 1, snapshotJson: '{"body":"第一版"}' }],
  drafts: [{ id: '7', status: 'EDITING', actorUserId: '3', draftSnapshotJson: '{"body":"私有草稿"}' }],
}
function mount(provider) { return shallowMount(ResourceVersionManager, { props: { resourceType: 'LEDGER_ARTICLE', resourceId: '7', provider } }) }
test('台账复用版本工作区，独立服务提供不可变版本且不启用治理审批或迁移', async () => {
  const provider = { load: vi.fn().mockResolvedValue({ data: workspace }), events: vi.fn().mockResolvedValue({ data: [] }), openEditor: vi.fn().mockResolvedValue() }
  const view = mount(provider); await view.vm.open()
  expect(provider.load).toHaveBeenCalledWith('7')
  expect(view.vm.snapshot).toBe('{"body":"第二版"}')
  expect(view.vm.canSubmit).toBe(false); expect(view.vm.canExport).toBe(false)
  await view.setData({ selected: 'r:21' }); await view.vm.edit()
  expect(provider.openEditor).toHaveBeenCalledWith(expect.objectContaining({ resourceId: '7', versionId: '21', articleVersion: 4 }))
  expect(view.vm.editing).toBe(false)
  view.unmount()
})
test('台账私有草稿通过账户 ID 判断可编辑，不能靠同名账户获得权限', async () => {
  const provider = { load: vi.fn().mockResolvedValue({ data: workspace }), events: vi.fn().mockResolvedValue({ data: [] }), openEditor: vi.fn() }
  const view = mount(provider); await view.vm.open(); await view.setData({ selected: 'd:7' })
  expect(view.vm.editableDraft).toBe(true)
  await view.setData({ workspace: { ...workspace, actorUserId: '9', actor: 'same-name', drafts: [{ ...workspace.drafts[0], applicant: 'same-name' }] } })
  expect(view.vm.editableDraft).toBe(false)
  view.unmount()
})
test('公共版本组件提供有效上下文检查，资源切换后不会恢复或导航到旧文章', async () => {
  let confirm
  const mutate = vi.fn()
  const provider = { load: vi.fn().mockResolvedValue({ data: workspace }), events: vi.fn().mockResolvedValue({ data: [] }), openEditor: async ({ isCurrent }) => { await new Promise(resolve => { confirm = resolve }); if (isCurrent()) mutate() } }
  const view = mount(provider); await view.vm.open(); const pending = view.vm.edit(); await flushPromises()
  await view.setProps({ resourceId: '8' }); confirm(); await pending
  expect(mutate).not.toHaveBeenCalled(); expect(view.emitted('changed')).toBeUndefined()
  view.unmount()
})
