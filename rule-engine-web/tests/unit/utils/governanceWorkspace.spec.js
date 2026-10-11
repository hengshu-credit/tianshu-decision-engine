import { completeResourceChange, isNewContentRequest } from '@/utils/governanceWorkspace'

describe('保存、发布和删除的结果边界', () => {
  test('识别统一新建和原生规则首次发布，已发布后的修改不被当成新增', () => {
    expect(isNewContentRequest({ action: 'CREATE', resourceType: 'VARIABLE' })).toBe(true)
    expect(isNewContentRequest({ action: 'UPDATE', resourceType: 'RULE', submittedSnapshotJson: '{"status":0,"publishedVersion":null}' })).toBe(true)
    expect(isNewContentRequest({ action: 'UPDATE', resourceType: 'RULE', submittedSnapshotJson: '{"status":0,"publishedVersion":2}' })).toBe(false)
    expect(isNewContentRequest({ action: 'UPDATE', resourceType: 'VARIABLE' })).toBe(false)
  })
  function view() { return { $message: { success: vi.fn() }, $router: { push: vi.fn() } } }
  test('保存新增草稿留在模块，刷新草稿行，不送审', async () => {
    const page = view(), reload = vi.fn()
    await completeResourceChange(page, { data: { id: 18, resourceId: 8, action: 'CREATE', status: 'EDITING' } }, reload)
    expect(page.$router.push).not.toHaveBeenCalled()
    expect(page.$message.success.mock.calls[0][0]).toContain('草稿已保存')
    expect(reload).toHaveBeenCalledOnce()
  })
  test('无依赖的未发布草稿直接删除，刷新后不进入审批', async () => {
    const page = view(), reload = vi.fn()
    await completeResourceChange(page, { data: { deleted: true, action: 'DELETE', id: null } }, reload)
    expect(page.$router.push).not.toHaveBeenCalled()
    expect(page.$message.success.mock.calls[0][0]).toContain('无需审批')
    expect(reload).toHaveBeenCalledOnce()
  })
  test('已生效内容的删除申请必须进入审批，不宣称已经删除', async () => {
    const page = view()
    await completeResourceChange(page, { data: { deleted: false, action: 'DELETE', id: 20 } })
    expect(page.$router.push).toHaveBeenCalledWith('/approval/20')
    expect(page.$message.success.mock.calls[0][0]).toContain('审批通过后生效')
  })
})
