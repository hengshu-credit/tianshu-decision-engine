import { shallowMount, flushPromises } from '@vue/test-utils'
import Register from '@/views/Register.vue'
import { getConsoleAuthConfig, initializeConsoleAdministrator, consoleRegister } from '@/api/auth'

vi.mock('@/api/auth', () => ({
  getConsoleAuthConfig: vi.fn(), initializeConsoleAdministrator: vi.fn().mockResolvedValue({}),
  consoleRegister: vi.fn(), verifyConsoleRegistration: vi.fn(), resendConsoleRegistration: vi.fn()
}))

test('首次注册不依赖邮箱，并提交专用初始化接口后清除明文密码', async () => {
  getConsoleAuthConfig.mockResolvedValue({ data: { initializationRequired: true, registrationEnabled: false } })
  const wrapper = shallowMount(Register, { global: {
    mocks: { $route: { query: {} } },
    stubs: { RouterLink: true, ElResult: true, 'el-form': { template: '<form><slot /></form>', methods: { validate(callback) { callback(true) } } } }
  } })
  await flushPromises()
  expect(wrapper.find('[label="注册邮箱"]').exists()).toBe(false)
  await wrapper.setData({ form: { username: 'owner', displayName: '系统管理员', email: '', password: 'OwnerPass123!', confirmPassword: 'OwnerPass123!' } })
  wrapper.vm.submit()
  await flushPromises()
  expect(initializeConsoleAdministrator).toHaveBeenCalledWith({ username: 'owner', displayName: '系统管理员', password: 'OwnerPass123!' })
  expect(consoleRegister).not.toHaveBeenCalled()
  expect(wrapper.vm.initialized).toBe(true)
  expect(wrapper.vm.form.password).toBe('')
  wrapper.unmount()
})
