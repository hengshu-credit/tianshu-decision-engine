import { shallowMount, flushPromises } from '@test-utils'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'
import * as versions from '@/api/resourceVersions'
import * as governance from '@/api/governance'

const workspace = {
  actor: 'owner', latestVersionId: '2',
  versions: [{ versionId: '3', version: 1, revision: 3 }, { versionId: '2', version: 2, revision: 2, latest: true }],
  history: [{ id: '1', versionNo: 1, revisionNo: 1, snapshotJson: '{"defaultValue":"old"}' }, { id: '2', versionNo: 2, revisionNo: 2, snapshotJson: '{"defaultValue":"latest"}' }, { id: '3', versionNo: 1, revisionNo: 3, snapshotJson: '{"defaultValue":"fixed"}' }],
  drafts: [],
}
const mount = () => shallowMount(ResourceVersionManager, { props: { resourceType: 'VARIABLE', resourceId: '7' } })
beforeEach(() => {
  vi.spyOn(versions, 'getResourceVersions').mockResolvedValue({ data: structuredClone(workspace) })
  vi.spyOn(versions, 'getResourceVersionEvents').mockResolvedValue({ data: [] })
  vi.spyOn(versions, 'getVersionDeletionImpact').mockResolvedValue({ data: { allowed: true, expectedSnapshotId: '3', issues: [] } })
})
afterEach(() => vi.restoreAllMocks())

test('最新版读取生产指针，覆盖旧版本不改变默认预览', async () => {
  const view = mount(); await view.vm.open()
  expect(view.vm.snapshot).toBe('{"defaultValue":"latest"}')
  await view.setData({ selected: 'r:3' }); expect(view.vm.snapshot).toBe('{"defaultValue":"fixed"}')
  await view.setData({ selected: 'r:1' }); expect(view.vm.snapshot).toBe('{"defaultValue":"old"}')
  view.unmount()
})

test('历史编辑新增草稿同时携带来源和覆盖目标的精确修订', async () => {
  const create = vi.spyOn(versions, 'createVersionDraft').mockResolvedValue({ data: { id: '9' } })
  const view = mount(); await view.vm.open(); await view.setData({ selected: 'r:1' }); view.vm.edit()
  await view.setData({ publishMode: 'OVERWRITE', targetVersionId: '2', editJson: '{"defaultValue":"edited"}', changeSummary: '修正阈值' })
  await view.vm.save()
  expect(create).toHaveBeenCalledWith('VARIABLE', '7', { snapshotJson: '{"defaultValue":"edited"}', changeSummary: '修正阈值', sourceVersionId: '1', publishMode: 'OVERWRITE', targetVersionId: '2', expectedTargetSnapshotId: '2' })
  expect(view.vm.editing).toBe(false); view.unmount()
})

test('无目标的覆盖与无效内容都不能保存', async () => {
  const create = vi.spyOn(versions, 'createVersionDraft')
  const view = mount(); await view.vm.open(); view.vm.edit()
  await view.setData({ publishMode: 'OVERWRITE' }); await view.vm.save(); expect(create).not.toHaveBeenCalled()
  await view.setData({ publishMode: 'NEW', editJson: '[]' }); await view.vm.save(); expect(create).not.toHaveBeenCalled(); view.unmount()
})

test('已有草稿保存携带摘要，失败时保留编辑内容', async () => {
  const save = vi.spyOn(governance, 'saveGovernanceDraft').mockRejectedValue(new Error('草稿已变更'))
  const view = mount(); await view.vm.open()
  await view.setData({ workspace: { ...workspace, drafts: [{ id: '9', status: 'EDITING', applicant: 'owner', snapshotDigest: 'digest', draftSnapshotJson: '{"a":1}' }] }, selected: 'd:9' })
  view.vm.edit(); await view.setData({ editJson: '{"a":2}' }); await view.vm.save()
  expect(save).toHaveBeenCalledWith('9', { snapshotJson: '{"a":2}', changeSummary: '', expectedSnapshotDigest: 'digest' })
  expect(view.vm.editing).toBe(true); expect(view.vm.editJson).toBe('{"a":2}'); view.unmount()
})

test('资源切换使迟到的版本响应失效', async () => {
  let finish
  vi.spyOn(versions, 'getResourceVersions').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const view = mount(); const pending = view.vm.open(); await view.setProps({ resourceId: '8' })
  finish({ data: workspace }); await pending; expect(view.vm.workspace).toEqual({}); expect(view.vm.visible).toBe(false); view.unmount()
})

test('草稿删除后不能因相同数字 ID 显示历史版本', async () => {
  const view = mount(); await view.vm.open(); await view.setData({ selected: 'd:2' })
  expect(view.vm.snapshot).toBe(''); view.unmount()
})

test('确认期间切换草稿仍只提交原草稿', async () => {
  let confirm
  const submit = vi.spyOn(governance, 'submitGovernanceRequest').mockResolvedValue({})
  const view = mount(); await view.vm.open()
  view.vm.$confirm = vi.fn(() => new Promise(resolve => { confirm = resolve }))
  await view.setData({ workspace: { ...workspace, drafts: [{ id: '9', snapshotDigest: 'first', changeSummary: '原草稿' }, { id: '10', snapshotDigest: 'second' }] }, selected: 'd:9' })
  const pending = view.vm.publish(); await view.setData({ selected: 'd:10' }); confirm(); await pending
  expect(submit).toHaveBeenCalledWith('9', '原草稿', 'first'); view.unmount()
})

test('确认期间切换资源不发起原资源操作', async () => {
  let confirm
  const remove = vi.spyOn(versions, 'deleteResourceVersion').mockResolvedValue({})
  const view = mount(); await view.vm.open()
  view.vm.$confirm = vi.fn(() => new Promise(resolve => { confirm = resolve }))
  const pending = view.vm.remove(workspace.versions[0]); await flushPromises(); await view.setProps({ resourceId: '8' }); confirm(); await pending
  expect(remove).not.toHaveBeenCalled(); view.unmount()
})

test('删除前显示具体版本依赖阻碍，不进入确认或发送删除请求', async () => {
  vi.spyOn(versions, 'getVersionDeletionImpact').mockResolvedValue({ data: { allowed: false, expectedSnapshotId: '3', issues: [{ message: '业务 v1 仍被规则 #9 引用' }] } })
  const remove = vi.spyOn(versions, 'deleteResourceVersion').mockResolvedValue({})
  const view = mount(); await view.vm.open(); view.vm.$confirm = vi.fn().mockResolvedValue()
  await view.vm.remove(workspace.versions[0])
  expect(view.vm.$confirm).not.toHaveBeenCalled(); expect(remove).not.toHaveBeenCalled()
  expect(view.vm.$message.error).toHaveBeenCalledWith('业务 v1 仍被规则 #9 引用'); view.unmount()
})

test('删除预检期间资源切换会取消后续确认', async () => {
  let finish
  vi.spyOn(versions, 'getVersionDeletionImpact').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const remove = vi.spyOn(versions, 'deleteResourceVersion').mockResolvedValue({})
  const view = mount(); await view.vm.open(); view.vm.$confirm = vi.fn().mockResolvedValue()
  const pending = view.vm.remove(workspace.versions[0]); await view.setProps({ resourceId: '8' })
  finish({ data: { allowed: true, expectedSnapshotId: '3', issues: [] } }); await pending
  expect(view.vm.$confirm).not.toHaveBeenCalled(); expect(remove).not.toHaveBeenCalled(); view.unmount()
})

test('保存期间切换资源不把原草稿显示到新资源', async () => {
  let finish
  vi.spyOn(versions, 'createVersionDraft').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const view = mount(); await view.vm.open(); view.vm.edit()
  const pending = view.vm.save(); await view.setProps({ resourceId: '8' }); finish({ data: { id: '9' } }); await pending
  expect(view.vm.workspace).toEqual({}); expect(view.emitted('changed')).toBeUndefined(); view.unmount()
})
