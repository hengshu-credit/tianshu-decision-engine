import BillingList from '@/views/billing/BillingList.vue'
import * as billingApi from '@/api/billing'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function context() {
  const ctx = {
    ...BillingList.data.call({ emptyConfigForm: BillingList.methods.emptyConfigForm }),
    activeTab: 'summary', refreshDate: '2026-10-11',
    $message: { success: vi.fn(), error: vi.fn() },
  }
  for (const [name, method] of Object.entries(BillingList.methods)) ctx[name] = method.bind(ctx)
  for (const [name, getter] of Object.entries(BillingList.computed || {})) Object.defineProperty(ctx, name, { get: getter.bind(ctx) })
  ctx.loadSummaries = vi.fn().mockResolvedValue()
  return ctx
}

describe('BillingList 后台汇总任务', () => {
  let ctx
  beforeEach(() => {
    vi.useFakeTimers()
    billingApi.refreshBillingSummary.mockReset()
    billingApi.getBillingSummaryRefreshTask.mockReset()
    billingApi.getLatestBillingSummaryRefresh.mockReset().mockResolvedValue({ data: null })
    ctx = context()
  })
  afterEach(() => { ctx.stopSummaryRefresh?.(); vi.useRealTimers(); vi.clearAllMocks() })
  const task = (status, extra = {}) => ({ taskId: 'task-one', summaryDate: '2026-10-11', status, ...extra })

  test('提交中防重复点击，排队响应不能报为已完成', async () => {
    const pending = deferred()
    billingApi.refreshBillingSummary.mockReturnValue(pending.promise)
    const first = ctx.handleRefreshSummary(), second = ctx.handleRefreshSummary()
    pending.resolve({ data: task('PENDING') })
    await Promise.all([first, second])
    expect(billingApi.refreshBillingSummary).toHaveBeenCalledTimes(1)
    expect(ctx.summaryRefreshTask.status).toBe('PENDING')
    expect(ctx.$message.success).not.toHaveBeenCalled()
    expect(ctx.loadSummaries).not.toHaveBeenCalled()
  })

  test('排队和运行中只轮询原任务，完成后才刷新汇总表', async () => {
    billingApi.refreshBillingSummary.mockResolvedValue({ data: task('PENDING') })
    billingApi.getBillingSummaryRefreshTask.mockResolvedValueOnce({ data: task('RUNNING') })
      .mockResolvedValueOnce({ data: task('SUCCEEDED', { summaryCount: 5, recordCount: 1000000 }) })
    await ctx.handleRefreshSummary()
    await vi.advanceTimersByTimeAsync(1500)
    expect(ctx.summaryRefreshTask.status).toBe('RUNNING')
    expect(ctx.loadSummaries).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1500)
    expect(ctx.summaryRefreshTask).toMatchObject({ status: 'SUCCEEDED', summaryCount: 5, recordCount: 1000000 })
    expect(ctx.loadSummaries).toHaveBeenCalledOnce()
    expect(billingApi.refreshBillingSummary).toHaveBeenCalledOnce()
    expect(ctx.$message.success).toHaveBeenCalledWith(expect.stringContaining('汇总已完成'))
    expect(vi.getTimerCount()).toBe(0)
  })

  test('进度查询失败不重新提交或宣称任务失败，重试仍查询同一任务', async () => {
    billingApi.refreshBillingSummary.mockResolvedValue({ data: task('RUNNING') })
    billingApi.getBillingSummaryRefreshTask.mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce({ data: task('SUCCEEDED', { summaryCount: 2, recordCount: 10 }) })
    await ctx.handleRefreshSummary()
    await vi.advanceTimersByTimeAsync(1500)
    expect(ctx.summaryRefreshTask.status).toBe('RUNNING')
    expect(ctx.summaryRefreshError).toContain('进度读取失败')
    expect(vi.getTimerCount()).toBe(0)
    await ctx.pollSummaryRefresh()
    expect(billingApi.getBillingSummaryRefreshTask).toHaveBeenLastCalledWith('task-one')
    expect(billingApi.refreshBillingSummary).toHaveBeenCalledOnce()
    expect(ctx.summaryRefreshError).toBe('')
    expect(ctx.summaryRefreshTask.status).toBe('SUCCEEDED')
  })

  test('切换刷新日期后迟到的进度不能覆盖新日期', async () => {
    const old = deferred()
    billingApi.getLatestBillingSummaryRefresh.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce({ data: task('SUCCEEDED', { taskId: 'task-two', summaryDate: '2026-10-12' }) })
    const first = ctx.loadSummaryRefreshTask()
    ctx.refreshDate = '2026-10-12'
    await ctx.loadSummaryRefreshTask()
    old.resolve({ data: task('RUNNING') })
    await first
    expect(ctx.summaryRefreshTask.taskId).toBe('task-two')
    expect(billingApi.getLatestBillingSummaryRefresh).toHaveBeenLastCalledWith({ summaryDate: '2026-10-12' })
  })

  test('卸载后清理轮询，迟到的完成响应不触发通知或列表加载', async () => {
    const pending = deferred()
    billingApi.refreshBillingSummary.mockResolvedValue({ data: task('RUNNING') })
    billingApi.getBillingSummaryRefreshTask.mockReturnValue(pending.promise)
    await ctx.handleRefreshSummary()
    await vi.advanceTimersByTimeAsync(1500)
    BillingList.beforeUnmount.call(ctx)
    pending.resolve({ data: task('SUCCEEDED', { summaryCount: 2 }) })
    await Promise.resolve(); await Promise.resolve()
    expect(ctx.$message.success).not.toHaveBeenCalled()
    expect(ctx.loadSummaries).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })

  test('服务端任务失败保留旧汇总，允许发起新任务', async () => {
    ctx.summaryList = [{ id: 99 }]
    billingApi.refreshBillingSummary.mockResolvedValueOnce({ data: task('FAILED', { errorMessage: '汇总处理失败，原汇总保留' }) })
      .mockResolvedValueOnce({ data: task('PENDING', { taskId: 'retry-task' }) })
    await ctx.handleRefreshSummary()
    expect(ctx.summaryRefreshTask.status).toBe('FAILED')
    expect(ctx.summaryList).toEqual([{ id: 99 }])
    expect(ctx.$message.success).not.toHaveBeenCalled()
    await ctx.handleRefreshSummary()
    expect(ctx.summaryRefreshTask.taskId).toBe('retry-task')
  })

  test('已有完成任务时新提交结果未知，不能查询旧任务冒充新提交成功', async () => {
    ctx.summaryRefreshTask = task('SUCCEEDED', { taskId: 'old-task' })
    billingApi.refreshBillingSummary.mockRejectedValueOnce(new Error('提交响应丢失'))
    await ctx.handleRefreshSummary()
    expect(ctx.summaryRefreshTask).toBeNull()
    expect(ctx.summaryRefreshError).toContain('提交结果未确认')
    await ctx.handleRefreshSummary()
    expect(billingApi.refreshBillingSummary).toHaveBeenCalledOnce()
    billingApi.getLatestBillingSummaryRefresh.mockResolvedValueOnce({ data: task('RUNNING', { taskId: 'new-task' }) })
    await ctx.loadSummaryRefreshTask()
    expect(ctx.summaryRefreshTask.taskId).toBe('new-task')
    expect(ctx.$message.success).not.toHaveBeenCalled()
  })
})
