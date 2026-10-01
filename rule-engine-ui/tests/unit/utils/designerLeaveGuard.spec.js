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
})
