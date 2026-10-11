import { flushPromises, shallowMount } from '@test-utils'
import ExecutionLog from '@/views/log/ExecutionLog.vue'
import request from '@/api/request'
import * as runtimeLogApi from '@/api/runtimeLog'
import { clearCurrentUser, setCurrentUser, setPermissionEnforcement } from '@/security/permissionState'

const tabs = {
  props: ['modelValue'],
  emits: ['update:modelValue', 'tab-click'],
  provide() {
    return { selectTab: name => {
      this.$emit('update:modelValue', name)
      this.$emit('tab-click', { paneName: name })
    } }
  },
  template: '<nav><slot /></nav>',
}
const tab = {
  inject: ['selectTab'],
  props: ['name', 'label'],
  template: '<button :data-tab="name" @click="selectTab(name)">{{ label }}</button>',
}

let wrapper
beforeEach(() => {
  setPermissionEnforcement(true)
  setCurrentUser({ username: 'operator', permissions: ['log:view'] })
  request.mockResolvedValue({ data: { records: [], total: 0 } })
  runtimeLogApi.getExecutionMetrics.mockResolvedValue({ data: {
    instanceId: 'node-a',
    persistence: { queueDepth: 0, queueCapacity: 5000, overflowStrategy: 'DURABLE_OUTBOX_REPLAY' },
    sourceResolution: {}, openExecution: {}, externalCircuitBreakers: {}, ruleWarmup: {},
  } })
  runtimeLogApi.getRuleSetStats.mockResolvedValue({ data: { overview: {}, ruleSets: [] } })
})
afterEach(() => {
  wrapper?.unmount()
  clearCurrentUser()
  setPermissionEnforcement(false)
  vi.clearAllMocks()
})

async function mountLog() {
  wrapper = shallowMount(ExecutionLog, {
    global: {
      mocks: { $route: { params: {}, query: {} }, $router: { push: vi.fn(), replace: vi.fn() } },
      stubs: {
        'el-tabs': tabs,
        'el-tab-pane': tab,
        'el-table': { template: '<div />' },
        'el-drawer': { template: '<div />' },
        ExecutionMetricsPanel: false,
      },
    },
  })
  await flushPromises()
  return wrapper
}

test('运行状态只在有权用户打开页签时加载，并标明当前节点和筛选边界', async () => {
  await mountLog()
  expect(wrapper.find('[data-tab="runtime"]').text()).toBe('运行状态')
  expect(runtimeLogApi.getExecutionMetrics).not.toHaveBeenCalled()
  expect(wrapper.find('.execution-metrics-panel').exists()).toBe(false)
  wrapper.vm.qp.projectCode = 'project-a'
  wrapper.vm.qp.ruleCode = 'risk-rule'

  await wrapper.find('[data-tab="runtime"]').trigger('click')
  await flushPromises()

  expect(runtimeLogApi.getExecutionMetrics).toHaveBeenCalledExactlyOnceWith()
  expect(runtimeLogApi.getRuleSetStats).not.toHaveBeenCalled()
  expect(wrapper.find('[data-testid="execution-runtime-scope"]').text()).toContain('不受项目、规则或时间筛选影响')
  expect(wrapper.find('[data-testid="execution-metrics-instance"]').text()).toContain('当前节点：node-a')
  expect(wrapper.findComponent({ name: 'ExecutionMetricsPanel' }).vm.persistenceDetail).toContain('持久化队列重放')
  expect(wrapper.find('.rule-set-stats').exists()).toBe(false)
  expect(wrapper.vm.qp).toMatchObject({ projectCode: 'project-a', ruleCode: 'risk-rule' })
})

test('切回明细保留筛选，再次打开运行状态重新读取快照', async () => {
  await mountLog()
  wrapper.vm.qp.traceId = 'trace-filter'
  await wrapper.find('[data-tab="runtime"]').trigger('click')
  await flushPromises()
  await wrapper.find('[data-tab="logs"]').trigger('click')
  expect(wrapper.find('.execution-metrics-panel').exists()).toBe(false)
  expect(wrapper.vm.qp.traceId).toBe('trace-filter')
  await wrapper.find('[data-tab="runtime"]').trigger('click')
  await flushPromises()
  expect(runtimeLogApi.getExecutionMetrics).toHaveBeenCalledTimes(2)
})

test('缺少log:view时不提供运行状态入口，也不发送指标请求', async () => {
  setCurrentUser({ username: 'limited', permissions: [] })
  await mountLog()
  expect(wrapper.find('[data-tab="runtime"]').exists()).toBe(false)
  await wrapper.vm.handleViewChange({ paneName: 'runtime' })
  await flushPromises()
  expect(wrapper.vm.activeView).toBe('logs')
  expect(runtimeLogApi.getExecutionMetrics).not.toHaveBeenCalled()
  expect(wrapper.find('.execution-metrics-panel').exists()).toBe(false)
})
