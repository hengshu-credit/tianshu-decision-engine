async function insertScriptReference(page, code) {
  const picker = page.getByRole('complementary', { name: '天枢决策引擎内容选择器', exact: true })
  await picker.getByRole('navigation', { name: '内容分类', exact: true }).getByRole('button', { name: /^全部内容/ }).click()
  await picker.getByRole('textbox', { name: '搜索内容名称或编码' }).fill(code)
  await picker.getByRole('gridcell', { name: code, exact: true }).filter({ has: page.locator('.content-ref__code') }).dblclick()
}

module.exports = { insertScriptReference }
