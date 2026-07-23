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

describe('Login input box model', () => {
  test('border, background and radius belong to the outer wrapper', () => {
    expect(loginSource).toMatch(
      /:deep\(\.login-form \.el-input__wrapper\)\s*\{[\s\S]*?min-height:\s*44px;[\s\S]*?border-radius:\s*12px;[\s\S]*?background:/
    )
    expect(loginSource).toMatch(
      /:deep\(\.login-form \.el-input__wrapper:hover\)\s*\{[\s\S]*?box-shadow:/
    )
    expect(loginSource).toMatch(
      /:deep\(\.login-form \.el-input__wrapper\.is-focus\)\s*\{[\s\S]*?box-shadow:/
    )
  })

  test('the native input does not draw a second box', () => {
    const innerBlock = loginSource.match(
      /:deep\(\.login-form \.el-input__inner\)\s*\{([\s\S]*?)\}/
    )?.[1] || ''

    expect(innerBlock).toMatch(/border:\s*0;/)
    expect(innerBlock).toMatch(/border-radius:\s*0;/)
    expect(innerBlock).toMatch(/box-shadow:\s*none;/)
    expect(innerBlock).toMatch(/background:\s*transparent;/)
    expect(loginSource).not.toContain(':deep(.login-form .el-input__inner:hover)')
    expect(loginSource).not.toContain(':deep(.login-form .el-input__inner:focus)')
  })
})
