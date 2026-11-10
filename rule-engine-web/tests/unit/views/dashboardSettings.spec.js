import { flushPromises } from '@vue/test-utils'
import { shallowMount, mount } from '@test-utils'
import * as dashboardApi from '@/api/dashboard'
import * as projectApi from '@/api/project'
import { setCurrentUser, clearCurrentUser } from '@/security/permissionState'
import DashboardSettings from '@/views/dashboard/DashboardSettings.vue'
import ContentReference from '@/components/common/ContentReference.vue'
import { createReferenceOperand } from '@/utils/operand'
import { DASHBOARD_MAP_VIEW_KEY } from '@/utils/dashboardFilters'

const settings = {
  editable: true,
  projectId: null,
  projectOverride: false,
  options: [{
    refType: 'VARIABLE', refId: 3, label: '请求编号',
    valueType: 'STRING', scope: 'GLOBAL', projectId: 0
  }],
  fields: [{
    metricField: 'REQUEST_ID',
    label: '请求编号',
    mapping: {
      refType: 'VARIABLE', refId: 3, jsonSource: 'INPUT',
      mappingSource: 'SYSTEM_DEFAULT', valid: true
    }
  }]
}

describe('DashboardSettings', () => {
  beforeEach(() => {
    setCurrentUser({ userId: 8, username: 'demo', permissions: ['rule:view'], globalEditable: true })
    window.sessionStorage.clear()
    projectApi.listProjects.mockResolvedValue({ data: { records: [] } })
    dashboardApi.getDashboardSettings.mockResolvedValue({ data: settings })
    dashboardApi.saveDashboardSettings.mockResolvedValue({ data: settings })
  })

  afterEach(() => { vi.clearAllMocks(); clearCurrentUser() })

  test('字段与常量使用统一引用展示，决策结果保存常量 ID 而非取值文本', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    wrapper.vm.applySettings({ ...settings, options: [
      { refType: 'VARIABLE', refId: 500, code: 'Decision.Code', label: '决策结果', valueType: 'STRING', varSource: 'INPUT' },
      ...[501, 502, 503].map((id, index) => ({ refType: 'VARIABLE', refId: id,
        code: ['APPROVE_CODE', 'REVIEW_CODE', 'REJECT_CODE'][index], label: ['通过常量', '人审常量', '拒绝常量'][index],
        valueType: 'STRING', varSource: 'CONSTANT', constantValue: ['PASS', 'REVIEW', 'REJECT'][index] })),
    ], fields: [{ metricField: 'DECISION_RESULT', label: '决策结果', mapping: {
      refType: 'VARIABLE', refId: 500, jsonSource: 'OUTPUT', mappingSource: 'SYSTEM_DEFAULT', valid: true,
      decisionValues: { pass: ['PASS'], review: ['REVIEW'], reject: ['REJECT'] },
    } }] })
    const display = mount(ContentReference, { props: { value: wrapper.vm.fieldPickerOptions(wrapper.vm.decisionRow)[0] } })
    expect(display.find('[data-content-part="code"]').text()).toBe('Decision.Code')
    expect(display.text()).toContain('决策结果')
    expect(display.text()).toContain('最新')
    expect(wrapper.vm.constantOptions).toHaveLength(3)
    for (const [index, group] of ['pass', 'review', 'reject'].entries()) {
      wrapper.vm.removeDecisionOperand(group, 0)
      wrapper.vm.addDecisionOperand(group, createReferenceOperand(wrapper.vm.constantOptions[index]))
    }
    const constants = wrapper.vm.constantOptions
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings.mock.calls[0][1].mappings.DECISION_RESULT).toEqual({
      refType: 'VARIABLE', refId: 500, jsonSource: 'OUTPUT', decisionValues: {
        passOperands: [createReferenceOperand(constants[0])],
        reviewOperands: [createReferenceOperand(constants[1])],
        rejectOperands: [createReferenceOperand(constants[2])],
      },
    })
    display.unmount()
    wrapper.unmount()
  })

  test('三组阈值和路径表达式可编辑、删除、保存并重载，逗号和大小写原样保留', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    const configured = { ...settings, fields: [{ metricField: 'DECISION_RESULT', label: '决策结果', mapping: {
      refType: 'VARIABLE', refId: 3, jsonSource: 'OUTPUT', mappingSource: 'SYSTEM_DEFAULT',
      decisionValues: { pass: ['allow,Case'], review: ['review'], reject: ['reject'] },
    } }] }
    wrapper.vm.applySettings(configured)
    expect(wrapper.vm.decisionRow.passOperands[0].value).toBe('allow,Case')
    const path = { kind: 'PATH', refType: 'VARIABLE', refId: '9007199254740993', code: 'response', relativePath: '.decision', valueType: 'STRING' }
    const expression = { kind: 'OPERATION', terms: [{ operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' } }, { operator: '+', operand: { kind: 'LITERAL', value: '2', valueType: 'NUMBER' } }] }
    wrapper.vm.updateDecisionOperand('review', 0, path)
    wrapper.vm.updateDecisionOperand('reject', 0, expression)
    wrapper.vm.addDecisionOperand('pass', { kind: 'LITERAL', value: '0', valueType: 'NUMBER' })
    wrapper.vm.removeDecisionOperand('pass', 1)
    expect(wrapper.vm.initialRows[0].reviewOperands[0].value).toBe('review')
    dashboardApi.saveDashboardSettings.mockImplementation(async (scope, body) => ({ data: {
      ...configured, fields: [{ ...configured.fields[0], mapping: { ...body.mappings.DECISION_RESULT, mappingSource: 'USER_DEFAULT' } }],
    } }))
    await wrapper.vm.save()
    expect(wrapper.vm.decisionRow.reviewOperands).toEqual([path])
    expect(wrapper.vm.decisionRow.rejectOperands).toEqual([expression])
    expect(wrapper.vm.decisionRow.passOperands[0].value).toBe('allow,Case')
    expect(wrapper.vm.decisionKinds).toEqual(expect.arrayContaining(['LITERAL', 'PATH', 'OPERATION']))
    wrapper.vm.editSystemDefaults = true
    wrapper.vm.settings.defaultVersion = 2
    dashboardApi.saveDashboardDefaults.mockResolvedValue({ data: settings })
    await wrapper.vm.restore(wrapper.vm.decisionRow)
    expect(wrapper.vm.decisionRow.passOperands).toEqual([])
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardDefaults).toHaveBeenCalledWith({ version: 2, mappings: {} })
    wrapper.unmount()
  })

  test('加载解析后的继承来源并按 ID 保存映射', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    expect(wrapper.vm.rows[0].referenceKey).toBe('VARIABLE:3')
    expect(wrapper.vm.rows[0].source).toBe('SYSTEM_DEFAULT')
    expect(wrapper.vm.sourceLabel(wrapper.vm.rows[0].source)).toBe('公共默认')

    wrapper.vm.rows[0].referenceKey = 'VARIABLE:4'
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings).toHaveBeenCalledWith(
      { projectId: null },
      { mappings: {
        REQUEST_ID: {
          refType: 'VARIABLE', refId: 4, jsonSource: 'INPUT',
          decisionValues: null
        }
      } }
    )
    wrapper.unmount()
  })

  test('普通账户不显示公共配置入口，原样保存不冻结继承值', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    expect(wrapper.vm.canManageDefaults).toBe(false)
    expect(wrapper.text()).not.toContain('载入我的默认配置')
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings).toHaveBeenCalledWith({ projectId: null }, { mappings: {} })
    expect(dashboardApi.saveDashboardDefaults).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('管理员保存公共默认携带版本且不会写入个人偏好', async () => {
    setCurrentUser({ userId: 1, permissions: ['system:edit'], globalEditable: true })
    dashboardApi.getDashboardDefaults.mockResolvedValue({ data: { ...settings, defaultVersion: 2 } })
    dashboardApi.saveDashboardDefaults.mockResolvedValue({ data: { ...settings, defaultVersion: 3 } })
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    expect(wrapper.vm.canManageDefaults).toBe(true)
    wrapper.vm.editSystemDefaults = true
    await wrapper.vm.load()
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardDefaults).toHaveBeenCalledWith({ version: 2, mappings: {
      REQUEST_ID: { refType: 'VARIABLE', refId: 3, jsonSource: 'INPUT', decisionValues: null },
    } })
    expect(dashboardApi.saveDashboardSettings).not.toHaveBeenCalled()
    expect(wrapper.vm.settings.defaultVersion).toBe(3)
    wrapper.unmount()
  })

  test('公共配置可以载入管理员个人映射，保持公共版本，并支持清除字段', async () => {
    dashboardApi.getDashboardDefaults.mockResolvedValue({ data: { ...settings, defaultVersion: 7 } })
    dashboardApi.saveDashboardDefaults.mockResolvedValue({ data: { ...settings, defaultVersion: 8 } })
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    wrapper.vm.editSystemDefaults = true
    await wrapper.vm.load()
    await wrapper.vm.loadPersonalDefaults()
    expect(wrapper.vm.settings.defaultVersion).toBe(7)
    expect(wrapper.vm.rows[0].referenceKey).toBe('VARIABLE:3')
    await wrapper.vm.restore(wrapper.vm.rows[0])
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardDefaults).toHaveBeenCalledWith({ version: 7, mappings: {} })
    wrapper.unmount()
  })

  test('保存其他个人字段时保留现有覆盖，未配置公共项不阻止保存', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()
    wrapper.vm.applySettings({ ...settings, fields: [
      { ...settings.fields[0], mapping: { ...settings.fields[0].mapping, mappingSource: 'USER_DEFAULT' } },
      { metricField: 'AMOUNT', label: '金额', mapping: { mappingSource: 'SYSTEM_DEFAULT', valid: false } },
    ] })
    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings.mock.calls[0][1].mappings).toEqual({
      REQUEST_ID: { refType: 'VARIABLE', refId: 3, jsonSource: 'INPUT', decisionValues: null },
    })
    expect(wrapper.vm.parseReference('VARIABLE:9007199254740993').refId).toBe('9007199254740993')
    wrapper.unmount()
  })

  test('只读模式禁用保存', async () => {
    dashboardApi.getDashboardSettings.mockResolvedValueOnce({
      data: { ...settings, editable: false }
    })
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    await wrapper.vm.save()
    expect(dashboardApi.saveDashboardSettings).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('地图视角在当前会话保存并可一键恢复中国默认值', async () => {
    const wrapper = shallowMount(DashboardSettings)
    await flushPromises()

    expect(wrapper.find('.dashboard-map-view-settings').exists()).toBe(true)
    expect(wrapper.text()).toContain('保存地图视角')
    expect(wrapper.vm.mapView).toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })

    wrapper.vm.mapView = { longitude: 112, latitude: 31, zoom: 2.4 }
    await wrapper.vm.saveMapView()
    expect(JSON.parse(window.sessionStorage.getItem(DASHBOARD_MAP_VIEW_KEY)))
      .toEqual({ longitude: 112, latitude: 31, zoom: 2.4 })

    await wrapper.vm.restoreChinaMapView()
    expect(wrapper.vm.mapView).toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })
    expect(JSON.parse(window.sessionStorage.getItem(DASHBOARD_MAP_VIEW_KEY)))
      .toEqual({ longitude: 104, latitude: 35, zoom: 1.5 })
    wrapper.unmount()
  })
})
