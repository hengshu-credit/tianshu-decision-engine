import { mount } from '@test-utils'
import ArtifactDeploymentDialog from '@/components/artifact/ArtifactDeploymentDialog.vue'

describe('ArtifactDeploymentDialog', () => {
  test('所有组件必须由用户逐项填写目标数据库 ID', async () => {
    const wrapper = mount(ArtifactDeploymentDialog, {
      props: { modelValue: true, artifactId: 7, bindingComponentIds: ['binding:DB:9', 'binding:API:5'] }
    })

    expect(wrapper.vm.form.bindings).toEqual({})
    expect(wrapper.vm.ready).toBe(false)
    wrapper.vm.form.environmentCode = 'prod'
    wrapper.vm.form.targetDefinitionId = 16
    wrapper.vm.form.bindings['binding:DB:9'] = 101
    wrapper.vm.form.bindings['binding:API:5'] = 102
    await wrapper.vm.$nextTick()
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
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.ready).toBe(true)
    wrapper.vm.submit()
    const payload = wrapper.emitted('deploy')[0][0]
    expect(payload.createRule).toBe(true)
    expect(payload.targetDefinitionId).toBeUndefined()
    expect(payload.targetRuleCode).toBe('Target_Exact_Code')
  })
})
