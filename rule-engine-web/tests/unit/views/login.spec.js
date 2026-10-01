const fs = require('fs')
const path = require('path')
const { compileStyle, parse } = require('@vue/compiler-sfc')

const filename = path.resolve(__dirname, '../../../src/views/Login.vue')
const loginSource = fs.readFileSync(filename, 'utf8')
const { descriptor } = parse(loginSource, { filename })
const style = descriptor.styles.find(item => item.lang === 'scss')
const compiled = compileStyle({
  filename,
  id: 'data-v-login-contract',
  scoped: style.scoped,
  source: style.content,
  preprocessLang: style.lang,
})

if (compiled.errors.length) {
  throw compiled.errors[0]
}

const loginCss = compiled.code

function declarationBlock(selector) {
  return Array.from(loginCss.matchAll(/([^{}]+)\{([^}]*)\}/g))
    .find(match => match[1]
      .split(',')
      .some(item => item.trim() === selector))?.[2] || ''
}

describe('Login Element Plus 输入框契约', () => {
  test('登录页背景使用主题表面和当前配色 RGB，不保留固定日夜渐变', () => {
    const background = Array.from(loginCss.matchAll(/([^{}]+)\{([^}]*)\}/g))
      .find(match => match[1].includes('.login-bg'))?.[2] || ''

    expect(background).toContain('var(--tianshu-bg-page)')
    expect(background).toContain('var(--tianshu-bg-workspace)')
    expect(background).toContain('var(--el-color-primary-rgb)')
    expect(background).toContain('var(--tianshu-color-secondary-rgb)')
    expect(loginSource).not.toContain("[data-theme='dark'] .login-bg")
  })

  test('账号和密码使用 Element 内置清空与密码显隐能力', () => {
    const template = descriptor.template.content

    expect(template).toMatch(
      /v-model="form\.username"[\s\S]*?autocomplete="username"[\s\S]*?clearable/
    )
    expect(template).toMatch(
      /v-model="form\.password"[\s\S]*?type="password"[\s\S]*?show-password/
    )
    expect(template).not.toContain('#suffix')
    expect(loginSource).not.toMatch(/CONSOLE_(USERNAME|PASSWORD)/)
  })

  test('输入框通过 Element 变量读取当前主题色和语义背景', () => {
    const input = declarationBlock('[data-v-login-contract] .login-form .el-input')

    expect(input).toContain('--el-input-text-color: var(--tianshu-text-primary)')
    expect(input).toContain('--el-input-bg-color: var(--tianshu-bg-elevated)')
    expect(input).toContain('--el-input-border-color: var(--tianshu-border)')
    expect(input).toContain('--el-input-hover-border-color: var(--el-color-primary-light-3)')
    expect(input).toContain('--el-input-focus-border-color: var(--el-color-primary)')
    expect(input).toContain('--el-input-placeholder-color: var(--tianshu-text-tertiary)')
  })

  test('输入框外壳只控制尺寸和圆角，不重复绘制 Element 状态', () => {
    const wrapper = declarationBlock(
      '[data-v-login-contract] .login-form .el-input__wrapper'
    )

    expect(wrapper).toContain('min-height: 44px')
    expect(wrapper).toContain('border-radius: 12px')
    expect(wrapper).not.toContain('box-shadow')
    expect(loginCss).not.toContain('.el-input__wrapper:hover')
    expect(loginCss).not.toContain('.el-input__wrapper.is-focus')
  })

  test('浏览器自动填充使用与 Element 外壳相同的主题背景', () => {
    const autofill = declarationBlock(
      '[data-v-login-contract] .login-form .el-input__inner:-webkit-autofill'
    )

    expect(autofill).toContain('-webkit-text-fill-color: var(--el-input-text-color)')
    expect(autofill).toContain('caret-color: var(--el-input-focus-border-color)')
    expect(autofill).toContain(
      '-webkit-box-shadow: 0 0 0 1000px var(--el-input-bg-color) inset'
    )
  })
})

describe('Login 登录按钮加载态契约', () => {
  test('加载时保持主题背景并仅轻微降低透明度', () => {
    const loading = declarationBlock(
      '.login-btn.el-button--primary.is-loading[data-v-login-contract]:disabled'
    )
    const loadingMask = declarationBlock(
      '.login-btn.is-loading[data-v-login-contract]::before'
    )
    const opacity = Number(loading.match(/opacity:\s*([\d.]+)/)?.[1])

    expect(loading).toContain(
      'background: var(--tianshu-brand-background) !important'
    )
    expect(loading).toContain('border-color: var(--el-color-primary) !important')
    expect(opacity).toBeGreaterThanOrEqual(0.85)
    expect(opacity).toBeLessThan(1)
    expect(loadingMask).toContain('background-color: transparent')
  })
})
