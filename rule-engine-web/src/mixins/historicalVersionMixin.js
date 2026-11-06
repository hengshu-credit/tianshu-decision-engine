// The legacy version dialogs share selection state. A later preview wins over an earlier compare request.
export const historicalVersionMethods = {
  viewVersion(row) {
    this.versionReadId++
    this.versionPreview = row
    this.versionCompare = null
  },
  async compareHistoricalVersions(read) {
    this.viewVersion(null)
    const requestId = this.versionReadId
    try {
      const response = await read()
      if (this.versionReadId !== requestId) return
      const comparison = response?.data || response
      if (!comparison?.left || !comparison?.right) throw new Error('版本对比结果不完整，请刷新后重试')
      this.versionCompare = comparison
    } catch (error) {
      if (this.versionReadId === requestId) this.$message.error(error.message || '版本对比失败')
    }
  },
}
