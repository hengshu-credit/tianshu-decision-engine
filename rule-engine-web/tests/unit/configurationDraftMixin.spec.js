import { mount } from '@vue/test-utils'
import configurationDraftMixin from '@/mixins/configurationDraftMixin'
import { clearDesignerLeaveGuards, confirmDesignerPathsCanClose } from '@/utils/designerLeaveGuard'

const mixin = configurationDraftMixin(vm => ({ form: vm.form }))
const component = { template: '<div />', mixins: [mixin], data: () => ({ form: { id: null, name: '', password: '', reference: { id: 1 } } }) }
describe('配置离开保护', () => {
  let wrapper, confirm
  beforeEach(() => {
    confirm = vi.fn()
    wrapper = mount(component, { global: { mocks: { $route: { path: '/database/new' }, $confirm: confirm } } })
  })
  afterEach(() => { wrapper.unmount(); clearDesignerLeaveGuards() })
  it('初始化不误报；更改嵌套引用可阻止关闭页签，取消不丢内容', async () => {
    wrapper.vm.form.name = '加载的配置'
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
    wrapper.vm.markConfigurationSaved()
    wrapper.vm.form.reference.id = 2
    confirm.mockRejectedValue('cancel')
    expect(await confirmDesignerPathsCanClose(['/database/new'])).toBe(false)
    expect(wrapper.vm.form.reference.id).toBe(2)
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(true)
  })
  it('保存期间又修改的内容仍为未保存，新分配的根 ID 不误报', () => {
    wrapper.vm.markConfigurationSaved()
    wrapper.vm.form.name = '提交值'
    const submitted = wrapper.vm.configurationFingerprint
    wrapper.vm.form.name = '保存期间继续编辑'
    wrapper.vm.markConfigurationSaved(submitted)
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(true)
    wrapper.vm.form.name = '提交值'
    wrapper.vm.form.id = 71
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
  })
  it('凭据修改触发刷新保护，但不写浏览器存储', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem')
    wrapper.vm.markConfigurationSaved()
    wrapper.vm.form.password = 'memory-only'
    const event = { preventDefault: vi.fn(), returnValue: null }
    wrapper.vm.handleConfigurationBeforeUnload(event)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(storage).not.toHaveBeenCalled()
    storage.mockRestore()
  })
  it('仅表格编辑状态变化不算内容变化', () => {
    wrapper.vm.markConfigurationSaved()
    wrapper.vm.form.reference._editing = true
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
  })
  it('服务端凭据状态不算编辑，但嵌套业务字段和清除选择仍参与比较', () => {
    wrapper.vm.form.request = { configuredSecrets: '业务字段原值' }
    wrapper.vm.form.clearCredentials = []
    wrapper.vm.markConfigurationSaved()
    wrapper.vm.form._secretConfigured = { '/password': false }
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(false)
    wrapper.vm.form.request.configuredSecrets = '业务字段新值'
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(true)
    wrapper.vm.form.request.configuredSecrets = '业务字段原值'
    wrapper.vm.form.clearCredentials = ['password']
    expect(wrapper.vm.configurationHasUnsavedChanges).toBe(true)
  })
})
