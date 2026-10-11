const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

test('执行日志的运行状态按需加载当前节点快照并显示中文持久化策略', async ({ page }) => {
  const harness = await installDistRoutes(page, { apiData: new Map([
    ['/api/rule/log/list', { records: [], total: 0 }],
    ['/api/rule/ops/execution-metrics', {
      instanceId: 'runtime-entry-node',
      persistence: { queueDepth: 0, queueCapacity: 5000, overflowStrategy: 'DURABLE_OUTBOX_REPLAY' },
      sourceResolution: {}, openExecution: {}, externalCircuitBreakers: {}, ruleWarmup: { state: 'READY' },
    }],
  ]) })
  const requestCount = () => harness.requests.filter(request =>
    new URL(request.url).pathname === '/api/rule/ops/execution-metrics').length
  await page.goto('http://tianshu.local/index.html#/log')
  await expect(page.getByRole('tab', { name: '运行状态', exact: true })).toBeVisible()
  expect(requestCount()).toBe(0)
  await page.getByRole('tab', { name: '运行状态', exact: true }).click()
  await expect(page.getByTestId('execution-runtime-scope')).toContainText('不受项目、规则或时间筛选影响')
  await expect(page.getByTestId('execution-metrics-instance')).toContainText('runtime-entry-node')
  await expect(page.getByTestId('execution-metrics-persistence')).toContainText('持久化队列重放')
  expect(requestCount()).toBe(1)
  await page.getByTestId('execution-metrics-refresh').click()
  await expect.poll(requestCount).toBe(2)
  await page.getByRole('tab', { name: '执行明细', exact: true }).click()
  await expect(page.locator('.execution-metrics-panel')).toHaveCount(0)
  await page.getByRole('tab', { name: '运行状态', exact: true }).click()
  await expect.poll(requestCount).toBe(3)
  harness.assertClean()
})
