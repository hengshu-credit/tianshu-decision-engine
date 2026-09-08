const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')
const { createOperationsApiData } = require('./support/operationsFixtures.cjs')

const listPages = ['project', 'rule', 'variable', 'list', 'datasource', 'database', 'model', 'function', 'experiment', 'log', 'billing', 'account', 'approval']

for (const colorScheme of ['LIGHT', 'DARK']) {
  for (const route of listPages) {
    test(`${colorScheme} ${route} 列表固定操作列并保持单行和实色表头`, async ({ page }) => {
      await page.setViewportSize({ width: 1100, height: 900 })
      await page.addInitScript(scheme => {
        localStorage.setItem('tianshu-ui-theme-v1', JSON.stringify({
          schemaVersion: 1, colorScheme: scheme, accentPreset: 'LIQUID_PURPLE',
          sidebarTheme: 'DARK', contentWidth: 'FLUID', fixedSidebar: true, colorWeak: false
        }))
      }, colorScheme)
      const apiData = createOperationsApiData()
      if (route === 'variable') apiData.set('/api/rule/variable/list', {
        records: [{ id: 1, varCode: 'credit_status', varLabel: '授信状态', varType: 'ENUM', varSource: 'API', scope: 'PROJECT', status: 1 }], total: 1
      })
      if (route === 'model') apiData.get('/api/rule/model/list').records[0].publishedVersion = 1
      apiData.set('/api/rule/console/accounts', [{ id: 1, username: 'reviewer', displayName: '业务审批账户', roleCodes: ['BUSINESS'], status: 1 }])
      apiData.set('/api/rule/console/roles', [{ id: 1, roleCode: 'BUSINESS', roleName: '业务管理员', status: 1 }])
      apiData.set('/api/rule/console/permissions', [])
      apiData.set('/api/rule/governance/requests/summary', { pendingCount: 1 })
      apiData.set('/api/rule/governance/requests', { records: [{ id: 1, requestNo: 'APPROVAL_001', resourceType: 'RULE', resourceName: '授信规则', action: 'UPDATE', status: 'PENDING', applicant: 'reviewer', createTime: '2026-09-07 10:00:00' }], total: 1 })
      const { assertClean } = await installDistRoutes(page, { apiData })
      await page.goto(`http://tianshu.local/index.html#/${route}`)
      await expect(page.locator('html')).toHaveAttribute('data-theme', colorScheme.toLowerCase())
      const table = page.locator('.el-table:visible').first()
      await expect(table.locator('.el-table__body tr').first()).toBeVisible()
      const header = table.locator('th').filter({ hasText: /^操作$/ })
      await expect(header).toHaveCSS('position', 'sticky')
      await expect(header).toHaveCSS('right', '0px')
      const before = await header.boundingBox()
      const scrollWrap = table.locator('.el-scrollbar__wrap').first()
      await scrollWrap.evaluate(element => { element.scrollLeft = element.scrollWidth })
      await expect.poll(() => scrollWrap.evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
      await expect.poll(async () => Math.abs((await header.boundingBox()).x - before.x)).toBeLessThan(1)
      const issues = await table.evaluate(element => {
        const problems = []
        const canvas = document.createElement('canvas')
        canvas.width = canvas.height = 1
        const context = canvas.getContext('2d')
        for (const cell of element.querySelectorAll('th, td')) {
          const content = cell.querySelector('.cell')
          if (content && getComputedStyle(content).whiteSpace !== 'nowrap') problems.push(`换行: ${cell.textContent.trim()}`)
          if (cell.tagName === 'TH' || cell.classList.contains('el-table-fixed-column--right')) {
            context.clearRect(0, 0, 1, 1)
            context.fillStyle = getComputedStyle(cell).backgroundColor
            context.fillRect(0, 0, 1, 1)
            if (context.getImageData(0, 0, 1, 1).data[3] !== 255) problems.push(`透明: ${cell.textContent.trim()}`)
          }
          const buttons = [...cell.querySelectorAll('button')].filter(button => button.getBoundingClientRect().width > 0)
          if (cell.classList.contains('el-table-fixed-column--right') && buttons.length) {
            const bounds = cell.getBoundingClientRect()
            const firstTop = buttons[0].getBoundingClientRect().top
            for (const button of buttons) {
              const rect = button.getBoundingClientRect()
              if (Math.abs(rect.top - firstTop) > 1) problems.push(`按钮换行: ${button.textContent.trim()}`)
              if (rect.left < bounds.left || rect.right > bounds.right) problems.push(`按钮被裁剪: ${button.textContent.trim()}`)
            }
          }
        }
        return problems
      })
      expect(issues).toEqual([])
      assertClean()
    })
  }
}

test('长函数参数保持单行并能悬停查看全文', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 900 })
  const apiData = createOperationsApiData()
  apiData.get('/api/rule/function/list').records[0].paramsJson = JSON.stringify(
    ['monthly_credit_application_count', 'maximum_authorized_credit_limit', 'risk_evaluation_score'].map(name => ({ name, type: 'NUMBER' }))
  )
  const { assertClean } = await installDistRoutes(page, { apiData })
  await page.goto('http://tianshu.local/index.html#/function')
  const table = page.locator('.el-table:visible').first()
  const cell = table.locator('td').filter({ hasText: 'monthly_credit_application_count' })
  await expect(cell.locator('.cell')).toHaveCSS('white-space', 'nowrap')
  await cell.hover()
  await expect(page.getByRole('tooltip').filter({ hasText: 'risk_evaluation_score' })).toBeVisible()
  assertClean()
})
