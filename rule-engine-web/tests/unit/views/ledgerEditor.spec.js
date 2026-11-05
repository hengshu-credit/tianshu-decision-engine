import { shallowMount, flushPromises } from '@test-utils'
import LedgerEditor from '@/views/ledger/LedgerEditor.vue'
import * as api from '@/api/ledger'
import { confirmDesignerPathsCanClose, clearDesignerLeaveGuards } from '@/utils/designerLeaveGuard'
vi.mock('@/api/ledger', () => ({ getLedgerArticleDraft: vi.fn(), saveLedgerDraft: vi.fn(), publishLedgerArticle: vi.fn(), listLedgerEvents: vi.fn(), getLedgerEvent: vi.fn(), getLedgerVersions: vi.fn(), getLedgerVersionEvents: vi.fn(), restoreLedgerVersion: vi.fn(), listLedgerAssets: vi.fn(), uploadLedgerAsset: vi.fn(), updateLedgerAsset: vi.fn(), scanLedgerAsset: vi.fn(), deleteLedgerAssets: vi.fn(), getLedgerAssetReferences: vi.fn(), downloadLedgerAsset: vi.fn(), saveLedgerEvent: vi.fn() }))
vi.mock('@/utils/ledgerMarkdown', () => ({ Vditor: vi.fn(), loadLedgerMarkdown: vi.fn().mockResolvedValue(), ledgerEditorCdn: '/vendor/vditor', renderLedgerMarkdown: vi.fn().mockResolvedValue('') }))
const body = { title: '原文章', body: '原正文', category: 'WORK', scope: 'PROJECT', projectId: '1', eventIds: [], occurredAt: '2026-11-05T10:20:30', summary: '', changeSummary: '' }
function mount(id) {
  return shallowMount(LedgerEditor, { global: { mocks: { $route: { params: id ? { id } : {}, query: {}, path: '/ledger/articles/new' }, $store: { state: { currentProject: null } }, $router: { push: vi.fn().mockResolvedValue(), replace: vi.fn().mockResolvedValue() } } } })
}
beforeEach(() => { vi.clearAllMocks(); api.listLedgerEvents.mockResolvedValue({ data: [] }); api.getLedgerArticleDraft.mockResolvedValue({ data: { id: '7', version: 2, publishedVersion: 1, content: body } }) })
afterEach(() => clearDesignerLeaveGuards())

test('工作区刷新取消保留正文，浏览器关闭也保护未保存内容', async () => {
  const view = mount(); await flushPromises(); view.vm.updateBody('未保存正文')
  view.vm.$confirm.mockRejectedValue('cancel')
  expect(await confirmDesignerPathsCanClose(['/ledger/articles/new'])).toBe(false)
  expect(view.vm.content.body).toBe('未保存正文')
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
  view.unmount()
  expect(await confirmDesignerPathsCanClose(['/ledger/articles/new'])).toBe(true)
})

test('身份失效后迟到的草稿读取不能恢复原账户正文', async () => {
  let resolve
  api.getLedgerArticleDraft.mockReturnValue(new Promise(done => { resolve = done }))
  const view = mount('7'); view.vm.clearIdentity()
  resolve({ data: { version: 2, publishedVersion: 1, content: body } }); await flushPromises()
  view.vm.updateBody('已销毁编辑器的迟到同步')
  expect(view.vm.content.body).toBe('')
  expect(view.vm.events).toEqual([])
  expect(view.vm.dirty).toBe(false)
  view.unmount()
})

test('身份失效后迟到的草稿保存不能继续发布或跳转', async () => {
  let resolve
  api.saveLedgerDraft.mockReturnValue(new Promise(done => { resolve = done }))
  const view = mount('7'); await flushPromises()
  const pending = view.vm.publish(); view.vm.clearIdentity()
  resolve({ data: { id: '7', version: 3, publishedVersion: 1 } }); await pending
  expect(api.publishLedgerArticle).not.toHaveBeenCalled()
  expect(view.vm.$router.push).not.toHaveBeenCalled()
  expect(view.vm.content.body).toBe('')
  view.unmount()
})
test('发布失败时保留已保存草稿及正文，使用保存后取得的并发版本', async () => {
  api.saveLedgerDraft.mockResolvedValue({ data: { id: '7', version: 3, publishedVersion: 1 } }); api.publishLedgerArticle.mockRejectedValue(new Error('含待检素材'))
  const view = mount('7'); await flushPromises(); view.vm.updateBody('修改后的正文'); await view.setData({ publishDialog: true }); await view.vm.publish()
  expect(api.saveLedgerDraft).toHaveBeenCalledWith('7', expect.objectContaining({ version: 2, content: expect.objectContaining({ body: '修改后的正文' }) }))
  expect(api.publishLedgerArticle).toHaveBeenCalledWith('7', 3)
  expect(view.vm.publishDialog).toBe(true); expect(view.vm.content.body).toBe('修改后的正文'); expect(view.vm.version).toBe(3)
  expect(view.vm.$router.push).not.toHaveBeenCalled(); view.unmount()
})
test('保存草稿不发布，创建后使用服务器返回的文章 ID 继续编辑', async () => {
  api.saveLedgerDraft.mockResolvedValue({ data: { id: '12', version: 0, publishedVersion: 0 } })
  const view = mount(); await flushPromises(); await view.setData({ content: { ...body, scope: '', projectId: null } }); await view.vm.saveDraft()
  expect(api.saveLedgerDraft).toHaveBeenCalledWith(null, expect.objectContaining({ version: null }))
  expect(api.publishLedgerArticle).not.toHaveBeenCalled()
  expect(view.vm.$router.replace).toHaveBeenCalledWith('/ledger/articles/12/edit')
  view.unmount()
})
test('快速输入后立即保存直接读取编辑器当前正文，不依赖延迟的 input 同步', async () => {
  api.saveLedgerDraft.mockResolvedValue({ data: { id: '7', version: 3, publishedVersion: 1 } })
  const view = mount('7'); await flushPromises()
  view.vm.$refs.markdown.getValue = () => '尚未触发延迟同步的最新正文'
  await view.vm.saveDraft()
  expect(api.saveLedgerDraft).toHaveBeenCalledWith('7', expect.objectContaining({ content: expect.objectContaining({ body: '尚未触发延迟同步的最新正文' }) }))
  view.unmount()
})
