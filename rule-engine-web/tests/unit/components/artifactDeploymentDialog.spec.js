import { mount } from '@test-utils'
import ArtifactDeploymentDialog from '@/components/artifact/ArtifactDeploymentDialog.vue'
import { flushPromises } from '@vue/test-utils'
import { getArtifactDeploymentOptions } from '@/api/artifact'

vi.mock('@/api/artifact', () => ({ getArtifactDeploymentOptions: vi.fn() }))

const options = modelType => ({ data: { modelType, requirements: [] } })
const fillTarget = wrapper => {
  wrapper.vm.targetMode = 'create'
  Object.assign(wrapper.vm.form, { environmentCode: 'test', targetProjectId: 9, targetRuleCode: 'Keep_User_Code', targetRuleName: '目标规则' })
}

describe('ArtifactDeploymentDialog', () => {
  beforeEach(() => { getArtifactDeploymentOptions.mockReset(); getArtifactDeploymentOptions.mockResolvedValue(options('TABLE')) })

  test('新建目标自动使用制品模型类型并显示中文，不要求业务人员填写技术代码', async () => {
    getArtifactDeploymentOptions.mockResolvedValue(options('SCORE'))
    const wrapper = mount(ArtifactDeploymentDialog, { props: { modelValue: true, artifactId: 7 } })
    fillTarget(wrapper)
    await flushPromises()
    expect(wrapper.vm.ready).toBe(true)
    expect(wrapper.get('[data-testid="artifact-model-type"]').text()).toContain('评分卡')
    expect(wrapper.find('input[placeholder="如 TABLE、FLOW、SCRIPT"]').exists()).toBe(false)
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0].targetModelType).toBe('SCORE')
    expect(wrapper.emitted('deploy')[0][0].targetRuleCode).toBe('Keep_User_Code')
  })

  test('类型加载中不得使用旧表单技术类型提交', async () => {
    let resolve
    getArtifactDeploymentOptions.mockReturnValue(new Promise(done => { resolve = done }))
    const wrapper = mount(ArtifactDeploymentDialog, { props: { modelValue: true, artifactId: 7 } })
    fillTarget(wrapper); wrapper.vm.form.targetModelType = 'TABLE'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.ready).toBe(false)
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')).toBeUndefined()
    resolve(options('TABLE')); await flushPromises()
    expect(wrapper.vm.ready).toBe(true)
  })

  test('类型加载失败或未知时禁止提交并允许重试', async () => {
    getArtifactDeploymentOptions.mockRejectedValue(new Error('加载失败'))
    const wrapper = mount(ArtifactDeploymentDialog, { props: { modelValue: true, artifactId: 7 } })
    fillTarget(wrapper); wrapper.vm.form.targetModelType = 'TABLE'
    await flushPromises()
    expect(wrapper.vm.ready).toBe(false)
    expect(wrapper.get('[data-testid="artifact-options-error"]').attributes('title')).toContain('加载失败')
    getArtifactDeploymentOptions.mockResolvedValue(options('SCORE'))
    await wrapper.get('[data-testid="retry-artifact-options"]').trigger('click'); await flushPromises()
    expect(wrapper.vm.ready).toBe(true)
    getArtifactDeploymentOptions.mockResolvedValueOnce(options('SCORECARD'))
    await wrapper.vm.loadOptions()
    expect(wrapper.vm.ready).toBe(false)
  })

  test('切包和项目重查的迟到结果不能覆盖当前制品类型', async () => {
    const pending = []
    getArtifactDeploymentOptions.mockImplementation(() => new Promise(resolve => pending.push(resolve)))
    const wrapper = mount(ArtifactDeploymentDialog, { props: { modelValue: true, artifactId: 7 } })
    fillTarget(wrapper); await wrapper.vm.$nextTick()
    const old = pending.splice(0)
    await wrapper.setProps({ artifactId: 8 }); await wrapper.vm.$nextTick()
    pending.at(-1)(options('FLOW')); await flushPromises()
    expect(wrapper.get('[data-testid="artifact-model-type"]').text()).toContain('决策流')
    old.forEach(resolve => resolve(options('SCORE'))); await flushPromises()
    expect(wrapper.get('[data-testid="artifact-model-type"]').text()).toContain('决策流')
    wrapper.vm.form.targetProjectId = 10; await wrapper.vm.$nextTick()
    expect(wrapper.vm.ready).toBe(false)
    expect(wrapper.get('[data-testid="artifact-model-type"]').text()).not.toContain('决策流')
    pending.at(-1)(options('FLOW')); await flushPromises()
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0]).toMatchObject({ artifactId: 8, targetProjectId: 10, targetModelType: 'FLOW' })
  })
  test('所有组件必须由用户逐项填写目标数据库 ID', async () => {
    const wrapper = mount(ArtifactDeploymentDialog, {
      props: { modelValue: true, artifactId: 7, bindingComponentIds: ['binding:DB:9', 'binding:API:5'] }
    })

    expect(wrapper.vm.form.bindings).toEqual({})
    expect(wrapper.vm.ready).toBe(false)
    wrapper.vm.form.environmentCode = 'prod'
    wrapper.vm.form.targetProjectId = 9
    wrapper.vm.form.targetDefinitionId = 16
    wrapper.vm.form.bindings['binding:DB:9'] = 101
    wrapper.vm.form.bindings['binding:API:5'] = 102
    await flushPromises()
    expect(wrapper.vm.ready).toBe(true)
    wrapper.vm.submit()
    expect(wrapper.emitted('deploy')[0][0].bindings).toEqual({ 'binding:DB:9': 101, 'binding:API:5': 102 })
  })

  test('可以显式创建目标规则且不会携带目标规则 ID', async () => {
    const wrapper = mount(ArtifactDeploymentDialog, {
      props: { modelValue: true, artifactId: 7, bindingComponentIds: [] }
    })

    wrapper.vm.targetMode = 'create'
    wrapper.vm.form.environmentCode = 'uat'
    wrapper.vm.form.targetProjectId = 9
    wrapper.vm.form.targetRuleCode = 'Target_Exact_Code'
    wrapper.vm.form.targetRuleName = '目标规则'
    wrapper.vm.form.targetModelType = 'TABLE'
    await flushPromises()

    expect(wrapper.vm.ready).toBe(true)
    wrapper.vm.submit()
    const payload = wrapper.emitted('deploy')[0][0]
    expect(payload.createRule).toBe(true)
    expect(payload.targetDefinitionId).toBeUndefined()
    expect(payload.targetRuleCode).toBe('Target_Exact_Code')
  })
})
