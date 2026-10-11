import { flushPromises } from '@test-utils'
import request from '@/api/request'
import { watchResourceState } from '@/utils/workspaceDraftState'
import { notifyDraftChange } from '@/utils/governanceWorkspace'

afterEach(() => vi.clearAllMocks())

test('按类型合并状态请求，每批至多100项，发布按钮和状态标签共用结果且ID不丢精度', async () => {
  request.get.mockImplementation(async (_, config) => ({ data: config.params.ids.split(',').map(resourceId => ({ resourceId, effectiveStatus: 'DRAFT', requestStatus: 'EDITING', owned: true })) }))
  const listeners = [], release = []
  for (let n = 0; n < 101; n++) {
    const id = String(9007199254740992n + BigInt(n)), listener = vi.fn()
    listeners.push(listener); release.push(watchResourceState('VARIABLE', id, listener))
  }
  const shared = vi.fn(); release.push(watchResourceState('VARIABLE', '9007199254740992', shared))
  await flushPromises()
  expect(request.get).toHaveBeenCalledTimes(2)
  expect(request.get.mock.calls.map(([, config]) => config.params.ids.split(',').length)).toEqual([100, 1])
  expect(shared).toHaveBeenLastCalledWith(expect.objectContaining({ resourceId: '9007199254740992', ready: true, requestStatus: 'EDITING' }))
  expect(listeners.every(listener => listener.mock.lastCall[0].ready)).toBe(true)
  release.forEach(stop => stop())
})

test('刷新后旧响应不能覆盖最新审批状态，读取失败不能当作无草稿', async () => {
  let finish
  request.get.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    .mockResolvedValueOnce({ data: [{ resourceId: '7', requestStatus: 'PENDING' }] })
    .mockRejectedValueOnce(new Error('network'))
  const listener = vi.fn(), stop = watchResourceState('PROJECT', 7, listener)
  await flushPromises()
  notifyDraftChange(); await flushPromises()
  finish({ data: [{ resourceId: '7', requestStatus: 'EDITING' }] }); await flushPromises()
  expect(listener).toHaveBeenLastCalledWith(expect.objectContaining({ requestStatus: 'PENDING', ready: true }))
  notifyDraftChange(); await flushPromises()
  expect(listener).toHaveBeenLastCalledWith({ ready: false, error: true })
  stop()
})
