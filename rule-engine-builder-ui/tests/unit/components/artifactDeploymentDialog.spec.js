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
})
