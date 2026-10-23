import { loadPagedCatalog } from '@/utils/pagedCatalog'

test('完整加载 5001 个引用，保留原始 ID 和编码', async () => {
  const rows = Array.from({ length: 5001 }, (_, i) => ({ id: String(9007199254740993n + BigInt(i)), varCode: `Keep_Code_${i}` }))
  const loader = vi.fn(async ({ pageNum, pageSize }) => ({ data: { records: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: rows.length } }))
  const response = await loadPagedCatalog(loader, { scope: 'GLOBAL' })
  expect(response.data.records).toEqual(rows)
  expect(loader).toHaveBeenCalledTimes(26)
  expect(loader).toHaveBeenLastCalledWith({ pageNum: 26, pageSize: 200, scope: 'GLOBAL' })
})

test('后续页失败不返回部分目录，服务端重复页不会无限请求', async () => {
  const repeated = vi.fn(async () => ({ data: { records: [{ id: 1 }], total: 2 } }))
  await expect(loadPagedCatalog(repeated)).rejects.toThrow('未返回新数据')
  expect(repeated).toHaveBeenCalledTimes(2)
  const failed = vi.fn().mockResolvedValueOnce({ data: { records: [{ id: 1 }], total: 2 } }).mockRejectedValueOnce(new Error('断网'))
  await expect(loadPagedCatalog(failed)).rejects.toThrow('断网')
})
