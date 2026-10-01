import { shallowMount } from '@test-utils'
import ProjectWorkbenchOverview from '@/components/ProjectWorkbenchOverview.vue'

const workbench = {
  metrics: {
    fieldCount: 6,
    dataSourceCount: 2,
    enabledDataSourceCount: 1,
    modelCount: 0,
    draftRuleCount: 2,
    publishedRuleCount: 1,
    pendingApprovalCount: 3,
    recentExecutionCount: 20,
    recentSuccessRate: 95,
  },
  checks: [
    {
      code: 'FIELD',
      title: '定义业务字段',
      status: 'READY',
      reason: '已配置字段',
      actionCode: 'CONFIGURE_FIELDS',
      actionLabel: '查看字段',
    },
    {
      code: 'RULE',
      title: '设计决策规则',
      status: 'ACTION_REQUIRED',
      reason: '需要补充规则',
      actionCode: 'CONFIGURE_RULES',
      actionLabel: '创建规则',
    },
    {
      code: 'RUN',
      title: '验证线上运行',
      status: 'BLOCKED',
      reason: '需要先发布规则',
      actionCode: 'CONFIGURE_RULES',
      actionLabel: '先发布规则',
    },
  ],
  warnings: [],
  recentExecution: null,
}

describe('ProjectWorkbenchOverview', () => {
  test.each([
    [{ loading: true }, '正在检查'],
    [{ error: '连接失败' }, '状态读取失败'],
    [{}, '尚未获取检查结果'],
    [{ workbench: { checks: [], metrics: {} } }, '尚未获取检查结果'],
    [{ workbench: { checks: [{ code: 'RULE', status: 'READY' }], warnings: ['执行记录暂时无法读取'] } }, '部分状态待确认'],
  ])('未获得完整结果时不宣称就绪：%s', (props, label) => {
    const wrapper = shallowMount(ProjectWorkbenchOverview, { props })
    expect(wrapper.text()).toContain(label)
    expect(wrapper.text()).not.toContain('当前已就绪')
    wrapper.unmount()
  })

  test('刷新或失败时隐藏旧的检查建议并提供重试入口', async () => {
    const wrapper = shallowMount(ProjectWorkbenchOverview, {
      props: { workbench, loading: true },
    })
    expect(wrapper.find('.next-action-card').exists()).toBe(false)
    await wrapper.setProps({ loading: false, error: '连接失败' })
    expect(wrapper.find('.next-action-card').exists()).toBe(false)
    await wrapper.get('[data-testid="refresh-workbench"]').trigger('click')
    expect(wrapper.emitted('action')).toContainEqual([
      { actionCode: 'REFRESH_WORKBENCH' },
    ])
    wrapper.unmount()
  })

  test('只有完整返回且全部已处理时才显示就绪', () => {
    const wrapper = shallowMount(ProjectWorkbenchOverview, {
      props: { workbench: { checks: [{ code: 'RULE', status: 'READY' }] } },
    })
    expect(wrapper.text()).toContain('当前检查项已就绪')
    wrapper.unmount()
  })

  test('优先展示被阻塞的下一步并汇总真实指标', () => {
    const wrapper = shallowMount(ProjectWorkbenchOverview, {
      props: { workbench },
    })

    expect(wrapper.vm.nextCheck.code).toBe('RUN')
    expect(wrapper.vm.readyCount).toBe(1)
    expect(wrapper.vm.remainingCount).toBe(2)
    expect(wrapper.vm.metricItems.map((item) => item.value)).toContain(
      '1 已发布'
    )
    expect(wrapper.text()).toContain('需要先发布规则')
  })

  test('可选项计入已处理且缺失指标显示占位符', () => {
    const wrapper = shallowMount(ProjectWorkbenchOverview, {
      props: {
        workbench: {
          metrics: {},
          checks: [{ code: 'MODEL', status: 'OPTIONAL' }],
        },
      },
    })

    expect(wrapper.vm.readyCount).toBe(1)
    expect(wrapper.vm.remainingCount).toBe(0)
    expect(wrapper.vm.metricItems[0].value).toBe('-')
  })
})
