import { shallowMount, flushPromises } from '@vue/test-utils'
import Login from '@/views/Login.vue'
import { getConsoleAuthConfig } from '@/api/auth'

vi.mock('@/api/auth', () => ({ getConsoleAuthConfig: vi.fn(), consoleLogin: vi.fn() }))

test('未配置邮件时，已启用的管理员应急恢复仍有登录页入口', async () => {
  getConsoleAuthConfig.mockResolvedValue({ data: { registrationEnabled: false, passwordRecoveryEnabled: false, breakGlassRecoveryEnabled: true } })
  const wrapper = shallowMount(Login, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } } })
  await flushPromises()
  expect(wrapper.find('a[href="/admin-recovery"]').exists()).toBe(true)
  expect(wrapper.find('a[href="/forgot-password"]').exists()).toBe(false)
  wrapper.unmount()
})

test('配置暂不可用时可在登录页重试并恢复对应入口', async () => {
  getConsoleAuthConfig.mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ data: { loginEnabled: true, passwordRecoveryEnabled: true } })
  const wrapper = shallowMount(Login, { global: { stubs: {
    'el-alert': { template: '<div role="alert"><slot /></div>' },
    'el-button': { template: '<button><slot /></button>' },
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  } } })
  await flushPromises()
  await wrapper.get('[role="alert"] button').trigger('click')
  await flushPromises()
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  expect(wrapper.find('a[href="/forgot-password"]').exists()).toBe(true)
  wrapper.unmount()
})
