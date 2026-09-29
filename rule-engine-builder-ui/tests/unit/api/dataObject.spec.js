vi.unmock('@/api/dataObject')

import request from '@/api/request'
import { createOrUpdateDataObject } from '@/api/dataObject'

describe('data object API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.mockImplementation(async (config) => {
      if (config && config.url === '/rule/dataobject/7') {
        return {
          data: {
            object: { id: 7, objectCode: 'RequestBody', scope: 'PROJECT', projectId: 3 },
            fields: [{ id: 70, objectId: 7, varCode: 'payload', scope: 'PROJECT', projectId: 3 }],
          },
        }
      }
      if (config && config.url === '/rule/dataobject/field/70/options') return { data: [] }
      return { data: { id: 101 } }
    })
  })

  test('切换数据对象作用域时同步更新所有字段归属', async () => {
    await createOrUpdateDataObject({ id: 7, scope: 'GLOBAL', projectId: 0 })

    const draft = request.mock.calls.find(([arg]) => arg === '/rule/governance/drafts')?.[1]
    expect(draft).toMatchObject({
      resourceType: 'DATA_OBJECT',
      resourceId: 7,
      action: 'UPDATE',
    })
    const snapshot = JSON.parse(draft.snapshotJson)
    expect(snapshot).toMatchObject({ scope: 'GLOBAL', projectId: 0 })
    expect(snapshot.fields).toEqual([
      expect.objectContaining({ id: 70, scope: 'GLOBAL', projectId: 0 }),
    ])
  })
})
