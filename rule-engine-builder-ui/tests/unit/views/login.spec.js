const fs = require('fs')
const path = require('path')

const loginSource = fs.readFileSync(
  path.resolve(__dirname, '../../../src/views/Login.vue'),
  'utf8'
)

describe('Login 输入框可见性', () => {
  test('白色输入框使用深色文字', () => {
    expect(loginSource).toMatch(
      /:deep\(\.login-form \.el-input__inner\)\s*\{[\s\S]*?color:\s*\$login-text;/
    )
  })

  test('密码继续遮罩且不注入配置凭据', () => {
    expect(loginSource).toContain('type="password"')
    expect(loginSource).toContain("form: { username: '', password: '' }")
    expect(loginSource).not.toMatch(/CONSOLE_(USERNAME|PASSWORD)/)
  })
})
