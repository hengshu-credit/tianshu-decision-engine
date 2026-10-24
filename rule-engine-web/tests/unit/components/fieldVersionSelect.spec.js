import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import FieldVersionSelect from '@/components/common/FieldVersionSelect.vue'
import { listFieldVersions, getFieldVersion, getFieldVersionFields } from '@/api/fieldPickerVersion'

vi.mock('@/api/fieldPickerVersion', () => ({ listFieldVersions: vi.fn(), getFieldVersion: vi.fn(), getFieldVersionFields: vi.fn() }))
const VarPicker = (await vi.importActual('@/components/common/VarPicker.vue')).default
const item = { id: '19', _varId: '19', _refType: 'VARIABLE', varCode: 'now', varType: 'STRING' }
const fixed = { versionMode: 'FIXED', versionId: '9007199254740993', version: 2, versionOwnerId: '19', code: 'old', label: '历史名称', valueType: 'STRING' }

describe('字段版本选择', () => {
  beforeEach(() => vi.clearAllMocks())
  it('再次打开下拉会读取新发布的版本', async () => {
    listFieldVersions.mockResolvedValueOnce({ data: [{ versionId: '11', version: 1 }] }).mockResolvedValueOnce({ data: [{ versionId: '12', version: 2 }, { versionId: '11', version: 1 }] })
    const wrapper = shallowMount(FieldVersionSelect, { props: { item } })
    await wrapper.vm.load(true); await wrapper.vm.load(false); await wrapper.vm.load(true)
    expect(wrapper.vm.versions.map(version => version.versionId)).toEqual(['12', '11'])
    wrapper.unmount()
  })
  it('重新打开业务版本字段时保留来源，并区分同号历史快照缓存', async () => {
    const modelItem = { id: '2', _varId: '2', _refType: 'MODEL_OUTPUT', varCode: 'm.current', varType: 'NUMBER', _ref: { category: 'model', modelId: '7', modelCode: 'm' } }
    const value = { kind: 'REFERENCE', refType: 'MODEL_OUTPUT', refId: '1', code: 'm.old', valueType: 'NUMBER', versionMode: 'FIXED', versionId: '8', versionOwnerId: '7', versionSource: 'BUSINESS_VERSION' }
    getFieldVersionFields.mockResolvedValue({ data: { ...value, fields: [{ refId: '1', refType: 'MODEL_OUTPUT', code: 'm.old', valueType: 'NUMBER' }] } })
    const wrapper = shallowMount(VarPicker, { props: { vars: [modelItem], operandMode: true, value } })
    await wrapper.setData({ groupVersions: { 'model:7': { versionId: '8', versionMode: 'FIXED' } }, popoverVisible: true })
    await flushPromises()
    expect(getFieldVersionFields).toHaveBeenCalledWith({ module: 'model', ownerId: '7' }, '8', 'BUSINESS_VERSION')
    expect(wrapper.vm.groupVersions['model:7'].versionSource).toBe('BUSINESS_VERSION')
    wrapper.unmount()
  })
  it('打开下拉才读取版本；选择固定版本先读取历史元信息；选择最新移除固定 ID', async () => {
    listFieldVersions.mockResolvedValue({ data: [{ versionId: fixed.versionId, version: 2 }] })
    getFieldVersion.mockResolvedValue({ data: fixed })
    const wrapper = shallowMount(FieldVersionSelect, { props: { item } })
    expect(listFieldVersions).not.toHaveBeenCalled()
    await wrapper.vm.load(true)
    await wrapper.vm.load(true)
    expect(listFieldVersions).toHaveBeenCalledTimes(1)
    await wrapper.vm.change(fixed.versionId)
    expect(wrapper.emitted('change')[0][0]).toMatchObject(fixed)
    await wrapper.vm.change('LATEST')
    expect(wrapper.emitted('change')[1][0]).toMatchObject({ versionMode: 'LATEST', versionId: null, version: 2 })
    wrapper.unmount()
  })
  it('未选字段只暂存版本，已选字段修改版本直接同步输入框', async () => {
    const wrapper = shallowMount(VarPicker, { props: { vars: [item], operandMode: true, value: null } })
    wrapper.vm.openPopover()
    await flushPromises()
    wrapper.vm.changeFieldVersion(item, fixed)
    expect(wrapper.emitted('input')).toBeUndefined()
    wrapper.vm.onItemClick(wrapper.vm.panelItems[0])
    expect(wrapper.emitted('input')[0][0]).toMatchObject({ ...fixed, refId: '19' })
    await wrapper.setProps({ value: wrapper.emitted('input')[0][0] })
    wrapper.vm.changeFieldVersion(item, { versionMode: 'LATEST', versionId: null, version: 3 })
    expect(wrapper.emitted('input').at(-1)[0]).toMatchObject({ code: 'now', versionMode: 'LATEST', versionId: null, version: 3 })
    wrapper.unmount()
  })
  it('模型切换历史版本使用历史子字段 ID，并能重新定位已选历史字段', async () => {
    const modelItem = { id: '2', _varId: '2', _refType: 'MODEL_OUTPUT', varCode: 'm.current', varType: 'NUMBER', _ref: { category: 'model', modelId: '7', modelCode: 'm' } }
    const value = { kind: 'REFERENCE', refType: 'MODEL_OUTPUT', refId: '1', code: 'm.old', valueType: 'NUMBER', ...fixed, versionOwnerId: '7' }
    getFieldVersionFields.mockResolvedValue({ data: { versionId: fixed.versionId, version: 2, versionOwnerId: '7', ownerCode: 'm', fields: [{ refId: '1', refType: 'MODEL_OUTPUT', code: 'm.old', valueType: 'NUMBER' }] } })
    const wrapper = shallowMount(VarPicker, { props: { vars: [modelItem], operandMode: true, value } })
    await wrapper.setData({ popoverVisible: true })
    await flushPromises()
    expect(wrapper.vm.panelItems.map(row => row._varId)).toEqual(['1', '7'])
    expect(wrapper.vm.panelItems[1]).toMatchObject({ _refType: 'MODEL', rootReference: true, versionMode: 'FIXED' })
    expect(wrapper.vm.panelSelectedKey).toBe('MODEL_OUTPUT:1')
    expect(wrapper.vm.panelItems[0]).toMatchObject({ versionMode: 'FIXED', versionId: fixed.versionId })
    wrapper.unmount()
  })
  it('函数当前签名改变后，仍显示并选择所绑定版本的名称、返回类型和参数', async () => {
    const value = { kind: 'FUNCTION', ...fixed, functionId: '19', functionCode: 'oldFn', label: '旧函数', valueType: 'NUMBER', parameters: [{ name: 'n', type: 'NUMBER' }], args: [] }
    const wrapper = shallowMount(VarPicker, { props: { operandMode: true, allowedKinds: ['FUNCTION'], expectedType: 'NUMBER', value,
      functions: [{ id: '19', funcCode: 'newFn', funcName: '新函数', returnType: 'STRING', paramsJson: '[]' }] } })
    wrapper.vm.openPopover()
    await flushPromises()
    expect(wrapper.vm.panelItems[0]).toMatchObject({ varCode: 'oldFn', varLabel: '旧函数', varType: 'NUMBER' })
    wrapper.vm.onItemClick(wrapper.vm.panelItems[0])
    expect(wrapper.emitted('input')[0][0]).toMatchObject({ versionId: fixed.versionId, functionCode: 'oldFn', valueType: 'NUMBER', parameterCount: 1 })
    wrapper.unmount()
  })
  it('旧函数版本与治理版本 ID 相同时仍可分别选择', async () => {
    listFieldVersions.mockResolvedValue({ data: [{ versionId: '19', version: 1 }, { versionId: '19', version: 3, versionSource: 'FUNCTION_HISTORY' }] })
    getFieldVersion.mockResolvedValue({ data: { ...fixed, versionId: '19', versionSource: 'FUNCTION_HISTORY' } })
    const wrapper = shallowMount(FieldVersionSelect, { props: { item: { _function: true, function: { id: '19' } } } })
    await wrapper.vm.load(true)
    expect(wrapper.vm.versions.map(wrapper.vm.versionKey)).toEqual(['19', 'FUNCTION_HISTORY:19'])
    await wrapper.vm.change('FUNCTION_HISTORY:19')
    expect(getFieldVersion).toHaveBeenCalledWith(expect.objectContaining({ refType: 'FUNCTION' }), '19', 'FUNCTION_HISTORY')
    expect(wrapper.emitted('change')[0][0]).toMatchObject({ versionSource: 'FUNCTION_HISTORY', versionMode: 'FIXED' })
    wrapper.unmount()
  })
  it('历史元信息加载期间不允许双击提交旧值', async () => {
    const wrapper = shallowMount(VarPicker, { props: { operandMode: true, vars: [item] } })
    wrapper.vm.openPopover()
    await flushPromises()
    wrapper.vm.pendingVersions[wrapper.vm.pendingVersionKey(item)] = true
    wrapper.vm.onItemClick(item)
    expect(wrapper.emitted('input')).toBeUndefined()
    wrapper.vm.pendingVersions[wrapper.vm.pendingVersionKey(item)] = false
    wrapper.vm.changeFieldVersion(item, fixed)
    wrapper.vm.onItemClick(wrapper.vm.panelItems[0])
    expect(wrapper.emitted('input')[0][0]).toMatchObject({ versionId: fixed.versionId, code: 'old' })
    wrapper.unmount()
  })
})
