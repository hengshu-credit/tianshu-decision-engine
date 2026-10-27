const { test, expect } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

for (const restricted of [false, true]) test(`模型预热${restricted ? '受限账号只看汇总' : '全局管理员可重新验证'}`, async ({ page }) => {
  let retries = 0
  const warmup = { state: 'FAILED', targetCount: 1, successCount: 0, failureCount: 1, cpuFallbackCount: 0,
    detailsRestricted: restricted, failures: restricted ? [] : [{ modelId: 7, modelName: '图像模型', version: 2, errorType: 'IllegalStateException' }] }
  const permissions = ['log:view', 'project:view', 'rule:view', 'rule:submit', 'model:view', 'model:edit']
  const harness = await installDistRoutes(page, { apiData: new Map([
    ['/api/auth/console/config', { loginEnabled: true, initializationRequired: false }],
    ['/api/auth/console/me', { username: 'warmup-review', userId: 1, permissions, defaultPermissions: permissions, projectAccessMode: restricted ? 'SELECTED' : 'ALL', projectIds: [1], globalEditable: true }],
    ['/api/auth/console/preferences/theme', null],
    ['POST /api/rule/console/activity', null],
    ['/api/rule/log/list', { records: [], total: 0 }],
    ['/api/rule/ops/execution-metrics', { instanceId: 'warmup-node', persistence: {}, sourceResolution: {}, openExecution: {}, externalCircuitBreakers: {}, ruleWarmup: { state: 'READY' }, onnxWarmup: warmup }],
    ['POST /api/rule/ops/model-warmup/retry', () => { retries++; return { ...warmup, state: 'READY', successCount: 1, failureCount: 0, failures: [] } }],
  ]) })
  await page.goto('http://tianshu.local/#/log')
  await page.getByRole('tab', { name: '运行状态', exact: true }).click()
  const panel = page.getByTestId('execution-metrics-model-warmup')
  await expect(panel).toContainText('预热失败')
  if (restricted) {
    await expect(panel).toContainText('当前账号仅可查看节点汇总')
    await expect(panel).not.toContainText('图像模型')
    await expect(panel.getByRole('button', { name: '重新验证模型预热', exact: true })).toHaveCount(0)
    expect(retries).toBe(0)
  } else {
    await expect(panel).toContainText('图像模型')
    await panel.getByRole('button', { name: '重新验证模型预热', exact: true }).click()
    await expect(panel).toContainText('已就绪')
    expect(retries).toBe(1)
  }
  harness.assertClean()
})
