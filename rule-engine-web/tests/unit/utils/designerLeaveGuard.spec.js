import {
  clearDesignerLeaveGuards,
  confirmDesignerPathsCanClose,
  registerDesignerLeaveGuard,
} from '@/utils/designerLeaveGuard'

describe('designerLeaveGuard', () => {
  beforeEach(() => clearDesignerLeaveGuards())

  test('工作区关闭脏设计器前逐个执行异步确认', async () => {
    const confirm = vi.fn().mockResolvedValue(false)
    const unregister = registerDesignerLeaveGuard('/designer/table/30', confirm)

    await expect(
      confirmDesignerPathsCanClose(['/rule', '/designer/table/30'])
    ).resolves.toBe(false)
    expect(confirm).toHaveBeenCalledTimes(1)

    unregister()
    await expect(
      confirmDesignerPathsCanClose(['/designer/table/30'])
    ).resolves.toBe(true)
  })
  test('批量关闭取消时撤销前面页签的离开许可', async () => {
    const abort = vi.fn()
    registerDesignerLeaveGuard('/database/1', async () => true, abort)
    registerDesignerLeaveGuard('/model/2', async () => false)
    expect(await confirmDesignerPathsCanClose(['/database/1', '/model/2'])).toBe(false)
    expect(abort).toHaveBeenCalledOnce()
  })
})
