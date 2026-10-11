import { flushPromises, mount } from '@test-utils'
import graphDesignerToolsMixin from '@/mixins/graphDesignerToolsMixin'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'

function mountGraph(graph) {
  return mount({
    name: 'DecisionFlow',
    mixins: [graphDesignerToolsMixin],
    components: { RuleDesignerActionBar },
    data: () => ({
      canEditDraft: true, designerBusy: false, designerHasUnsavedChanges: true,
      designerValidationReport: null,
      lf: { getGraphData: () => graph, selectElementById: vi.fn(), focusOn: vi.fn(), resize: vi.fn() },
    }),
    methods: {
      handleCheck: ruleDraftMixin.methods.handleCheck,
      runDesignerAction: action => action(),
      compileDesignerDraft: vi.fn(async function () {
        this.designerValidationReport = { valid: true, errors: [], warnings: [], autoOpen: true }
      }),
      captureDesignerDraftState: vi.fn(), selectNodeData: vi.fn(), selectEdgeData: vi.fn(),
      locateDesignerIssue: vi.fn(),
    },
    template: '<div><div ref="canvasContainer" /><rule-designer-action-bar :can-edit="canEditDraft" :busy="designerBusy" :report="designerValidationReport" @check="handleCheck" @locate="locateGraphValidationIssue" /></div>',
  }, {
    stubs: {
      'el-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
      'el-dialog': {
        props: ['modelValue', 'title'],
        template: '<section v-if="modelValue" role="dialog" :aria-label="title"><slot /><slot name="footer" /></section>',
      },
    },
  })
}

test('检查直接展示问题报告，未保存节点按 ID 定位且关闭报告', async () => {
  const node = { id: 'draft-action', type: 'script-task', properties: { nodeName: '新动作', actionData: [] } }
  const wrapper = mountGraph({ nodes: [node], edges: [] })
  expect(wrapper.find('[data-action="show-report"]').exists()).toBe(false)
  await wrapper.get('[data-action="check"]').trigger('click')
  expect(wrapper.vm.$options.methods.compileDesignerDraft).not.toHaveBeenCalled()
  expect(wrapper.vm.$options.methods.captureDesignerDraftState).toHaveBeenCalledOnce()
  expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('校验报告')
  expect(wrapper.get('[role="dialog"]').text()).toContain('新动作')
  expect(wrapper.get('[role="dialog"]').text()).toContain('未配置动作')
  await wrapper.get('[data-action="locate-issue"]').trigger('click')
  await flushPromises()
  expect(wrapper.vm.$options.methods.selectNodeData).toHaveBeenCalledWith(node)
  expect(wrapper.vm.lf.focusOn).toHaveBeenCalledWith('draft-action')
  expect(wrapper.vm.$options.methods.locateDesignerIssue).not.toHaveBeenCalled()
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  wrapper.unmount()
})

test('检查通过也展示报告，关闭后再次检查读取当前画布而非上一次结果', async () => {
  const graph = { nodes: [{ id: 'start', type: 'start-event' }, { id: 'end', type: 'end-event' }], edges: [] }
  const wrapper = mountGraph(graph)
  await wrapper.get('[data-action="check"]').trigger('click')
  await flushPromises()
  expect(wrapper.vm.$options.methods.compileDesignerDraft).toHaveBeenCalledWith({ checkOnly: true })
  expect(wrapper.get('[role="dialog"]').text()).toContain('格式、Schema 与依赖校验均已通过')
  await wrapper.get('[data-action="close-report"]').trigger('click')
  graph.nodes.push({ id: 'gateway', type: 'exclusive-gateway', properties: { nodeName: '新条件' } })
  await wrapper.get('[data-action="check"]').trigger('click')
  expect(wrapper.get('[role="dialog"]').text()).toContain('至少需要两个出口')
  expect(wrapper.vm.designerValidationReport.valid).toBe(false)
  await wrapper.setData({ designerValidationReport: null })
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  wrapper.unmount()
})

test('连线问题按连线 ID 定位，编译报告仍走已有定位链路', async () => {
  const edge = { id: 'broken-edge', sourceNodeId: 'start', targetNodeId: 'missing' }
  const wrapper = mountGraph({ nodes: [{ id: 'start', type: 'start-event' }], edges: [edge] })
  await wrapper.get('[data-action="check"]').trigger('click')
  await wrapper.get('[data-action="locate-issue"]').trigger('click')
  await flushPromises()
  expect(wrapper.vm.$options.methods.selectEdgeData).toHaveBeenCalledWith(edge)
  expect(wrapper.vm.lf.focusOn).toHaveBeenCalledWith(edge.id)
  const issue = { code: 'COMPILE_FAILED', path: '$.nodes[0]', message: '表达式无效' }
  await wrapper.setData({ designerValidationReport: { valid: false, errors: [issue] } })
  await wrapper.get('[data-action="locate-issue"]').trigger('click')
  expect(wrapper.vm.$options.methods.locateDesignerIssue).toHaveBeenCalledWith(issue)
  wrapper.unmount()
})

test.each([{ designerBusy: true }, { canEditDraft: false }, { lf: null }])('不可检查时不生成误导性报告：%j', async state => {
  const wrapper = mountGraph({ nodes: [], edges: [] })
  await wrapper.setData(state)
  await wrapper.get('[data-action="check"]').trigger('click')
  expect(wrapper.vm.designerValidationReport).toBeNull()
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  wrapper.unmount()
})
