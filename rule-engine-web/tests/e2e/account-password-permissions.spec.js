const { expect, test } = require('@playwright/test')
const { installDistRoutes } = require('./support/distRoutes.cjs')

async function setup(page, permissions) {
  const saved = []
  const profile = { username: 'demo', displayName: '演示账户', email: '', registrationStatus: 'ACTIVE' }
  const fixtures = new Map([
    ['/api/auth/console/config', { loginEnabled: true }],
    ['/api/auth/console/me', { userId: 9, username: 'demo', permissionVersion: 1, permissions }],
    ['/api/auth/console/preferences/theme', null],
    ['POST /api/rule/console/activity', null],
    ['/api/auth/console/profile', profile],
    ['PUT /api/auth/console/profile', ({ request }) => ({ ...profile, ...request.postDataJSON() })],
    ['POST /api/auth/console/password/change', ({ request }) => { saved.push(request.postDataJSON()); return null }],
    ['/api/rule/console/accounts', [{ id: 10, username: 'target', displayName: '目标账户', status: 1, roleIds: [2], roleCodes: [], projectIds: [1] }]],
    ['POST /api/rule/console/accounts', ({ request }) => { saved.push(request.postDataJSON()); return request.postDataJSON() }],
    ['/api/rule/console/roles', []],
    ['/api/rule/console/permissions', []],
  ])
  const origin = process.env.E2E_DEV_URL || 'http://tianshu.local'
  const checks = await installDistRoutes(page, { apiData: fixtures, origin, liveStatic: Boolean(process.env.E2E_DEV_URL) })
  return { ...checks, saved, origin }
}

test('demo 无本人改密权限时不可见密码表单，但仍能保存个人资料', async ({ page }) => {
  const state = await setup(page, ['rule:view'])
  await page.goto(`${state.origin}/#/account/profile`)
  await expect(page.getByText('当前账户无修改本人密码权限，请联系管理员。')).toBeVisible()
  await expect(page.getByRole('button', { name: '修改密码', exact: true })).toHaveCount(0)
  await expect(page.locator('input[type="password"]')).toHaveCount(0)
  const field = page.locator('.el-form-item').filter({ hasText: '显示名称' }).locator('input')
  await field.fill('演示账户资料')
  await page.getByRole('button', { name: '保存资料', exact: true }).click()
  await expect(field).toHaveValue('演示账户资料')
  expect(state.requests.some(item => item.url.endsWith('/password/change'))).toBe(false)
  state.assertClean()
})

test('独立授予本人改密后可填写旧密码并提交，成功返回登录页', async ({ page }) => {
  const state = await setup(page, ['account:password-change'])
  await page.goto(`${state.origin}/#/account/profile`)
  for (const [label, value] of [['当前密码', 'OldPass123!'], ['新密码', 'ChangedPass123!'], ['确认新密码', 'ChangedPass123!']]) {
    await page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp(`^${label}$`) }) }).locator('input').fill(value)
  }
  await page.getByRole('button', { name: '修改密码', exact: true }).click()
  await expect(page).toHaveURL(/#\/login/)
  expect(state.saved).toEqual([{ currentPassword: 'OldPass123!', password: 'ChangedPass123!', confirmPassword: 'ChangedPass123!' }])
  state.assertClean()
})

test('仅管理账户不能通过编辑表单重置密码或调整授权', async ({ page }) => {
  const state = await setup(page, ['account:manage'])
  await page.goto(`${state.origin}/#/account`)
  await expect(page.getByRole('button', { name: '重置密码', exact: true })).not.toBeVisible()
  await expect(page.getByRole('button', { name: '权限调整', exact: true })).not.toBeVisible()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑账户' })
  await expect(dialog).toBeVisible()
  await expect(dialog.locator('input[type="password"]')).toHaveCount(0)
  await expect(dialog.getByText('项目范围', { exact: true })).toHaveCount(0)
  await dialog.locator('.el-form-item').filter({ hasText: '显示名称' }).locator('input').fill('更新资料')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(state.saved).toEqual([{ id: 10, username: 'target', displayName: '更新资料', email: '', status: 1 }])
  state.assertClean()
})
