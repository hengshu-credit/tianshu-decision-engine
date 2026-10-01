vi.unmock('@/api/ruleList')

import request from '@/api/request'
import {
  changeLibraryStatusDraft,
  createLibraryDraft,
  deleteLibraryDraft,
  stageRecordChange,
  updateLibraryDraft
} from '@/api/ruleList'

describe('governed rule list API', () => {
  beforeEach(() => vi.clearAllMocks())

  test('library lifecycle uses unified governance drafts', async () => {
    const library = {
      id: 9,
      projectId: 7,
      scope: 'PROJECT',
      listCode: 'mobile_black',
      listName: 'Mobile blacklist'
    }

    await createLibraryDraft({ ...library, id: null })
    await updateLibraryDraft(library)
    await changeLibraryStatusDraft(library, 0)
    await deleteLibraryDraft(library)

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'LIST_LIBRARY',
        action: 'CREATE',
        projectId: 7
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'LIST_LIBRARY',
        resourceId: 9,
        action: 'UPDATE'
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'LIST_LIBRARY',
        resourceId: 9,
        action: 'DISABLE',
        snapshotJson: null
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'LIST_LIBRARY',
        resourceId: 9,
        action: 'DELETE',
        snapshotJson: null
      })
    )
  })

  test('record writes expose only the staged change batch endpoint', async () => {
    const record = { id: 3, itemContent: '13800138000' }

    await stageRecordChange(9, 'DELETE', record)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/list/9/change-batch',
      method: 'post',
      data: { operation: 'DELETE', record }
    })
  })
})
