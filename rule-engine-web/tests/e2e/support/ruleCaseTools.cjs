async function openRuleCaseTools(page) {
  await page.getByRole('tab', { name: '测试用例', exact: true }).click()
  await page.getByRole('button', { name: '当前规则用例', exact: true }).click()
}

async function returnToRuleTest(page) {
  await page.getByRole('dialog').filter({ has: page.getByRole('button', { name: '查看回归结果' }) }).getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('tab', { name: '规则测试', exact: true }).click()
}

module.exports = { openRuleCaseTools, returnToRuleTest }
