const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

for (const [colorScheme, tableScrollMode] of ['LIGHT', 'DARK'].flatMap(scheme => ['AUTO', 'FIXED'].map(mode => [scheme, mode]))) {
  test(`${colorScheme} ${tableScrollMode} 规则集统计显示补齐与缺失追踪，展开明细完整可见`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.addInitScript(({ scheme, mode }) => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
      schemaVersion: 3, colorScheme: scheme, tableScrollMode: mode, accentMode: 'PRESET', accentPreset: 'THEME_BLUE',
      customSolidColor: '#2639E9', customGradientColors: ['#2639E9', '#873FF2'], customGradientType: 'LINEAR', customGradientAngle: 135,
      navigationLayout: 'LEFT', sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
    })), { scheme: colorScheme, mode: tableScrollMode })
    const data = createOperationsApiData()
    let pending = 5
    data.set('/api/rule/log/rule-set-stats', () => ({
      overview: { evaluationCount: 3, hitCount: 1, failureCount: 1, hitRate: 1/3, failureRate: 1/3, avgCostTimeMs: 116.67, p95CostTimeMs: 200, p99CostTimeMs: 200 },
      ruleSets: [{ ruleCode: 'RS-A', ruleName: '准入规则集', evaluationCount: 2, hitCount: 1, hitRate: 0.5, failureRate: 0.5, avgCostTimeMs: 150, p95CostTimeMs: 200, p99CostTimeMs: 200,
        items: [{ ruleCode: 'AGE', ruleName: '年龄条件', evaluationCount: 2, hitCount: 1, hitRate: 0.5 }] }],
      coverage: { totalCount: 20, pendingCount: pending, readyCount: 10, noTraceCount: 2, invalidCount: 1, truncatedCount: 2 },
    }))
    const { assertClean } = await installDistRoutes(page, { apiData: data })
    await page.goto('http://tianshu.local/index.html#/log')
    await page.getByRole('tab', { name: '规则集命中统计', exact: true }).click()
    await expect(page.getByTestId('rule-set-coverage')).toContainText('5 条日志正在后台补齐')
    await expect(page.getByTestId('rule-set-coverage')).toContainText('1 条追踪无法解析')
    await expect(page.getByTestId('rule-set-coverage')).toContainText('2 条追踪已截断')
    await expect(page.locator('.stats-cards')).toContainText('200.00 ms')
    const row = page.locator('.rule-set-table .el-table__body tr').filter({ hasText: '准入规则集' }).first()
    await row.locator('.el-table__expand-icon').click()
    await expect(page.getByText('年龄条件', { exact: true })).toBeVisible()
    await expect.poll(async () => page.getByText('年龄条件', { exact: true }).evaluate(element => {
      const row = element.closest('tr').getBoundingClientRect()
      for (let parent = element.closest('tr').parentElement; parent; parent = parent.parentElement) {
        const style = getComputedStyle(parent)
        const box = parent.getBoundingClientRect()
        if (/hidden|auto|scroll|clip/.test(style.overflowY) && (box.height <= 0 || row.top < box.top - 1 || row.bottom > box.bottom + 1)) return false
      }
      return true
    })).toBe(true)
    pending = 0
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await expect(page.getByTestId('rule-set-coverage')).not.toContainText('尚未完整')
    await expect(page.getByTestId('rule-set-coverage')).toContainText('2 条未记录追踪')
    await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
    await expect(page.locator('html')).toHaveAttribute('data-table-scroll-mode', tableScrollMode.toLowerCase())
    assertClean()
  })
}

test('看板规则集命中也提示统计缺口，避免误读为完整进件命中率', async ({ page }) => {
  const data = createOperationsApiData()
  data.set('/api/rule/dashboard/governance', {
    ruleSetHits: { visible: true, items: [{ ruleCode: 'RS', ruleName: '规则集', applicationCount: 1, hitCount: 1, hitRate: 1 }], coverage: { pendingCount: 123 } },
    approvals: { visible: false, totalCount: 0, statuses: [] },
  })
  const { assertClean } = await installDistRoutes(page, { apiData: data })
  await page.goto('http://tianshu.local/index.html#/dashboard')
  await expect(page.getByTestId('rule-set-coverage')).toContainText('123 条日志正在后台补齐')
  assertClean()
})
