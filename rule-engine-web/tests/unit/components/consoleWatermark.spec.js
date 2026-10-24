import { mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import dayjs from 'dayjs'
import ConsoleWatermark from '@/components/common/ConsoleWatermark.vue'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'
import { applySystemBrand, DEFAULT_BRAND, systemBrand } from '@/system/systemBrand'
import { setWatermarkPreview } from '@/system/watermarkPreview'

const user = { username: 'reviewer', displayName: '审核员', loginTime: '2026-10-10T12:00:00.121', permissions: [] }
const watermarkStub = { props: ['content', 'font', 'gap', 'rotate'], template: '<div class="watermark-content">{{ content.join(" / ") }}</div>' }
let wrapper
let route
beforeEach(() => {
  applySystemBrand({ ...DEFAULT_BRAND, watermarkEnabled: true })
  setCurrentUser(user)
  route = reactive({ path: '/dashboard' })
  wrapper = mount(ConsoleWatermark, { global: { mocks: { $route: route }, stubs: { 'el-watermark': watermarkStub } } })
})
afterEach(() => {
  wrapper.unmount(); setWatermarkPreview(null); clearCurrentUser(); applySystemBrand(DEFAULT_BRAND)
  delete document.documentElement.dataset.theme
  delete document.fullscreenElement
})

test('单行显示系统名称、登录用户名及毫秒级登录时间，路由切换不重置时间', async () => {
  const content = () => document.querySelector('.console-watermark')?.textContent
  expect(content()).toBe('天枢决策引擎 reviewer 2026:10:10 12:00:00.121')
  expect(wrapper.findComponent(watermarkStub).props('content')).toHaveLength(1)
  const original = content()
  route.path = '/no-permission'; await nextTick()
  expect(content()).toBe(original)
  applySystemBrand({ ...DEFAULT_BRAND, systemName: '新系统名称', watermarkEnabled: true }); await nextTick()
  expect(content()).toBe('新系统名称 reviewer 2026:10:10 12:00:00.121')
  clearCurrentUser(); await nextTick()
  expect(document.querySelector('.console-watermark')).toBeNull()
  setCurrentUser({ ...user, username: 'another-user', displayName: '', loginTime: '2026-10-24T03:00:00Z' }); await nextTick()
  expect(content()).toContain('新系统名称 another-user')
  expect(content()).not.toContain('reviewer')
  expect(content()).toContain(dayjs('2026-10-24T03:00:00Z').format('YYYY:MM:DD HH:mm:ss.SSS'))
})

test('关闭水印或进入登录相关页面时不显示身份', async () => {
  for (const path of ['/login', '/register', '/forgot-password', '/password-reset', '/admin-recovery']) {
    route.path = path; await nextTick()
    expect(document.querySelector('.console-watermark')).toBeNull()
  }
  route.path = '/system/settings'; await nextTick()
  expect(document.querySelector('.console-watermark')).not.toBeNull()
  applySystemBrand(DEFAULT_BRAND); await nextTick()
  expect(document.querySelector('.console-watermark')).toBeNull()
})

test('生效配置实时改变全局内容、透明度、排列与密度', async () => {
  applySystemBrand({ ...DEFAULT_BRAND, watermarkEnabled: true, watermarkContent: '内部资料\n{用户名}', watermarkTransparency: 60, watermarkLayout: 'VERTICAL', watermarkDensity: 'DENSE' })
  await nextTick()
  expect(wrapper.findComponent(watermarkStub).props()).toMatchObject({ content: ['内部资料', 'reviewer'], rotate: -90, gap: [40, 40], font: { color: 'rgba(0, 0, 0, 0.4)', fontSize: 13 } })
})

test('整页草稿预览仅在当前编辑者的配置页显示，其他用户及页面使用生效配置', async () => {
  setCurrentUser({ ...user, permissions: ['system:edit'] })
  route.path = '/system/settings'
  setWatermarkPreview({ ...DEFAULT_BRAND, watermarkEnabled: true, watermarkContent: '未保存 {用户名}' })
  await nextTick()
  expect(document.querySelector('.console-watermark').textContent).toBe('未保存 reviewer')
  expect(systemBrand.watermarkContent).toBe(DEFAULT_BRAND.watermarkContent)
  route.path = '/project'; await nextTick()
  expect(document.querySelector('.console-watermark').textContent).toContain('天枢决策引擎 reviewer')
  route.path = '/system/settings'
  setCurrentUser({ ...user, username: 'other', permissions: ['system:edit'] }); await nextTick()
  expect(document.querySelector('.console-watermark').textContent).toContain('天枢决策引擎 other')
  expect(document.querySelector('.console-watermark').textContent).not.toContain('未保存')
})

test('开关草稿立即改变当前页面预览，取消后恢复已保存开关', async () => {
  setCurrentUser({ ...user, permissions: ['system:edit'] }); route.path = '/system/settings'
  setWatermarkPreview({ ...DEFAULT_BRAND, watermarkEnabled: false }); await nextTick()
  expect(document.querySelector('.console-watermark')).toBeNull()
  expect(systemBrand.watermarkEnabled).toBe(true)
  setWatermarkPreview(null); await nextTick()
  expect(document.querySelector('.console-watermark')).not.toBeNull()
})

test('跟随主题和全屏容器，退出全屏恢复到文档根部', async () => {
  const original = wrapper.findComponent(watermarkStub).props('font').color
  document.documentElement.dataset.theme = 'dark'
  window.dispatchEvent(new Event('tianshu-theme-change')); await nextTick()
  expect(wrapper.findComponent(watermarkStub).props('font').color).not.toBe(original)
  const fullscreen = document.createElement('div'); document.body.appendChild(fullscreen)
  Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: fullscreen })
  document.dispatchEvent(new Event('fullscreenchange')); await nextTick()
  expect(fullscreen.querySelector('.console-watermark')).not.toBeNull()
  delete document.fullscreenElement
  document.dispatchEvent(new Event('fullscreenchange')); await nextTick()
  expect(document.querySelector('.console-watermark').parentElement).toBe(document.body)
  fullscreen.remove()
})

test('缺少会话时间时明确显示未知，不用当前时间伪造登录时间', async () => {
  setCurrentUser({ username: 'legacy' }); await nextTick()
  expect(document.querySelector('.console-watermark').textContent).toBe('天枢决策引擎 legacy 未知')
})
