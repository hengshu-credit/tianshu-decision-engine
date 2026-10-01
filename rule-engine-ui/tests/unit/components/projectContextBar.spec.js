import { shallowMount } from '@test-utils'
import * as projectApi from '@/api/project'
import ProjectContextBar from '@/components/ProjectContextBar.vue'

function mountBar() {
  const currentProject = {
    id: 9,
    projectCode: 'RISK',
    projectName: '风控项目',
    status: 1,
  }
  const store = {
    state: { currentProject, projectContextGeneration: 0 },
    commit: vi.fn((type, value) => {
      if (type === 'INVALIDATE_PROJECT_CONTEXT_REQUESTS') {
        store.state.projectContextGeneration += 1
      }
      if (type === 'SET_CURRENT_PROJECT') store.state.currentProject = value
      if (type === 'SET_CURRENT_PROJECT') {
        store.state.projectContextGeneration += 1
      }
    }),
  }
  const router = { push: vi.fn(), replace: vi.fn().mockResolvedValue() }
  const wrapper = shallowMount(ProjectContextBar, {
    mocks: {
      $route: { path: '/rule', query: { projectId: '9', tab: 'all' } },
      $router: router,
      $store: store,
      $message: { error: vi.fn() },
    },
  })
  return { wrapper, store, router }
}

describe('ProjectContextBar', () => {
  afterEach(() => vi.clearAllMocks())

  test('仅在业务模块显示当前项目', () => {
    const { wrapper } = mountBar()

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.text()).toContain('风控项目')
    expect(wrapper.text()).toContain('RISK')
  })

  test('切换项目同步全局上下文和显式 URL 参数', async () => {
    projectApi.getProject.mockResolvedValue({
      data: {
        id: 10,
        projectCode: 'LOAN',
        projectName: '信贷项目',
        status: 1,
      },
    })
    const { wrapper, store, router } = mountBar()

    await wrapper.vm.switchProject(10)

    expect(store.commit).toHaveBeenCalledWith(
      'SET_CURRENT_PROJECT',
      expect.objectContaining({ id: 10 })
    )
    expect(router.replace).toHaveBeenCalledWith({
      path: '/rule',
      query: { projectId: 10, tab: 'all' },
    })
  })

  test('退出项目范围同时清除上下文和 URL 参数', async () => {
    const { wrapper, store, router } = mountBar()

    wrapper.vm.leaveContext()

    expect(store.commit).toHaveBeenCalledWith('SET_CURRENT_PROJECT', null)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/rule',
      query: { tab: 'all' },
    })
  })

  test('项目切换器按关键词远程搜索并保留当前项目', async () => {
    projectApi.listProjects.mockResolvedValue({
      data: {
        records: [
          { id: 10, projectCode: 'LOAN', projectName: '信贷项目', status: 1 },
        ],
      },
    })
    const { wrapper } = mountBar()

    await wrapper.vm.searchProjects('信贷')

    expect(projectApi.listProjects).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 50,
      status: 1,
      keyword: '信贷',
    })
    expect(wrapper.vm.projectOptions.map((item) => item.id)).toEqual([9, 10])
  })

  test('较慢的旧搜索结果不能覆盖最后一次项目搜索', async () => {
    let resolveOld
    let resolveNew
    projectApi.listProjects.mockImplementation(({ keyword }) =>
      new Promise((resolve) => {
        if (keyword === '旧') resolveOld = resolve
        if (keyword === '新') resolveNew = resolve
      })
    )
    const { wrapper } = mountBar()

    const first = wrapper.vm.searchProjects('旧')
    const second = wrapper.vm.searchProjects('新')
    resolveNew({ data: { records: [{ id: 11, projectCode: 'NEW', projectName: '新项目' }] } })
    await second
    resolveOld({ data: { records: [{ id: 10, projectCode: 'OLD', projectName: '旧项目' }] } })
    await first

    expect(wrapper.vm.projectOptions.map((item) => item.id)).toEqual([9, 11])
  })

  test('快速切换项目时较慢的旧请求不能覆盖最后一次选择', async () => {
    let resolveTen
    let resolveEleven
    projectApi.getProject.mockImplementation((id) =>
      new Promise((resolve) => {
        if (id === 10) resolveTen = resolve
        if (id === 11) resolveEleven = resolve
      })
    )
    const { wrapper, store, router } = mountBar()

    const first = wrapper.vm.switchProject(10)
    const second = wrapper.vm.switchProject(11)
    resolveEleven({
      data: { id: 11, projectCode: 'LAST', projectName: '最后选择' },
    })
    await second
    resolveTen({
      data: { id: 10, projectCode: 'OLD', projectName: '旧请求' },
    })
    await first

    expect(store.state.currentProject.id).toBe(11)
    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/rule',
      query: { projectId: 11, tab: 'all' },
    })
  })
})
