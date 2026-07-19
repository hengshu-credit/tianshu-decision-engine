import { createLocalVue, mount } from '@vue/test-utils'

describe('Jest Vue 环境门禁', () => {
  test('localVue 可以解析 v-loading 且不输出 Vue warning', () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount({
      template: '<div v-loading="true">content</div>'
    }, {
      localVue: createLocalVue()
    })

    expect(wrapper.text()).toBe('content')
    expect(consoleError).not.toHaveBeenCalled()

    wrapper.destroy()
    consoleError.mockRestore()
  })
})
