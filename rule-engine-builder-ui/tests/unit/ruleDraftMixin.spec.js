import { flushPromises, mount } from '@test-utils'
import { reactive } from 'vue'
import * as definitionApi from '@/api/definition'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import {
  clearDesignerLeaveGuards,
  confirmDesignerPathsCanClose,
} from '@/utils/designerLeaveGuard'

function mountHost() {
  return mount({
    name: 'RuleDraftMixinHost',
    mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
    template: '<div />',
  }, {
    mocks: {
      $route: { params: { id: 30 }, query: {} },
      $router: { push: vi.fn(), replace: vi.fn() },
    },
  })
}

function mountCachedHost() {
  return mount({
    name: 'RuleDraftMixinCacheHarness',
    components: {
      CachedHost: {
        name: 'CachedRuleDraftMixinHost',
        mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
        template: '<div />',
      },
    },
    data() {
      return { active: true }
    },
    template:
      '<keep-alive><cached-host v-if="active" ref="host" /></keep-alive>',
  }, {
    mocks: {
      $route: { params: { id: 30 }, query: {} },
      $router: { push: vi.fn() },
    },
  })
}

describe('ruleDraftMixin', () => {
  test('校验定位锁定来源修订，草稿锁版本变化时提示重新校验', () => {
    const context = {
      $route: { query: { sourceType: 'REVISION', sourceId: '6', validationSourceId: '6', validationLockVersion: '4', validationPath: '$.nodes[0]', validationMessage: '检查字段' } },
      viewRevision: { id: 6, lockVersion: 4 },
    }
    expect(ruleDraftMixin.computed.incomingValidationIssue.call(context).path).toBe('$.nodes[0]')
    context.viewRevision.lockVersion = 5
    expect(ruleDraftMixin.computed.incomingValidationIssue.call(context).stale).toBe(true)
    context.$route.query.sourceId = '7'
    expect(ruleDraftMixin.computed.incomingValidationIssue.call(context)).toBeNull()
  })
  beforeEach(() => {
    vi.clearAllMocks()
    window.sessionStorage.clear()
    clearDesignerLeaveGuards()
  })

  test('保存编译成功后执行发布前检查并停留在设计器', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    definitionApi.compileDesignerModel.mockResolvedValueOnce({
      data: { compileSuccess: true, preflightReport: { valid: true, errors: [], warnings: [] } },
    })
    const wrapper = mountHost()
    await flushPromises()
    wrapper.vm.initializeDesignerDraftTracking('{}')
    wrapper.vm.markDesignerDraftSaved('{}')

    wrapper.vm.serializeDesignerDraft = () => '{}'
    await wrapper.vm.compileDesignerDraft()

    expect(definitionApi.compileDesignerModel).toHaveBeenCalledWith('30', expect.objectContaining({ modelJson: '{}' }))
    expect(wrapper.vm.designerActionState).toBe('READY_TO_TEST')
    expect(wrapper.vm.designerCanTest).toBe(true)
    expect(wrapper.vm.designerValidationReport).toEqual({
      valid: true,
      errors: [],
      warnings: [],
    })
    expect(wrapper.vm.$router.push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('内容变更只留在页面，不自动记录会话草稿，并统一拦截离开', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    const wrapper = mountHost()
    await flushPromises()
    wrapper.vm.initializeDesignerDraftTracking('{"script":"old"}')
    wrapper.vm.serializeDesignerDraft = () => '{"script":"new"}'

    wrapper.vm.captureDesignerDraftState()

    expect(wrapper.vm.designerActionState).toBe('DIRTY')
    expect(wrapper.vm.designerHasUnsavedChanges).toBe(true)
    expect(wrapper.vm.designerRecoveryCandidate).toBeNull()
    expect(window.sessionStorage.length).toBe(0)
    await expect(wrapper.vm.confirmDesignerLeave()).resolves.toBe(true)
    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      '当前设计有未保存修改，放弃修改并离开吗？',
      '未保存提醒',
      expect.objectContaining({ type: 'warning' })
    )
    wrapper.unmount()
  })

  test('默认打开最新正式版本而不是现有草稿，明确暂存前没有写请求', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [{ id: 6, state: 'DRAFT', revisionNo: 3, modelJson: '{}' }] })
    definitionApi.listPublishedVersions.mockResolvedValueOnce({ data: [{ id: 81, version: 2 }, { id: 80, version: 1 }] })
    definitionApi.getVersionById.mockResolvedValueOnce({ data: { id: 81, definitionId: 30, version: 2, modelJson: '{"rules":[]}' } })
    const wrapper = mountHost()
    await flushPromises()
    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '81')
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 81, state: 'VERSION' })
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(definitionApi.createDraftFromSource).not.toHaveBeenCalled()
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('工作区缓存设计器激活后按当前路由重新注册离开保护', async() => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    const route = reactive({
      fullPath: '/designer/table/30',
      path: '/designer/table/30',
      name: 'DecisionTable',
      params: { id: '30' },
      query: {},
    })
    const wrapper = mount({
      name: 'ActivatedDesignerLeaveGuardHost',
      mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
      data() {
        return { definitionId: 30 }
      },
      template: '<div />',
    }, {
      mocks: {
        $route: route,
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
    await flushPromises()
    wrapper.vm.initializeDesignerDraftTracking('{"script":"old"}')
    wrapper.vm.serializeDesignerDraft = () => '{"script":"new"}'
    wrapper.vm.captureDesignerDraftState()
    wrapper.vm.$confirm = vi.fn().mockRejectedValueOnce(new Error('cancel'))

    clearDesignerLeaveGuards()
    ruleDraftMixin.activated.call(wrapper.vm)

    await expect(
      confirmDesignerPathsCanClose(['/designer/table/30'])
    ).resolves.toBe(false)
    expect(wrapper.vm.$confirm).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('重新进入时不会自动恢复从未暂存的页面修改', async() => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    const first = mountHost()
    await flushPromises()
    first.vm.initializeDesignerDraftTracking('{"script":"old"}')
    first.vm.serializeDesignerDraft = () => '{"script":"new"}'
    first.vm.captureDesignerDraftState()
    first.unmount()

    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    const reopened = mountHost()
    await flushPromises()
    reopened.vm.initializeDesignerDraftTracking('{"script":"old"}')

    expect(reopened.vm.designerRecoveryCandidate).toBeNull()
    expect(window.sessionStorage.length).toBe(0)
    reopened.unmount()
  })

  test('发布前检查存在阻断项时仍可测试并展示报告', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 }],
    })
    definitionApi.compileDesignerModel.mockResolvedValueOnce({
      data: { compileSuccess: true, preflightReport: {
        valid: false,
        errors: [{ code: 'MISSING_REFERENCE', message: '变量不存在' }],
        warnings: [],
      } },
    })
    const wrapper = mountHost()
    await flushPromises()
    wrapper.vm.initializeDesignerDraftTracking('{}')
    wrapper.vm.markDesignerDraftSaved('{}')

    wrapper.vm.serializeDesignerDraft = () => '{}'
    await wrapper.vm.compileDesignerDraft()

    expect(wrapper.vm.designerActionState).toBe('CHECK_FAILED')
    expect(wrapper.vm.designerCanTest).toBe(true)
    expect(wrapper.vm.designerValidationReport.valid).toBe(false)
    wrapper.unmount()
  })

  test('切换设计来源前必须确认放弃未保存修改', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 6, definitionId: 30, state: 'DRAFT', lockVersion: 4 },
        { id: 5, definitionId: 30, state: 'PUBLISHED', revisionNo: 1 },
      ],
    })
    const wrapper = mountHost()
    await flushPromises()
    wrapper.vm.initializeDesignerDraftTracking('{"script":"old"}')
    wrapper.vm.serializeDesignerDraft = () => '{"script":"new"}'
    wrapper.vm.captureDesignerDraftState()
    wrapper.vm.requestDesignerChoice = async () => ({ action: 'cancel' })

    await wrapper.vm.switchDesignerSource('REVISION:5')

    expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
    expect(wrapper.vm.designerActionState).toBe('DIRTY')
    wrapper.unmount()
  })

  test('有 DRAFT 时保存携带 revisionId 和 lockVersion 并更新乐观锁', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    const result = await wrapper.vm.saveDraftModel(
      '{"script":"x = input.x"}'
    )

    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({
      revisionId: '6',
      lockVersion: 4,
      modelJson: '{"script":"x = input.x"}',
    }))
    expect(result.compileSuccess).toBe(true)
    expect(wrapper.vm.draftRevision.lockVersion).toBe(5)
    wrapper.unmount()
  })

  test('保存扩展字段仅透传 OpenAPI 白名单且不能覆盖草稿治理字段', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{}',
        },
      ],
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: true,
        issues: [],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    await wrapper.vm.saveDraftModel('{"script":"trusted"}', {
      definitionId: 999,
      revisionId: 999,
      lockVersion: 999,
      modelJson: '{"script":"malicious"}',
      openApiConfigJson: '{"enabled":true}',
      updateOpenApiConfig: true,
      unknownField: 'must-not-pass',
    })

    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({
      revisionId: '6',
      lockVersion: 4,
      modelJson: '{"script":"trusted"}',
      openApiConfigJson: '{"enabled":true}',
      updateOpenApiConfig: true,
    }))
    wrapper.unmount()
  })

  test('已发布修订直接编辑页面副本，只在明确暂存时原子创建并保存', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [{ id: 5, definitionId: 30, revisionNo: 1, state: 'PUBLISHED', modelJson: '{}' }] })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 1, modelJson: '{"script":"editable"}' }, compileSuccess: true, issues: [] } })
    const wrapper = mountHost()
    await flushPromises()
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(wrapper.vm.draftRevision).toBeNull()
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    const result = await wrapper.vm.saveDraftModel('{"script":"editable"}')
    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ sourceType: 'REVISION', sourceId: '5', modelJson: '{"script":"editable"}' }))
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    expect(result.compileSuccess).toBe(true)
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 6, state: 'DRAFT' })
    wrapper.unmount()
  })

  test('没有修订的规则直接加载初始内容，明确暂存才产生首个草稿', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({ data: { modelJson: '{"rules":[]}' } })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 1 } } })
    const wrapper = mountHost()
    await flushPromises()
    expect(wrapper.vm.viewRevision).toMatchObject({ state: 'LEGACY', modelJson: '{"rules":[]}' })
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    await wrapper.vm.saveDraftModel('{"rules":[{"id":"local"}]}')
    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ modelJson: '{"rules":[{"id":"local"}]}' }))
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('修订列表查询失败时不以旧内容掩盖治理服务错误', async () => {
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('revision query failed')
    )
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(definitionApi.getContent).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe('revision query failed')
    wrapper.unmount()
  })

  test('旧版生效内容不是合法 JSON 时显示加载错误而不是空白设计器', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: { definitionId: 30, modelJson: '{broken json' },
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe(
      '当前规则的历史内容不是有效 JSON'
    )
    wrapper.unmount()
  })

  test('旧版生效内容为空时显示明确错误', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getContent.mockResolvedValueOnce({
      data: { definitionId: 30, modelJson: '' },
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.draftGuardError.message).toBe(
      '当前规则没有可查看的历史内容'
    )
    wrapper.unmount()
  })

  test('编译失败拒绝保存且不更新锁', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
        },
      ],
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: {
        revision: {
          id: 6,
          definitionId: 30,
          state: 'DRAFT',
          lockVersion: 5,
        },
        compileSuccess: false,
        compileMessage: '脚本解析失败',
        issues: [{ code: 'QL_PARSE_ERROR', severity: 'ERROR' }],
      },
    })
    const wrapper = mountHost()
    await flushPromises()

    await expect(wrapper.vm.saveDraftModel('invalid ql')).rejects.toThrow('脚本解析失败')

    expect(wrapper.vm.draftRevision.lockVersion).toBe(4)
    expect(wrapper.vm.draftIssues).toEqual([])
    expect(definitionApi.compileRule).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('暂存响应无效时保留页面内容并标记未暂存', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
        },
      ],
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: { compileSuccess: true, issues: [] },
    })
    const wrapper = mountHost()
    await flushPromises()

    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '草稿保存响应无效'
    )
    expect(wrapper.vm.draftRevision.id).toBe(6)
    expect(wrapper.vm.canEditDraft).toBe(true)
    expect(wrapper.vm.designerActionState).toBe('DIRTY')
    wrapper.unmount()
  })

  test('重载失败立即清空旧草稿并以只读状态完成守卫', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        {
          id: 6,
          definitionId: 30,
          revisionNo: 2,
          state: 'DRAFT',
          lockVersion: 4,
          modelJson: '{"source":"old"}',
        },
      ],
    })
    const wrapper = mountHost()
    await wrapper.vm.draftGuardPromise
    wrapper.vm.draftIssues = [{ code: 'OLD_ISSUE' }]
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('revision query failed')
    )

    const reload = wrapper.vm.loadDraftRevision()
    const loadingState = {
      loaded: wrapper.vm.draftGuardLoaded,
      draft: wrapper.vm.draftRevision,
      view: wrapper.vm.viewRevision,
      issues: wrapper.vm.draftIssues,
    }
    const outcome = await reload.then(
      (value) => ({ resolved: true, value }),
      (error) => ({ resolved: false, error })
    )

    expect({
      loadingState,
      outcome,
      loaded: wrapper.vm.draftGuardLoaded,
      draft: wrapper.vm.draftRevision,
      view: wrapper.vm.viewRevision,
      canEdit: wrapper.vm.canEditDraft,
      errorMessage: wrapper.vm.draftGuardError?.message,
    }).toEqual({
      loadingState: {
        loaded: false,
        draft: null,
        view: null,
        issues: [],
      },
      outcome: { resolved: true, value: null },
      loaded: true,
      draft: null,
      view: null,
      canEdit: false,
      errorMessage: 'revision query failed',
    })
    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '当前规则没有可编辑内容'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('初次草稿查询失败不会留下 rejected 守卫 Promise', async () => {
    definitionApi.listRuleRevisions.mockRejectedValueOnce(
      new Error('initial query failed')
    )

    const wrapper = mountHost()
    const outcome = await wrapper.vm.draftGuardPromise.then(
      (value) => ({ resolved: true, value }),
      (error) => ({ resolved: false, error })
    )

    expect({
      outcome,
      loaded: wrapper.vm.draftGuardLoaded,
      canEdit: wrapper.vm.canEditDraft,
      errorMessage: wrapper.vm.draftGuardError?.message,
    }).toEqual({
      outcome: { resolved: true, value: null },
      loaded: true,
      canEdit: false,
      errorMessage: 'initial query failed',
    })
    await expect(wrapper.vm.saveDraftModel('{}')).rejects.toThrow(
      '当前规则没有可编辑内容'
    )
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('进入生命周期携带规则 ID 与定位参数', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    const wrapper = mountHost()
    await flushPromises()

    wrapper.vm.goRuleLifecycle()

    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({
      name: 'RuleDetail',
      params: { id: 30 },
      query: { focus: 'lifecycle' },
    })
    wrapper.unmount()
  })

  test('设计器从工作区缓存恢复时重新校验 DRAFT 状态', async () => {
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'DRAFT',
            lockVersion: 4,
          },
        ],
      })
      .mockResolvedValueOnce({
        data: [
          {
            id: 6,
            definitionId: 30,
            revisionNo: 2,
            state: 'REVIEW',
            lockVersion: 5,
          },
        ],
      })
    const wrapper = mountCachedHost()
    await flushPromises()
    expect(wrapper.vm.$refs.host.canEditDraft).toBe(true)

    await wrapper.setData({ active: false })
    await wrapper.setData({ active: true })
    await flushPromises()

    expect(definitionApi.listRuleRevisions).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.$refs.host.canEditDraft).toBe(true)
    expect(wrapper.vm.$refs.host.viewRevision.state).toBe('REVIEW')
    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('缓存设计器忽略其他规则的版本路由变化', async () => {
    const route = reactive({
      name: 'DecisionTable',
      path: '/designer/table/30',
      params: { id: '30' },
      query: {},
    })
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, revisionNo: 2, state: 'DRAFT' }],
    })
    const wrapper = mount({
      name: 'RouteBoundRuleDraftMixinHost',
      mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
      data() {
        return { definitionId: null }
      },
      created() {
        this.definitionId = this.$route.params.id
      },
      template: '<div />',
    }, {
      mocks: {
        $route: route,
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
    await flushPromises()
    vi.clearAllMocks()

    route.name = 'RuleSet'
    route.path = '/designer/ruleset/40'
    route.params.id = '40'
    route.query = { sourceType: 'VERSION', sourceId: '54' }
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await flushPromises()

    expect(definitionApi.listRuleRevisions).not.toHaveBeenCalled()
    expect(definitionApi.getVersionById).not.toHaveBeenCalled()
    expect(definitionApi.getRuleRevision).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('进入同一规则表达式页面时不标记设计器返回后重载', async () => {
    const route = reactive({
      name: 'DecisionTable',
      path: '/designer/table/30',
      params: { id: '30' },
      query: {},
    })
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, definitionId: 30, revisionNo: 2, state: 'DRAFT' }],
    })
    const wrapper = mount({
      name: 'ExpressionRoundTripRuleDraftMixinHost',
      mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
      data() {
        return { definitionId: null }
      },
      created() {
        this.definitionId = this.$route.params.id
      },
      template: '<div />',
    }, {
      mocks: {
        $route: route,
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
    await flushPromises()
    vi.clearAllMocks()

    route.name = 'ExpressionEditor'
    route.path = '/designer/expression/30/session-30'
    route.params = { ruleId: '30', sessionId: 'session-30' }
    ruleDraftMixin.deactivated.call(wrapper.vm)

    route.name = 'DecisionTable'
    route.path = '/designer/table/30'
    route.params = { id: '30' }
    route.query = {}
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    ruleDraftMixin.activated.call(wrapper.vm)
    await flushPromises()

    expect(wrapper.vm.draftGuardNeedsRefresh).toBe(false)
    expect(definitionApi.listRuleRevisions).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('ruleDraftMixin stable source loading', () => {
  function mountWithRoute(query) {
    const route = reactive({ params: { id: 30 }, query })
    return mount({
      name: 'RuleDraftMixinStableSourceHost',
      mixins: [ruleDraftMixin],
    methods: { requestDesignerChoice: async () => ({ action: 'save', saveMode: 'OVERWRITE' }) },
      template: '<div />',
    }, {
      mocks: {
        $route: route,
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('reads an explicit revision exactly and keeps historical view read-only', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 },
        { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' },
      ],
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"exact"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(definitionApi.getRuleRevision).toHaveBeenCalledWith(30, '4')
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"exact"}' })
    expect(wrapper.vm.draftRevision.id).toBe(6)
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('wraps a VERSION only for display and does not fall back to draft', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }],
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    expect(wrapper.vm.viewRevision).toMatchObject({
      id: 9,
      state: 'VERSION',
      revisionNo: 7,
      sourceType: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"snapshot"}',
    })
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('clears the viewed node when its exact source fails instead of falling back', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }],
    })
    definitionApi.getRuleRevision.mockRejectedValueOnce(new Error('source missing'))
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(wrapper.vm.draftRevision.id).toBe(6)
    expect(wrapper.vm.viewRevision).toBeNull()
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.draftGuardError.message).toBe('source missing')
    expect(definitionApi.getContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('retains DRAFT-first behavior when route query is absent or invalid', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 4, revisionNo: 2, state: 'PUBLISHED' },
        { id: 6, revisionNo: 3, state: 'DRAFT' },
      ],
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: 'not-an-id' })
    await flushPromises()

    expect(definitionApi.getRuleRevision).not.toHaveBeenCalled()
    expect(definitionApi.getVersionById).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision.id).toBe(6)
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('forks a stable VERSION source and replaces route with the new draft', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: {
        revision: { id: 12, revisionNo: 8, state: 'DRAFT', lockVersion: 1 },
        issues: [{ code: 'WARN' }],
      },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    await wrapper.vm.saveDraftModel('{"edited":true}')

    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({
      sourceType: 'VERSION', sourceId: '9', modelJson: '{"edited":true}',
    }))
    expect(wrapper.vm.draftRevision).toMatchObject({ id: 12, state: 'DRAFT' })
    expect(wrapper.vm.viewRevision).toMatchObject({ id: 12, state: 'DRAFT' })
    expect(wrapper.vm.draftIssues).toEqual([{ code: 'WARN' }])
    expect(wrapper.vm.$router.replace).toHaveBeenCalledWith({
      query: { sourceType: 'REVISION', sourceId: '12' },
    })
    wrapper.unmount()
  })

  test('历史版本编辑时不切走或覆盖已有暂存草稿', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 8, state: 'DRAFT', lockVersion: 2 }],
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    definitionApi.saveDesignerDraft.mockResolvedValueOnce({ data: { revision: { id: 10, state: 'DRAFT', lockVersion: 1 }, compileSuccess: true } })
    await wrapper.vm.saveDraftModel('{}')

    expect(definitionApi.createDraftFromSource).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision.id).toBe(10)
    expect(wrapper.vm.$router.replace).toHaveBeenCalled()
    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({ saveMode: 'NEW', sourceType: 'VERSION', sourceId: '9' }))
    wrapper.unmount()
  })

  test('设计器同时提供生命周期修订和发布版本并可按稳定 ID 切换', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [
        { id: 6, revisionNo: 8, state: 'DRAFT', lockVersion: 2 },
        { id: 5, revisionNo: 7, state: 'PUBLISHED' },
      ],
    })
    definitionApi.listPublishedVersions.mockResolvedValueOnce({
      data: [{ id: 9, version: 7 }],
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    expect(wrapper.vm.designerSourceOptions).toEqual(expect.arrayContaining([
      {
        value: 'REVISION:6',
        label: '草稿 · 8',
        id: '6', state: 'DRAFT', lockVersion: 2, sourceLabel: '',
        group: 'REVISION',
      },
      {
        value: 'REVISION:5',
        label: '已发布 · 7',
        id: '5', state: 'PUBLISHED', lockVersion: undefined, sourceLabel: '',
        group: 'REVISION',
      },
      {
        value: 'VERSION:9',
        label: '发布版本 v7',
        group: 'VERSION',
      },
    ]))
    expect(wrapper.vm.designerSourceOptions[0].group).toBe('VERSION')
    expect(wrapper.vm.selectedDesignerSource).toBe('VERSION:9')

    wrapper.vm.switchDesignerSource('REVISION:6')

    expect(wrapper.vm.$router.replace).toHaveBeenCalledWith({
      query: { sourceType: 'REVISION', sourceId: '6' },
    })
    wrapper.unmount()
  })

  test('默认入口等待版本列表确认最新版本，不提前打开草稿', async () => {
    let resolveVersions
    definitionApi.listRuleRevisions.mockResolvedValueOnce({
      data: [{ id: 6, revisionNo: 8, state: 'DRAFT', lockVersion: 2 }],
    })
    definitionApi.listPublishedVersions.mockReturnValueOnce(new Promise((resolve) => {
      resolveVersions = resolve
    }))

    const wrapper = mountWithRoute({})
    await flushPromises()

    expect(wrapper.vm.draftGuardLoaded).toBe(false)
    expect(wrapper.vm.canEditDraft).toBe(false)
    expect(wrapper.vm.designerSourcesLoading).toBe(true)

    definitionApi.getVersionById.mockResolvedValueOnce({ data: { id: 9, version: 7, modelJson: '{}' } })
    resolveVersions({ data: [{ id: 9, version: 7 }] })
    await flushPromises()

    expect(wrapper.vm.designerSourcesLoading).toBe(false)
    expect(wrapper.vm.designerSourceOptions).toContainEqual({
      value: 'VERSION:9',
      label: '发布版本 v7',
      group: 'VERSION',
    })
    wrapper.unmount()
  })

  test('preserves the current node when fork conflicts and never saves its model', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    definitionApi.saveDesignerDraft.mockRejectedValueOnce(
      Object.assign(new Error('conflict'), { response: { status: 409 } })
    )
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()
    const viewed = wrapper.vm.viewRevision

    await expect(wrapper.vm.saveDraftModel('{"edited":true}')).rejects.toThrow('conflict')

    expect(wrapper.vm.viewRevision).toBe(viewed)
    expect(wrapper.vm.designerActionState).toBe('SAVE_CONFLICT')
    expect(definitionApi.saveContent).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('does not allow REVIEW nodes to fork', async () => {
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'REVIEW' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    expect(wrapper.vm.canForkViewRevision).toBe(false)
    expect(wrapper.vm.canEditDraft).toBe(true)
    await wrapper.vm.forkViewRevision()
    expect(definitionApi.saveDesignerDraft).not.toHaveBeenCalled()
    expect(definitionApi.createDraftFromSource).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('routes query changes through the shared viewed-revision refresh entry', async () => {
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }] })
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT' }] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED' },
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"snapshot"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    wrapper.vm.$route.query.sourceType = 'VERSION'
    wrapper.vm.$route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    expect(wrapper.vm.viewRevision).toMatchObject({ state: 'VERSION', sourceId: '9' })
    wrapper.unmount()
  })

  test('ignores an older source response after a newer source refresh wins', async () => {
    let resolveOldRevision
    const oldRevision = new Promise((resolve) => {
      resolveOldRevision = resolve
    })
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockReturnValueOnce(oldRevision)
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId: '4' })
    await flushPromises()

    wrapper.vm.$route.query.sourceType = 'VERSION'
    wrapper.vm.$route.query.sourceId = '9'
    const current = wrapper.vm.refreshViewedRevision()
    await current
    resolveOldRevision({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
    await flushPromises()

    expect(wrapper.vm.viewRevision).toMatchObject({
      state: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"new"}',
    })
    wrapper.unmount()
  })

  test('默认修订加载和切换历史节点都不会隐式创建草稿', async () => {
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({
        data: [{ id: 5, revisionNo: 2, state: 'PUBLISHED' }],
      })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: {
        id: 4,
        revisionNo: 1,
        state: 'PUBLISHED',
        modelJson: '{"source":"historical"}',
      },
    })
    const wrapper = mountWithRoute({})
    await flushPromises()

    wrapper.vm.$route.query.sourceType = 'REVISION'
    wrapper.vm.$route.query.sourceId = '4'
    await wrapper.vm.refreshViewedRevision()

    expect(definitionApi.createDraftRevision).not.toHaveBeenCalled()
    expect(definitionApi.createDraftFromSource).not.toHaveBeenCalled()
    expect(wrapper.vm.viewRevision).toMatchObject({
      id: 4,
      state: 'PUBLISHED',
      modelJson: '{"source":"historical"}',
    })
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('preserves a large exact source ID without Number coercion', async () => {
    const sourceId = '9007199254740993'
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: sourceId, version: 7, modelJson: '{"source":"large"}' },
    })
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, sourceId)
    expect(wrapper.vm.viewRevision.sourceId).toBe(sourceId)
    wrapper.unmount()
  })

  test('forks a large REVISION source using the exact query ID instead of a rounded response ID', async () => {
    const sourceId = '9007199254740993'
    definitionApi.listRuleRevisions.mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 9007199254740992, revisionNo: 2, state: 'PUBLISHED' },
    })
    definitionApi.saveDesignerDraft.mockResolvedValueOnce({
      data: { revision: { id: 12, state: 'DRAFT', lockVersion: 1 }, issues: [] },
    })
    const wrapper = mountWithRoute({ sourceType: 'REVISION', sourceId })
    await flushPromises()

    await wrapper.vm.saveDraftModel('{"edited":true}')

    expect(definitionApi.saveDesignerDraft).toHaveBeenCalledWith('30', expect.objectContaining({
      sourceType: 'REVISION',
      sourceId, modelJson: '{"edited":true}',
    }))
    wrapper.unmount()
  })

  test('starts exact source loading before the revision list settles and still displays it if list fails', async () => {
    let rejectList
    let resolveVersion
    definitionApi.listRuleRevisions.mockReturnValueOnce(new Promise((_resolve, reject) => {
      rejectList = reject
    }))
    definitionApi.getVersionById.mockReturnValueOnce(new Promise((resolve) => {
      resolveVersion = resolve
    }))
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    expect(definitionApi.getVersionById).toHaveBeenCalledWith(30, '9')
    resolveVersion({ data: { id: 9, version: 7, modelJson: '{"source":"exact"}' } })
    rejectList(new Error('list unavailable'))
    await wrapper.vm.draftGuardPromise

    expect(wrapper.vm.viewRevision).toMatchObject({
      state: 'VERSION',
      sourceId: '9',
      modelJson: '{"source":"exact"}',
    })
    expect(wrapper.vm.draftRevision).toBeNull()
    expect(wrapper.vm.canEditDraft).toBe(true)
    wrapper.unmount()
  })

  test('query refresh reloads host content only after its guard promise resolves', async () => {
    const loadContent = vi.fn(async function () {
      await this.draftGuardPromise
    })
    const route = reactive({
      params: { id: 30 },
      query: { sourceType: 'REVISION', sourceId: '4' },
    })
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' },
    })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const wrapper = mount({
      name: 'RuleDraftMixinContentReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: { $route: route, $router: { push: vi.fn(), replace: vi.fn() } },
    })
    await flushPromises()
    expect(loadContent).not.toHaveBeenCalled()

    route.query.sourceType = 'VERSION'
    route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    await flushPromises()

    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"new"}')
    expect(loadContent).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('activated refresh reloads host content after the new exact source is ready', async () => {
    const loadContent = vi.fn()
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision
      .mockResolvedValueOnce({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
      .mockResolvedValueOnce({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"fresh"}' } })
    const wrapper = mount({
      name: 'RuleDraftMixinActivatedReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: {
        $route: reactive({ params: { id: 30 }, query: { sourceType: 'REVISION', sourceId: '4' } }),
        $router: { push: vi.fn(), replace: vi.fn() },
      },
    })
    await flushPromises()
    wrapper.vm.draftGuardNeedsRefresh = true

    ruleDraftMixin.activated.call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    await flushPromises()

    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"fresh"}')
    expect(loadContent).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  test('does not reload host content when an old source request resolves after a new source', async () => {
    let resolveOldRevision
    const loadContent = vi.fn()
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getRuleRevision.mockReturnValueOnce(new Promise((resolve) => {
      resolveOldRevision = resolve
    }))
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"new"}' },
    })
    const route = reactive({ params: { id: 30 }, query: { sourceType: 'REVISION', sourceId: '4' } })
    const wrapper = mount({
      name: 'RuleDraftMixinStaleReloadHost',
      mixins: [ruleDraftMixin],
      methods: { loadContent },
      template: '<div />',
    }, {
      mocks: { $route: route, $router: { push: vi.fn(), replace: vi.fn() } },
    })
    await flushPromises()

    route.query.sourceType = 'VERSION'
    route.query.sourceId = '9'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveOldRevision({ data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"old"}' } })
    await flushPromises()

    expect(loadContent).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.viewRevision.modelJson).toBe('{"source":"new"}')
    wrapper.unmount()
  })

  test('keeps a newer viewed source when an older fork response returns', async () => {
    let resolveFork
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
    definitionApi.getVersionById.mockResolvedValueOnce({
      data: { id: 9, version: 7, modelJson: '{"source":"version"}' },
    })
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"current"}' },
    })
    definitionApi.saveDesignerDraft.mockReturnValueOnce(new Promise((resolve) => {
      resolveFork = resolve
    }))
    const wrapper = mountWithRoute({ sourceType: 'VERSION', sourceId: '9' })
    await flushPromises()

    const fork = wrapper.vm.saveDraftModel('{"edited":true}')
    await flushPromises()
    wrapper.vm.$route.query.sourceType = 'REVISION'
    wrapper.vm.$route.query.sourceId = '4'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveFork({ data: { revision: { id: 12, state: 'DRAFT', lockVersion: 1 }, issues: [] } })
    await fork

    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"current"}' })
    expect(wrapper.vm.$router.replace).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  test('keeps a newer viewed source when an older save response returns', async () => {
    let resolveSave
    definitionApi.listRuleRevisions
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 }] })
      .mockResolvedValueOnce({ data: [{ id: 6, revisionNo: 3, state: 'DRAFT', lockVersion: 4 }] })
    definitionApi.saveDesignerDraft.mockReturnValueOnce(new Promise((resolve) => {
      resolveSave = resolve
    }))
    definitionApi.getRuleRevision.mockResolvedValueOnce({
      data: { id: 4, revisionNo: 2, state: 'PUBLISHED', modelJson: '{"source":"current"}' },
    })
    const wrapper = mountWithRoute({})
    await flushPromises()

    const save = wrapper.vm.saveDraftModel('{"source":"save"}')
    await flushPromises()
    wrapper.vm.$route.query.sourceType = 'REVISION'
    wrapper.vm.$route.query.sourceId = '4'
    ruleDraftMixin.watch['$route.query'].call(wrapper.vm)
    await wrapper.vm.draftGuardPromise
    resolveSave({ data: { revision: { id: 6, state: 'DRAFT', lockVersion: 5 }, issues: [{ code: 'OLD' }] } })
    await save

    expect(wrapper.vm.viewRevision).toMatchObject({ id: 4, modelJson: '{"source":"current"}' })
    expect(wrapper.vm.draftRevision).toMatchObject({ id: 6, lockVersion: 4 })
    expect(wrapper.vm.draftIssues).toEqual([])
    wrapper.unmount()
  })
})
