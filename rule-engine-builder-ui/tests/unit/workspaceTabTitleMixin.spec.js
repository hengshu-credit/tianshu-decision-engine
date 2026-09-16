import { mount } from '@test-utils'
import { createStore } from 'vuex'
import workspaceTabs from '@/store/modules/workspaceTabs'
import workspaceTabTitleMixin from '@/mixins/workspaceTabTitleMixin'

test('详情异步加载、重命名和清空时同步所属页签，离开页面不误改其他标题', async() => {
  const store = createStore({ modules: { workspaceTabs } })
  const route = { fullPath: '/project/1', path: '/project/1', title: '项目详情' }
  await store.dispatch('workspaceTabs/open', route)
  const wrapper = mount({
    template: '<div />',
    mixins: [workspaceTabTitleMixin(vm => vm.project?.projectName)],
    data: () => ({ project: null }),
  }, { plugins: [store], mocks: { $route: route } })

  await store.dispatch('workspaceTabs/open', { fullPath: '/project/2', path: '/project/2', title: '项目详情' })
  await wrapper.setData({ project: { projectName: '企业授信' } })
  expect(store.state.workspaceTabs.tabs[0].detailTitle).toBe('企业授信')
  expect(store.state.workspaceTabs.tabs[1].detailTitle).toBeUndefined()
  expect(store.state.workspaceTabs.activePath).toBe('/project/2')
  await wrapper.setData({ project: { projectName: '企业授信第二版' } })
  expect(store.state.workspaceTabs.tabs[0].detailTitle).toBe('企业授信第二版')
  await wrapper.setData({ project: { projectName: '' } })
  expect(store.state.workspaceTabs.tabs[0].detailTitle).toBe('')
  wrapper.unmount()
})
