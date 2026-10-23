import { shallowMount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import Register from '@/views/Register.vue'
import { verifyConsoleRegistration } from '@/api/auth'

vi.mock('@/api/auth', () => ({ consoleRegister: vi.fn(), resendConsoleRegistration: vi.fn(), initializeConsoleAdministrator: vi.fn(), getConsoleAuthConfig: vi.fn().mockResolvedValue({ data: { initializationRequired: false } }), verifyConsoleRegistration: vi.fn().mockResolvedValue({}) }))

test('注册页复用组件打开验证链接时仍执行邮箱验证', async () => {
  const route = reactive({ query: {} })
  const wrapper = shallowMount(Register, { global: { mocks: { $route: route }, stubs: { ElResult: true, RouterLink: true } } })
  expect(verifyConsoleRegistration).not.toHaveBeenCalled()
  route.query.token = 'new-mail-token'
  await flushPromises()
  expect(verifyConsoleRegistration).toHaveBeenCalledWith({ token: 'new-mail-token' })
  expect(wrapper.vm.verified).toBe(true)
  wrapper.unmount()
})
