import { mount } from '@test-utils'

describe('Jest Vue 环境门禁', () => {
  test('全局 Vue 3 测试配置可以解析 v-loading 且不输出 Vue warning', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount({
      template: '<div v-loading="true">content</div>'
    })

    expect(wrapper.text()).toBe('content')
    expect(consoleError).not.toHaveBeenCalled()

    wrapper.unmount()
    consoleError.mockRestore()
  })
})
