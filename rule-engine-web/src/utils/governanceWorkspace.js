/** Keep saving a workspace separate from submitting it for production approval. */
export function notifyDraftChange() {
  window.dispatchEvent(new CustomEvent('tianshu:resource-draft-change'))
}

export function isNewContentRequest(request) {
  if (!request) return false
  if (request.action === 'CREATE') return true
  if (request.resourceType !== 'RULE') return false
  try {
    const snapshot = JSON.parse(request.submittedSnapshotJson || request.draftSnapshotJson || '{}')
    return Number(snapshot.status) === 0 && !(Number(snapshot.publishedVersion) > 0)
  } catch { return false }
}

export async function completeResourceChange(view, response, reload) {
  notifyDraftChange()
  const result = response?.data || {}
  if (result.deleted) {
    view.$message.success('已删除未发布草稿，无需审批')
  } else if (result.action === 'DELETE') {
    view.$message.success('审批草稿已创建，审批通过后生效')
    if (result.id) await view.$router.push('/approval/' + result.id)
  } else {
    view.$message.success('草稿已保存，可继续编辑；点击行内“发布”或“草稿与审批”提交审批')
  }
  if (reload) await reload()
}
