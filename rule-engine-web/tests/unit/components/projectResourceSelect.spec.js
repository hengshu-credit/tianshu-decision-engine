import { shallowMount, flushPromises } from '@vue/test-utils'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import { listProjectOptions as listProjects, getProjectOption as getProject } from '@/api/project'

describe('项目资源选择器', () => {
  let wrapper
  afterEach(() => { wrapper?.unmount(); vi.clearAllMocks() })
  it('按搜索词分页查询，1001 号项目保留 ID 而非名称', async () => {
    listProjects.mockResolvedValue({ data: { records: [{ id: '1001', projectName: '同名项目', projectCode: 'Keep_Code' }], total: 1001 } })
    wrapper = shallowMount(ProjectResourceSelect)
    const response = await wrapper.vm.fetchProjects({ query: 'Keep_Code', pageNum: 51, pageSize: 20 })
    expect(listProjects).toHaveBeenCalledWith({ keyword: 'Keep_Code', pageNum: 51, pageSize: 20, status: 1 })
    expect(response.data.records[0]).toMatchObject({ id: '1001', displayName: '同名项目 · Keep_Code' })
    expect(response.data.total).toBe(1001)
  })
  it('不在已加载候选中的项目按 ID 回显，迟到结果不覆盖新值', async () => {
    let resolveFirst
    getProject.mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve }))
    wrapper = shallowMount(ProjectResourceSelect, { props: { modelValue: '1001' } })
    getProject.mockResolvedValueOnce({ data: { id: '1002', projectName: '新项目' } })
    await wrapper.setProps({ modelValue: '1002' })
    await flushPromises()
    resolveFirst({ data: { id: '1001', projectName: '旧项目' } })
    await flushPromises()
    expect(wrapper.vm.selectedProject.id).toBe('1002')
  })
})
