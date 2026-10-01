import {
  clearDraftRecovery,
  createDraftFingerprint,
  readDraftRecovery,
  saveDraftRecovery,
} from '@/utils/ruleDesignerDraft'

describe('ruleDesignerDraft', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  test('对象字段顺序不同但内容相同时生成相同指纹', () => {
    expect(createDraftFingerprint('{"rules":[],"hitPolicy":"FIRST"}')).toBe(
      createDraftFingerprint('{"hitPolicy":"FIRST","rules":[]}')
    )
  })

  test('按规则和修订隔离恢复草稿并校验乐观锁版本', () => {
    saveDraftRecovery(window.sessionStorage, {
      definitionId: 30,
      revisionId: 6,
      lockVersion: 4,
      modelJson: '{"script":"result = 1"}',
      savedAt: '2026-08-28T08:00:00.000Z',
    })

    expect(
      readDraftRecovery(window.sessionStorage, {
        definitionId: 30,
        revisionId: 6,
        lockVersion: 4,
      })
    ).toMatchObject({
      definitionId: '30',
      revisionId: '6',
      lockVersion: 4,
      modelJson: '{"script":"result = 1"}',
    })
    expect(
      readDraftRecovery(window.sessionStorage, {
        definitionId: 30,
        revisionId: 6,
        lockVersion: 5,
      })
    ).toBeNull()
  })

  test('保存成功后可精确清除当前修订的恢复草稿', () => {
    const identity = { definitionId: 30, revisionId: 6 }
    saveDraftRecovery(window.sessionStorage, {
      ...identity,
      lockVersion: 4,
      modelJson: '{}',
    })

    clearDraftRecovery(window.sessionStorage, identity)

    expect(
      readDraftRecovery(window.sessionStorage, {
        ...identity,
        lockVersion: 4,
      })
    ).toBeNull()
  })
})
