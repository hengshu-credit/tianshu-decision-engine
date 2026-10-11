import { flushPromises, shallowMount } from '@test-utils'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import * as projectApi from '@/api/project'

vi.mock('@/api/project', () => ({
  getProject: vi.fn(),
  listProjects: vi.fn(),
}))

test('项目选择器按服务端分页搜索，并按ID回填当前项目', async () => {
  projectApi.getProject.mockResolvedValue({ data: { id: 99, projectName: '第999个项目', projectCode: 'P999' } })
  projectApi.listProjects.mockResolvedValue({ data: { records: [], total: 0 } })
  const wrapper = shallowMount(ProjectResourceSelect, {
    props: { value: 99 },
    global: { stubs: { RemoteFilterSelect: true } },
  })
  await flushPromises()

  expect(wrapper.vm.selectedProject.projectName).toBe('第999个项目')
  const page = await wrapper.vm.fetchOptions({ query: '999', pageNum: 3, pageSize: 20 })
  expect(projectApi.listProjects).toHaveBeenCalledWith({ pageNum: 3, pageSize: 20, status: 1, projectName: '999' })
  expect(page.data.total).toBe(0)
  wrapper.unmount()
})
