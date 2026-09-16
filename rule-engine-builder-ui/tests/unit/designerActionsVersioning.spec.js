import { mount, flushPromises } from '@test-utils'
import * as api from '@/api/definition'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'

const draft = { id: '9007199254740993', state: 'DRAFT', lockVersion: 4, modelJson: '{"script":"old"}' }
async function host(source = draft) {
  api.listRuleRevisions.mockResolvedValue({ data: [source, { ...draft, id: '77' }] })
  api.listPublishedVersions.mockResolvedValue({ data: [] })
  api.getRuleRevision.mockResolvedValue({ data: source })
  const wrapper = mount({
    mixins: [ruleDraftMixin], template: '<div />',
    data: () => ({ modelJson: '{"script":"new"}' }),
    methods: { serializeDesignerDraft() { return this.modelJson } },
  }, { mocks: { $route: { params: { id: '30' }, query: { projectId: '9', sourceType: 'REVISION', sourceId: source.id } }, $router: { replace: vi.fn(), push: vi.fn() } } })
  await flushPromises()
  wrapper.vm.initializeDesignerDraftTracking(source.modelJson)
  wrapper.vm.captureDesignerDraftState()
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  api.compileDesignerModel.mockResolvedValue({ data: { compileSuccess: true, compiledScript: 'new;', preflightReport: { valid: true, errors: [] } } })
  api.saveDesignerDraft.mockImplementation(async (_id, data) => ({ data: { revision: { ...draft, id: data.saveMode === 'NEW' ? '88' : draft.id, lockVersion: 5, modelJson: data.modelJson }, compileSuccess: true, issues: [] } }))
})
test('四个独立动作按顺序显示，未保存未编译仍可测试，状态不占按钮行', async () => {
  const wrapper = mount(RuleDesignerActionBar, { props: { canEdit: true, canTest: true, state: 'DIRTY' } })
  expect(wrapper.findAll('[data-action]').map(button => button.attributes('data-action'))).toEqual(['compile', 'save', 'publish', 'test'])
  expect(wrapper.get('[data-action="test"]').attributes('disabled')).toBeUndefined()
  for (const action of ['compile', 'save', 'publish', 'test']) {
    await wrapper.get(`[data-action="${action}"]`).trigger('click')
    expect(wrapper.emitted(action)).toHaveLength(1)
  }
  expect(wrapper.find('[role="status"]').exists()).toBe(false)
})
test('当前配置纯编译，无保存，未保存配置可测试', async () => {
  const wrapper = await host()
  await wrapper.vm.compileDesignerDraft()
  expect(api.compileDesignerModel).toHaveBeenCalledWith('30', { modelJson: '{"script":"new"}', sourceType: 'REVISION', sourceId: draft.id })
  expect(api.saveDesignerDraft).not.toHaveBeenCalled()
  expect(wrapper.vm.designerCompileResult.compiledScript).toBe('new;')
  expect(wrapper.vm.designerHasUnsavedChanges).toBe(true)
  expect(wrapper.vm.designerCanTest).toBe(true)
  wrapper.unmount()
})

test('编译失败保留完整报告交给弹窗，不重复弹出错误提示', async () => {
  const wrapper = await host()
  const report = { valid: false, errors: [{ code: 'COMPILE_FAILED', message: '语法错误', path: '$.script' }], warnings: [] }
  api.compileDesignerModel.mockResolvedValueOnce({ data: { compileSuccess: false, compileMessage: '语法错误', preflightReport: report } })
  await wrapper.vm.compileDesignerDraft()
  expect(wrapper.vm.designerValidationReport).toEqual(report)
  expect(wrapper.vm.designerActionState).toBe('CHECK_FAILED')
  expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
  expect(api.saveDesignerDraft).not.toHaveBeenCalled()
  wrapper.unmount()
})

test('现有规则编辑角色能够测试，不要求系统不存在的执行权限', async () => {
  setCurrentUser({ permissions: ['rule:view', 'rule:edit'] })
  try {
    const wrapper = await host()
    expect(wrapper.vm.designerCanTest).toBe(true)
    wrapper.unmount()
  } finally { clearCurrentUser() }
})
test.each(['NEW', 'OVERWRITE'])('保存选择 %s 使用稳定 ID 与精确锁', async mode => {
  const wrapper = await host()
  const pending = wrapper.vm.saveDraftModel(wrapper.vm.modelJson)
  await flushPromises()
  expect(api.saveDesignerDraft).not.toHaveBeenCalled()
  wrapper.vm.resolveDesignerChoice({ action: 'save', saveMode: mode })
  await pending
  const body = api.saveDesignerDraft.mock.calls[0][1]
  expect(body).toMatchObject({ saveMode: mode, sourceType: 'REVISION', sourceId: draft.id, modelJson: '{"script":"new"}', requestId: expect.any(String) })
  if (mode === 'OVERWRITE') expect(body).toMatchObject({ revisionId: draft.id, lockVersion: 4 })
  else { expect(body).not.toHaveProperty('revisionId'); expect(body).not.toHaveProperty('lockVersion') }
  expect(wrapper.vm.viewRevision.id).toBe(mode === 'NEW' ? '88' : draft.id)
  wrapper.unmount()
})
test.each(['save', 'discard', 'cancel'])('有修改切版 %s 保留目标并使用同一弹窗', async action => {
  const wrapper = await host()
  const pending = wrapper.vm.switchDesignerSource('REVISION:77')
  await flushPromises()
  expect(wrapper.vm.designerChoice.kind).toBe('switch')
  expect(wrapper.vm.designerChoice.canOverwrite).toBe(true)
  wrapper.vm.resolveDesignerChoice({ action, saveMode: 'NEW' })
  await pending
  expect(api.saveDesignerDraft).toHaveBeenCalledTimes(action === 'save' ? 1 : 0)
  if (action === 'cancel') {
    expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
    expect(wrapper.vm.modelJson).toBe('{"script":"new"}')
  } else expect(wrapper.vm.$router.replace).toHaveBeenLastCalledWith({ query: { projectId: '9', sourceType: 'REVISION', sourceId: '77' } })
  wrapper.unmount()
})
test('保存失败停留，已通知错误不重复提示，返回的 Promise 不拒绝', async () => {
  const wrapper = await host()
  api.saveDesignerDraft.mockRejectedValue(new Error('并发修改'))
  const pending = wrapper.vm.switchDesignerSource('REVISION:77')
  await flushPromises()
  wrapper.vm.resolveDesignerChoice({ action: 'save', saveMode: 'OVERWRITE' })
  await expect(pending).resolves.toBe(false)
  expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
  expect(wrapper.vm.modelJson).toBe('{"script":"new"}')
  const error = Object.assign(new Error('已提示'), { requestErrorNotified: true })
  wrapper.vm.$message.error.mockClear()
  await wrapper.vm.runDesignerAction(async () => { throw error })
  expect(wrapper.vm.$message.error).not.toHaveBeenCalled()
  wrapper.unmount()
})
test('编译旧配置响应不能覆盖后来编辑', async () => {
  const wrapper = await host()
  let resolve
  api.compileDesignerModel.mockReturnValue(new Promise(done => { resolve = done }))
  const pending = wrapper.vm.compileDesignerDraft()
  await flushPromises()
  wrapper.vm.modelJson = '{"script":"newer"}'
  resolve({ data: { compileSuccess: true, compiledScript: 'old;', preflightReport: { valid: true } } })
  await pending
  expect(wrapper.vm.designerCompileResult).toBeNull()
  expect(wrapper.vm.designerHasUnsavedChanges).toBe(true)
  wrapper.unmount()
})
test('重试相同失败请求复用幂等键', async () => {
  const wrapper = await host()
  api.saveDesignerDraft.mockRejectedValueOnce(new Error('网络中断'))
  await expect(wrapper.vm.saveDraftModel(wrapper.vm.modelJson, { saveMode: 'NEW' })).rejects.toThrow('网络中断')
  const key = api.saveDesignerDraft.mock.calls[0][1].requestId
  await wrapper.vm.saveDraftModel(wrapper.vm.modelJson, { saveMode: 'NEW' })
  expect(api.saveDesignerDraft.mock.calls[1][1].requestId).toBe(key)
  wrapper.unmount()
})

test('普通 HTTP 控制台缺少 randomUUID 时仍能保存并复用幂等键', async () => {
  const wrapper = await host()
  const getRandomValues = globalThis.crypto.getRandomValues.bind(globalThis.crypto)
  vi.stubGlobal('crypto', { getRandomValues })
  try {
    await wrapper.vm.saveDraftModel(wrapper.vm.modelJson, { saveMode: 'NEW' })
    expect(api.saveDesignerDraft.mock.calls[0][1].requestId).toMatch(/^[a-f0-9]{32}$/)
  } finally { vi.unstubAllGlobals(); wrapper.unmount() }
})
test('删除其他草稿不改变当前修改，只允许 DRAFT', async () => {
  const wrapper = await host()
  await wrapper.vm.deleteDesignerSource({ value: 'REVISION:77', state: 'DRAFT', id: '77', lockVersion: 4 })
  expect(api.deleteDesignerDraft).toHaveBeenCalledWith('30', '77', 4)
  expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
  expect(wrapper.vm.modelJson).toBe('{"script":"new"}')
  await wrapper.vm.deleteDesignerSource({ value: 'VERSION:81', state: 'VERSION', id: '81' })
  expect(api.deleteDesignerDraft).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})

test('跨规则跳转后晚到的保存结果不改新页面路由', async () => {
  const wrapper = await host()
  let resolve
  api.saveDesignerDraft.mockReturnValueOnce(new Promise(done => { resolve = done }))
  const pending = wrapper.vm.saveDraftModel(wrapper.vm.modelJson, { saveMode: 'NEW' })
  await flushPromises()
  wrapper.vm.$route.params.id = '31'
  resolve({ data: { revision: { ...draft, id: '88' }, compileSuccess: true } })
  await pending
  expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
  expect(wrapper.vm.viewRevision.id).toBe(draft.id)
  wrapper.unmount()
})

test('保存途中编辑不能标记新内容已保存', async () => {
  const wrapper = await host()
  let resolve
  api.saveDesignerDraft.mockReturnValueOnce(new Promise(done => { resolve = done }))
  const pending = wrapper.vm.saveDraftModel(wrapper.vm.modelJson, { saveMode: 'NEW' })
  await flushPromises()
  wrapper.vm.modelJson = '{"script":"newer"}'
  resolve({ data: { revision: { ...draft, id: '88', modelJson: '{"script":"new"}' }, compileSuccess: true } })
  await pending
  expect(wrapper.vm.modelJson).toBe('{"script":"newer"}')
  expect(wrapper.vm.designerHasUnsavedChanges).toBe(true)
  wrapper.unmount()
})

test('发布覆盖请求使用已保存修订锁和业务绑定代次，取消不提交', async () => {
  const wrapper = await host()
  wrapper.vm.modelJson = draft.modelJson
  api.listPublishedVersions.mockResolvedValue({ data: [{ id: '81', version: 2, bindingId: '8001', generation: 3 }] })
  api.publishDesignerDraft.mockResolvedValue({ data: { revision: { ...draft, state: 'REVIEW' }, approvalRequestId: '19' } })
  const pending = wrapper.vm.handlePublish()
  await flushPromises()
  expect(wrapper.vm.designerChoice.kind).toBe('publish')
  wrapper.vm.resolveDesignerChoice({ action: 'publish', publishMode: 'OVERWRITE', targetVersionId: '8001', comment: '更新额度' })
  await pending
  expect(api.publishDesignerDraft).toHaveBeenCalledWith('30', { revisionId: draft.id, lockVersion: 4, publishMode: 'OVERWRITE', targetVersionId: '8001', targetGeneration: 3, comment: '更新额度' })
  expect(api.saveDesignerDraft).not.toHaveBeenCalled()
  wrapper.unmount()
})
