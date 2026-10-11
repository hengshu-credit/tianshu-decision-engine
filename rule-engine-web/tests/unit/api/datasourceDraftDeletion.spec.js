const http = vi.hoisted(() => Object.assign(vi.fn(), { get: vi.fn(), post: vi.fn() }))
vi.unmock('@/api/datasource')
vi.mock('@/api/request', () => ({ default: http }))
import { deleteApiConfig } from '@/api/datasource'
import { metadataResponse } from '@/utils/metadataResponse'

test('删除API草稿只依赖自身ID，上游读取失败不应阻止删除', async () => {
  http.get.mockRejectedValue(new Error('上游配置已删除或不可读取'))
  http.mockRejectedValue(new Error('上游配置已删除或不可读取'))
  http.post.mockResolvedValue({ data: { deleted: true, approvalRequestId: null } })
  const result = await deleteApiConfig('9007199254740993')
  expect(http.post).toHaveBeenCalledWith('/rule/governance/resources/EXTERNAL_API/9007199254740993/delete', undefined, metadataResponse)
  expect(http.get).not.toHaveBeenCalled()
  expect(http).not.toHaveBeenCalled()
  expect(result.data.deleted).toBe(true)
})
