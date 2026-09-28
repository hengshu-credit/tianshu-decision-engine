import { flushPromises, mount } from '@test-utils'
import { getResourcePreflight } from '@/api/preflight'
import ResourcePreflightPanel from '@/components/common/ResourcePreflightPanel.vue'

vi.mock('@/api/preflight', () => ({
  getResourcePreflight: vi.fn(),
}))

describe('ResourcePreflightPanel', () => {
  afterEach(() => vi.clearAllMocks())

  test('展示已保存配置检查结果并保留检查范围', async () => {
    getResourcePreflight.mockResolvedValueOnce({
      data: {
        resourceType: 'DATABASE',
        resourceId: 8,
        checkedAt: '2026-09-28T10:00:00',
        checkScope: 'SAVED_CONFIGURATION',
        valid: false,
        errors: [{ code: 'DATASOURCE_DISABLED', severity: 'ERROR', title: '配置校验问题', path: 'status', message: '已停用', nextAction: '启用' }],
        warnings: [],
      },
    })
    const wrapper = mount(ResourcePreflightPanel, {
      props: { resourceType: 'DATABASE', resourceId: 8, configSignature: 'v1' },
      global: {
        stubs: {
          'el-button': true,
          'el-alert': true,
          'rule-validation-report': true,
        },
      },
    })
    await wrapper.find('[data-action="run-resource-preflight"]').trigger('click')
    await flushPromises()

    expect(getResourcePreflight).toHaveBeenCalledWith('DATABASE', 8)
    expect(wrapper.vm.report.valid).toBe(false)
    expect(wrapper.vm.reportDescription).toContain('SAVED_CONFIGURATION')
    expect(wrapper.find('[data-testid="resource-preflight-panel"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('请求失败不生成正常兜底报告', async () => {
    getResourcePreflight.mockRejectedValue(new Error('预检服务不可用'))
    const wrapper = mount(ResourcePreflightPanel, {
      props: { resourceType: 'MODEL', resourceId: 3 },
      global: {
        stubs: {
          'el-button': true,
          'el-alert': true,
          'rule-validation-report': true,
        },
      },
    })
    await wrapper.vm.runCheck()
    expect(wrapper.vm.report).toBeNull()
    expect(wrapper.vm.requestError).toBe('预检服务不可用')
    expect(wrapper.vm.loading).toBe(false)
    wrapper.unmount()
  })

  test('新建资源只显示先保存提示，不渲染低对比度禁用按钮', () => {
    const wrapper = mount(ResourcePreflightPanel, {
      props: { resourceType: 'MODEL' },
      global: {
        stubs: {
          'el-button': true,
          'el-alert': true,
          'rule-validation-report': true,
        },
      },
    })
    expect(wrapper.find('[data-action="run-resource-preflight"]').exists()).toBe(false)
    expect(wrapper.vm.hasResourceId).toBe(false)
    wrapper.unmount()
  })
})
