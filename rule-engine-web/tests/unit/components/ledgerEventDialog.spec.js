import { shallowMount, flushPromises } from '@test-utils'
import LedgerEventDialog from '@/components/ledger/LedgerEventDialog.vue'
import { saveLedgerEvent } from '@/api/ledger'

vi.mock('@/api/ledger', () => ({ saveLedgerEvent: vi.fn() }))

test('事件保存锁覆盖整个请求，失败保留输入并允许重试', async () => {
  let reject
  saveLedgerEvent.mockReturnValueOnce(new Promise((_, fail) => { reject = fail }))
  const view = shallowMount(LedgerEventDialog, { props: { modelValue: true, scope: 'GLOBAL' } })
  view.vm.form.title = '验收事件'
  const first = view.vm.save()
  await view.vm.save(); await view.vm.close()
  expect(saveLedgerEvent).toHaveBeenCalledTimes(1)
  expect(view.emitted('update:modelValue')).toBeUndefined()
  reject(new Error('暂时不可用')); await first
  expect(view.vm.form.title).toBe('验收事件')
  saveLedgerEvent.mockResolvedValueOnce({ data: { id: '91' } })
  await view.vm.save()
  expect(view.emitted('saved')).toEqual([[{ id: '91' }]])
  view.unmount()
})

test('取消关闭保留事件输入，确认放弃才关闭；重开后旧保存响应无效', async () => {
  const view = shallowMount(LedgerEventDialog, { props: { modelValue: true, scope: 'GLOBAL' } })
  view.vm.form.title = '未保存事件'
  view.vm.$confirm.mockRejectedValueOnce('cancel')
  await view.vm.close()
  expect(view.emitted('update:modelValue')).toBeUndefined()
  expect(view.vm.form.title).toBe('未保存事件')
  view.vm.$confirm.mockResolvedValueOnce(true)
  await view.vm.close()
  expect(view.emitted('update:modelValue')).toEqual([[false]])
  let resolve
  saveLedgerEvent.mockReturnValueOnce(new Promise(done => { resolve = done }))
  const pending = view.vm.save()
  await view.setProps({ modelValue: false }); await view.setProps({ modelValue: true })
  resolve({ data: { id: 'old' } }); await pending; await flushPromises()
  expect(view.emitted('saved')).toBeUndefined()
  expect(view.vm.form.title).toBe('')
  view.unmount()
})
