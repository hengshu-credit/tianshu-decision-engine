const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createDocsApiData } = require('./support/docsFixtures.cjs')

function deferred() {
  let resolve
  const promise = new Promise(yes => { resolve = yes })
  return { promise, resolve }
}

function field(page, container, label) {
  return container.locator('.el-form-item').filter({
    has: page.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${label}$`) }),
  })
}

async function select(page, container, label, option) {
  await field(page, container, label).locator('.el-select').click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

for (const theme of ['LIGHT', 'DARK']) {
  test.describe(theme, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(colorScheme => localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
        schemaVersion: 1, colorScheme, accentPreset: 'THEME_BLUE', sidebarTheme: 'DARK',
        contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false,
      })), theme)
    })

    for (const entry of [
      { route: '/billing', tab: '计费配置', path: '/api/rule/billing/config/list', label: '计费编码', key: 'billingCode' },
      { route: '/billing', tab: '计费明细', path: '/api/rule/billing/record/list', label: '计费编码', key: 'billingCode' },
      { route: '/billing', tab: '计费汇总', path: '/api/rule/billing/summary/list', label: '计费编码', key: 'billingCode' },
      { route: '/log', tab: '执行明细', path: '/api/rule/log/list', label: 'Trace ID', key: 'traceId' },
    ]) {
      test(`${entry.tab} 翻页查询保持所选页码`, async ({ page }) => {
        const data = createDocsApiData()
        const record = data.get(entry.path).records[0]
        data.set(entry.path, ({ url }) => ({ records: [{ ...record, [entry.key]: `PAGE_${url.searchParams.get('pageNum') || '1'}` }], total: 30 }))
        const { assertClean } = await installDistRoutes(page, { apiData: data })
        await page.goto(`http://tianshu.local/index.html#${entry.route}`)
        await page.getByRole('tab', { name: entry.tab, exact: true }).click()
        const body = page.locator('.el-table:visible .el-table__body').first()
        await expect(body).toContainText('PAGE_1')
        await page.locator('.el-pagination:visible .btn-next').click()
        await expect(body).toContainText('PAGE_2')
        await expect(page.locator('.el-pagination:visible .el-pager .is-active')).toHaveText('2')
        assertClean()
      })

      test(`${entry.tab} 旧响应不覆盖新查询，失败后可重新查询`, async ({ page }) => {
        const data = createDocsApiData(), delayed = deferred(), started = deferred()
        const record = data.get(entry.path).records[0]
        const result = key => ({ records: [{ ...record, [entry.key]: key }], total: 1 })
        data.set(entry.path, ({ url }) => {
          const key = url.searchParams.get(entry.key) || 'INITIAL'
          if (key === 'OLD' && url.searchParams.get('pageSize') === '10') {
            started.resolve()
            return delayed.promise
          }
          return result(key)
        })
        const { assertClean } = await installDistRoutes(page, { apiData: data })
        await page.goto(`http://tianshu.local/index.html#${entry.route}`)
        await expect(page.locator('html')).toHaveAttribute('data-theme', theme.toLowerCase())
        await page.getByRole('tab', { name: entry.tab, exact: true }).click()
        const area = page.locator('.uiue-search-container:visible').first()
        const input = field(page, area, entry.label).locator('input').first()
        const body = page.locator('.el-table:visible .el-table__body').first()
        await expect(body).toContainText('INITIAL')
        await input.fill('OLD')
        await area.getByRole('button', { name: '查询', exact: true }).click()
        await started.promise
        await input.fill('CURRENT')
        await area.getByRole('button', { name: '查询', exact: true }).click()
        await expect(body).toContainText('CURRENT')
        const oldResponse = page.waitForResponse(response => {
          const url = new URL(response.url())
          return url.pathname === entry.path && url.searchParams.get(entry.key) === 'OLD' && url.searchParams.get('pageSize') === '10'
        })
        delayed.resolve(result('OLD'))
        await (await oldResponse).finished()
        await input.click()
        await expect(body).toContainText('CURRENT')
        await expect(body).not.toContainText('OLD')
        await page.route(`**${entry.path}*`, async route => {
          if (new URL(route.request().url()).searchParams.get(entry.key) !== 'FAIL') return route.fallback()
          await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '测试查询失败' }) })
        })
        await input.fill('FAIL')
        await area.getByRole('button', { name: '查询', exact: true }).click()
        await expect(page.locator('.el-table:visible .el-table__empty-text')).toContainText('加载失败，请重新查询')
        await expect(page.locator('.el-pagination:visible')).toHaveCount(0)
        await input.fill('RECOVERED')
        await area.getByRole('button', { name: '查询', exact: true }).click()
        await expect(body).toContainText('RECOVERED')
        assertClean()
      })
    }

    test('计费对象切换与关窗重开不会显示上一轮候选', async ({ page }) => {
      const data = createDocsApiData(), delayed = deferred(), started = deferred()
      let ruleLoads = 0
      data.set('/api/rule/definition/list', () => {
        if (++ruleLoads === 1) { started.resolve(); return delayed.promise }
        return { records: [{ id: 9, ruleName: '重开后的规则', ruleCode: 'fresh_rule' }], total: 1 }
      })
      data.set('/api/rule/database/list', { records: [{ id: 22, datasourceName: '当前数据库', datasourceCode: 'current_db' }], total: 1 })
      const { assertClean } = await installDistRoutes(page, { apiData: data })
      await page.goto('http://tianshu.local/index.html#/billing')
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme.toLowerCase())
      await page.getByRole('button', { name: '新建计费项', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '新建计费配置', exact: true })
      await started.promise
      await select(page, dialog, '计费对象', '数据库查询')
      const target = field(page, dialog, '指定对象').locator('.el-select')
      await expect(target).not.toHaveClass(/is-disabled/)
      await target.click()
      await expect(page.getByRole('option', { name: '当前数据库 / current_db', exact: true })).toBeVisible()
      await page.keyboard.press('Escape')
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      await expect(dialog).toBeHidden()
      await page.getByRole('button', { name: '新建计费项', exact: true }).click()
      await expect(target).not.toHaveClass(/is-disabled/)
      const response = page.waitForResponse('**/api/rule/definition/list*')
      delayed.resolve({ records: [{ id: 11, ruleName: '过期规则', ruleCode: 'old_rule' }], total: 1 })
      await (await response).finished()
      await target.click()
      await expect(page.getByRole('option', { name: '重开后的规则 / fresh_rule', exact: true })).toBeVisible()
      await expect(page.getByRole('option', { name: '过期规则 / old_rule', exact: true })).toHaveCount(0)
      await page.keyboard.press('Escape')
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      assertClean()
    })

    test('计费候选失败提示就地重试并恢复选项', async ({ page }, testInfo) => {
      const data = createDocsApiData()
      let fail = true
      const { assertClean } = await installDistRoutes(page, { apiData: data })
      await page.route('**/api/rule/definition/list*', async route => {
        if (!fail) return route.fallback()
        fail = false
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, message: '测试候选失败' }) })
      })
      await page.goto('http://tianshu.local/index.html#/billing')
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme.toLowerCase())
      await page.getByRole('button', { name: '新建计费项', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '新建计费配置', exact: true })
      await expect(dialog.getByRole('alert')).toContainText('计费对象加载失败')
      await expect.poll(async () => (await field(page, dialog, '指定对象').locator('.el-select').boundingBox()).width).toBeGreaterThan(400)
      const message = dialog.getByRole('alert').locator('span').first()
      await expect.poll(async () => message.evaluate(element => element.getBoundingClientRect().height <= Number.parseFloat(getComputedStyle(element).lineHeight) + 1)).toBe(true)
      await dialog.screenshot({ path: testInfo.outputPath(`candidate-error-${theme}.png`), animations: 'disabled' })
      await page.setViewportSize({ width: 560, height: 800 })
      await expect(dialog.getByRole('button', { name: '重试', exact: true })).toBeInViewport()
      await expect.poll(async () => dialog.evaluate(element => element.getBoundingClientRect().right <= innerWidth)).toBe(true)
      await dialog.screenshot({ path: testInfo.outputPath(`candidate-error-narrow-${theme}.png`), animations: 'disabled' })
      await dialog.getByRole('button', { name: '重试', exact: true }).click()
      await expect(dialog.getByRole('alert')).toHaveCount(0)
      await field(page, dialog, '指定对象').locator('.el-select').click()
      await expect(page.getByRole('option').first()).toBeVisible()
      assertClean()
    })
  })
}
