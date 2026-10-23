// 引用解析需要完整目录；仅用于构建元信息映射，不用于可远程搜索的下拉框。
export async function loadPagedCatalog(loader, params = {}) {
  const records = []
  const ids = new Set()
  const pageSize = 200
  let firstResponse
  for (let pageNum = 1; ; pageNum++) {
    const response = await loader({ ...params, pageNum, pageSize })
    if (!firstResponse) firstResponse = response
    const data = response?.data
    if (Array.isArray(data)) return response
    if (!Array.isArray(data?.records)) throw new Error('引用目录响应不完整，请重新加载')
    const before = records.length
    for (const record of data.records) {
      if (record.id == null) throw new Error('引用目录缺少资源 ID')
      const key = String(record.id)
      if (!ids.has(key)) { ids.add(key); records.push(record) }
    }
    const total = data.total == null ? null : Number(data.total)
    if ((total != null && records.length >= total) || (total == null && data.records.length < pageSize)) break
    if (records.length === before) throw new Error('引用目录分页未返回新数据，请重新加载')
  }
  return { ...firstResponse, data: { ...firstResponse.data, records, total: records.length } }
}
