import { flushPromises } from '@vue/test-utils'
import { shallowMount } from '@test-utils'
import SystemSettings from '@/views/system/SystemSettings.vue'
import * as api from '@/api/systemSettings'
import * as governance from '@/api/governance'
import { setCurrentUser, clearCurrentUser, getCurrentUser, setPermissionEnforcement } from '@/security/permissionState'
import { systemBrand, applySystemBrand, DEFAULT_BRAND } from '@/system/systemBrand'
import { getWatermarkPreview } from '@/system/watermarkPreview'

vi.mock('@/api/systemSettings', () => ({ getSystemSettings: vi.fn(), saveSystemBrand: vi.fn(), uploadSystemAsset: vi.fn(), testSystemMail: vi.fn(), getSystemSettingsHistory: vi.fn() }))
vi.mock('@/api/governance', () => ({ createResourceDraft: vi.fn(), saveGovernanceDraft: vi.fn(), submitGovernanceRequest: vi.fn(), getWorkspaceDraft: vi.fn() }))

const settings = () => ({
  brand: { id: 1, group: 'brand', scope: 'GLOBAL', projectId: 0, configVersion: 0, systemName: '天枢决策引擎', subtitle: '天工开物，枢衡定策', logoId: '', faviconId: '', watermarkEnabled: false },
  mail: { id: 2, group: 'mail', scope: 'GLOBAL', projectId: 0, configVersion: 0, email: '', host: '', port: 587, encryption: 'STARTTLS' },
  access: { id: 3, group: 'access', scope: 'GLOBAL', projectId: 0, configVersion: 0, registrationEnabled: false, passwordRecoveryEnabled: false, baseUrl: '', verificationTtlMinutes: 30, passwordResetTtlMinutes: 30 },
  codes: { id: 4, group: 'codes', scope: 'GLOBAL', projectId: 0, configVersion: 0, enabled: false, mappings: {} },
  deploymentManaged: {},
  codeCatalog: [{ code: '000000', message: '成功', httpStatus: 200 }, { code: '100001', message: '入参校验失败', httpStatus: 400 }, { code: '100002', message: '必填字段缺失', httpStatus: 400 }],
})

describe('系统配置', () => {
  test('首屏请求期间切换页签，初始化完成后读取当前页签草稿', async () => {
    let release
    api.getSystemSettings.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    const wrapper = shallowMount(SystemSettings)
    wrapper.vm.tab = 'mail'; wrapper.vm.refreshTab('mail')
    expect(wrapper.vm.loading).toBe(true)
    release({ data: settings() }); await flushPromises()
    expect(governance.getWorkspaceDraft).toHaveBeenCalledWith('SYSTEM_SETTING', 2)
    expect(wrapper.vm.writable).toBe(true)
    wrapper.unmount()
  })

  test('离开组件后，未完成的页签请求不能继续写入状态', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    let release
    api.getSystemSettings.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    wrapper.vm.tab = 'mail'; const pending = wrapper.vm.refreshTab('mail')
    const form = wrapper.vm.form.mail
    wrapper.unmount()
    release({ data: { ...settings(), mail: { host: 'late.example.com' } } }); await pending
    expect(wrapper.vm.form.mail).toBe(form)
    expect(governance.getWorkspaceDraft).not.toHaveBeenCalled()
  })

  test('页签离开确认不等待配置请求，切换后加载期间禁止编辑', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    const calls = api.getSystemSettings.mock.calls.length
    expect(await wrapper.vm.beforeTabLeave('mail')).toBe(true)
    expect(api.getSystemSettings).toHaveBeenCalledTimes(calls)
    let release
    governance.getWorkspaceDraft.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    wrapper.vm.tab = 'mail'
    const loading = wrapper.vm.refreshTab('mail'); await flushPromises()
    expect(wrapper.vm.tabLoading).toBe(true)
    expect(wrapper.vm.writable).toBe(false)
    release({ data: null }); await loading
    expect(wrapper.vm.writable).toBe(true)
    wrapper.unmount()
  })

  test('旧页签响应不能覆盖返回该页签后的输入', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    let release
    governance.getWorkspaceDraft.mockImplementationOnce(() => new Promise(resolve => { release = resolve }))
    wrapper.vm.tab = 'mail'; const old = wrapper.vm.refreshTab('mail'); await flushPromises()
    wrapper.vm.tab = 'access'; await wrapper.vm.refreshTab('access')
    wrapper.vm.tab = 'mail'; await wrapper.vm.refreshTab('mail')
    wrapper.vm.form.mail.host = 'new-input.example.com'
    release({ data: { status: 'EDITING', draftSnapshotJson: JSON.stringify({ ...settings().mail, host: 'stale.example.com' }) } }); await old
    expect(wrapper.vm.form.mail.host).toBe('new-input.example.com')
    expect(wrapper.vm.dirty).toBe(true)
    expect(wrapper.vm.tabLoading).toBe(false)
    wrapper.unmount()
  })

  test('页签请求失败保持只读，重试后恢复；未保存修改仍可取消离开', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.tab = 'mail'
    api.getSystemSettings.mockRejectedValueOnce(new Error('读取失败'))
    await wrapper.vm.refreshTab('mail')
    expect(wrapper.vm.tabError).toBe('读取失败')
    expect(wrapper.vm.writable).toBe(false)
    await wrapper.vm.refreshTab('mail')
    expect(wrapper.vm.tabError).toBe('')
    expect(wrapper.vm.writable).toBe(true)
    wrapper.vm.mailPassword = 'unsaved-secret'
    wrapper.vm.$confirm = vi.fn().mockRejectedValue('cancel')
    expect(await wrapper.vm.beforeTabLeave('access')).toBe(false)
    expect(wrapper.vm.mailPassword).toBe('unsaved-secret')
    wrapper.unmount()
  })

  test('图片地址和上传资源互斥，浏览器图标留空跟随当前 Logo', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.form.brand.logoId = 'old-logo'
    wrapper.vm.setAssetUrl({ id: 'logoId', url: 'logoUrl' }, 'https://images.example.com/logo.svg')
    expect(wrapper.vm.form.brand.logoId).toBe('')
    expect(wrapper.vm.logoPreview).toBe('https://images.example.com/logo.svg')
    expect(wrapper.vm.faviconPreview).toBe(wrapper.vm.logoPreview)
    wrapper.vm.setAssetUrl({ id: 'faviconId', url: 'faviconUrl' }, '/images/favicon.png')
    expect(wrapper.vm.faviconPreview).toBe('/images/favicon.png')
    wrapper.vm.restoreAsset('faviconId')
    expect(wrapper.vm.faviconPreview).toBe(wrapper.vm.logoPreview)
    expect(wrapper.vm.previewBrand.logoUrl).toBe(wrapper.vm.logoPreview)
    wrapper.unmount()
  })
  beforeEach(() => { vi.clearAllMocks(); setPermissionEnforcement(true); setCurrentUser({ userId: 1, username: 'test-admin', permissions: ['system:view', 'system:edit', 'system:submit', 'system:audit', 'system:mail-test', 'approval:submit'], globalEditable: true }); api.getSystemSettings.mockResolvedValue({ data: settings() }); governance.getWorkspaceDraft.mockResolvedValue({ data: null }) })
  afterEach(() => { clearCurrentUser(); setPermissionEnforcement(false); applySystemBrand(DEFAULT_BRAND) })
  test('水印编辑和取消不影响生效值，保存成功后立即应用服务器配置', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.form.brand.watermarkEnabled = true
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.dirty).toBe(true)
    expect(systemBrand.watermarkEnabled).toBe(false)
    expect(getWatermarkPreview(getCurrentUser()).watermarkEnabled).toBe(true)
    wrapper.vm.cancelChanges()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.form.brand.watermarkEnabled).toBe(false)
    expect(getWatermarkPreview(getCurrentUser())).toBeNull()
    wrapper.vm.form.brand.watermarkEnabled = true
    const appearance = { watermarkContent: '内部 {用户名}', watermarkTransparency: 75, watermarkLayout: 'HORIZONTAL', watermarkDensity: 'SPARSE' }
    Object.assign(wrapper.vm.form.brand, appearance)
    const saved = { ...settings().brand, watermarkEnabled: true, ...appearance, configVersion: 1 }
    api.saveSystemBrand.mockResolvedValue({ data: saved })
    api.getSystemSettings.mockResolvedValue({ data: { ...settings(), brand: saved } })
    await wrapper.vm.saveBrand()
    expect(api.saveSystemBrand).toHaveBeenCalledWith(expect.objectContaining({ watermarkEnabled: true, ...appearance }))
    expect(systemBrand.watermarkEnabled).toBe(true)
    expect(systemBrand).toMatchObject(appearance)
    expect(wrapper.vm.dirty).toBe(false)
    expect(getWatermarkPreview(getCurrentUser())).toBeNull()
    wrapper.unmount()
  })
  test('保存失败仅保留本页草稿预览，离开或切换配置分组会清除预览', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.form.brand.watermarkEnabled = true
    api.saveSystemBrand.mockRejectedValue(new Error('保存失败'))
    await wrapper.vm.saveBrand(); await flushPromises()
    expect(systemBrand.watermarkEnabled).toBe(false)
    expect(getWatermarkPreview(getCurrentUser()).watermarkEnabled).toBe(true)
    wrapper.vm.tab = 'mail'; await wrapper.vm.$nextTick()
    expect(getWatermarkPreview(getCurrentUser())).toBeNull()
    wrapper.vm.tab = 'brand'; await wrapper.vm.$nextTick()
    expect(getWatermarkPreview(getCurrentUser()).watermarkEnabled).toBe(true)
    wrapper.unmount()
    expect(getWatermarkPreview(getCurrentUser())).toBeNull()
  })
  test('多个系统码可以映射为同一对外 code，失败语义和 HTTP 状态保留', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.tab = 'codes'; wrapper.vm.form.codes.enabled = true
    wrapper.vm.changeMapping('100001', 'code', 'E1000'); wrapper.vm.changeMapping('100002', 'code', 'E1000')
    wrapper.vm.changeMapping('100002', 'message', '参数不正确')
    expect(wrapper.vm.form.codes.mappings['100001'].code).toBe('E1000')
    expect(wrapper.vm.form.codes.mappings['100002'].code).toBe('E1000')
    expect(JSON.parse(wrapper.vm.mappingPreview.external)).toEqual({ success: false, code: 'E1000', message: '参数不正确' })
    expect(wrapper.vm.mappingPreview.httpStatus).toBe(400)
    wrapper.vm.form.codes.enabled = false
    expect(JSON.parse(wrapper.vm.mappingPreview.external).code).toBe('100002')
    wrapper.unmount()
  })
  test('保存邮件草稿仅提交本次输入，后续编辑不回显授权码', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises(); wrapper.vm.tab = 'mail'
    wrapper.vm.form.mail.email = 'notify@example.com'; wrapper.vm.form.mail.host = 'smtp.example.com'; wrapper.vm.mailPassword = 'test-secret'
    const snapshot = { ...wrapper.vm.form.mail, _secretConfigured: { '/password': true } }
    const draft = { id: 10, status: 'EDITING', snapshotDigest: 'digest', draftSnapshotJson: JSON.stringify(snapshot) }
    governance.createResourceDraft.mockResolvedValue({ data: draft }); governance.getWorkspaceDraft.mockResolvedValue({ data: draft })
    await wrapper.vm.persistDraft()
    expect(governance.createResourceDraft).toHaveBeenCalledWith('SYSTEM_SETTING', expect.objectContaining({ password: 'test-secret' }), 'UPDATE', expect.any(Object))
    expect(wrapper.vm.mailPassword).toBe(''); expect(wrapper.vm.form.mail).not.toHaveProperty('password')
    expect(wrapper.vm.mailPasswordConfigured).toBe(true); expect(wrapper.vm.dirty).toBe(false)
    wrapper.unmount()
  })
  test('取消映射修改恢复已加载快照，恢复按钮只影响指定系统码', async () => {
    const data = settings(); data.codes.mappings = { '100001': { code: 'ERR', message: '' }, '100002': { code: 'ERR', message: '' } }
    api.getSystemSettings.mockResolvedValue({ data })
    const wrapper = shallowMount(SystemSettings); await flushPromises(); wrapper.vm.tab = 'codes'
    wrapper.vm.restoreMapping('100001'); expect(wrapper.vm.form.codes.mappings).not.toHaveProperty('100001')
    expect(wrapper.vm.form.codes.mappings['100002'].code).toBe('ERR')
    wrapper.vm.cancelChanges(); expect(wrapper.vm.form.codes.mappings['100001'].code).toBe('ERR'); expect(wrapper.vm.dirty).toBe(false)
    wrapper.unmount()
  })
  test('待审批配置展示已生效值并禁止再次编辑提交', async () => {
    const data = settings(); data.states = [{ resourceId: 4, requestId: 10, requestStatus: 'PENDING', owned: true }]
    api.getSystemSettings.mockResolvedValue({ data })
    const wrapper = shallowMount(SystemSettings); await flushPromises(); wrapper.vm.tab = 'codes'; await wrapper.vm.loadDraft('codes')
    expect(wrapper.vm.draft).toEqual({ id: 10, status: 'PENDING' })
    expect(wrapper.vm.writable).toBe(false); expect(wrapper.vm.form.codes.enabled).toBe(false)
    wrapper.unmount()
  })
  test('邮件预览使用生效有效期，系统通知不显示验证按钮和安全提示', async () => {
    const wrapper = shallowMount(SystemSettings); await flushPromises()
    wrapper.vm.form.access.passwordResetTtlMinutes = 90
    expect(wrapper.vm.mailPreview.highlight).toBe('链接有效期：30 分钟')
    wrapper.vm.mailTemplate = 'notice'; await wrapper.vm.$nextTick()
    expect(wrapper.find('.mail-letter').text()).toContain('尊敬的管理员')
    expect(wrapper.find('.mail-letter .preview-button').exists()).toBe(false)
    expect(wrapper.find('.mail-letter .mail-highlight').exists()).toBe(false)
    expect(wrapper.find('.mail-letter .mail-note').exists()).toBe(false)
    expect(wrapper.find('.mail-letter footer').text()).toContain('上海衡枢真信科技有限公司')
    wrapper.unmount()
  })
})
