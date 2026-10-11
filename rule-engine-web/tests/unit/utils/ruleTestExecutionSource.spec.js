import { executionSourceKey, executionSourceLabel, previewTestSource, requireExecutionIdentity, revisionTestSource } from '@/utils/ruleTestExecutionSource'

describe('规则测试来源身份与业务说明', () => {
  test('未保存预览始终携带自身修订、不可变版本或明确旧工作区基线', () => {
    expect(previewTestSource({ id: '9007199254740993', sourceId: '81', state: 'DRAFT', lockVersion: 7 }, '1'))
      .toEqual({ sourceType: 'PREVIEW', baseSourceType: 'REVISION', baseSourceId: '9007199254740993' })
    expect(previewTestSource({ id: '82', sourceId: '81', state: 'VERSION' }, '1'))
      .toEqual({ sourceType: 'PREVIEW', baseSourceType: 'VERSION', baseSourceId: '81' })
    expect(previewTestSource({ state: 'LEGACY', sourceType: 'LEGACY_CONTENT' }, '1'))
      .toEqual({ sourceType: 'PREVIEW', baseSourceType: 'WORKSPACE', baseSourceId: '1' })
    expect(previewTestSource(null, '1')).toBeNull()
  })
  test('画布预览的不同基线属于不同会话，不能只比较PREVIEW类型', () => {
    expect(executionSourceKey({ sourceType: 'PREVIEW', baseSourceType: 'REVISION', baseSourceId: '81' }))
      .not.toBe(executionSourceKey({ sourceType: 'PREVIEW', baseSourceType: 'REVISION', baseSourceId: '82' }))
  })

  test('画布预览响应必须匹配已验证的原始来源', () => {
    expect(() => requireExecutionIdentity({ definitionId: '1', sourceType: 'PREVIEW', baseSourceType: 'VERSION', baseSourceId: '82' }, '1',
      { sourceType: 'PREVIEW', baseSourceType: 'VERSION', baseSourceId: '81' })).toThrow('不一致')
  })
  test('草稿使用自身ID，不能把来源修订sourceId当成要执行的草稿', () => {
    expect(revisionTestSource({ id: '9007199254740993', revisionNo: 3, state: 'DRAFT', lockVersion: 7,
      sourceType: 'REVISION', sourceId: '8' })).toEqual({ sourceType: 'REVISION', sourceId: '9007199254740993', lockVersion: 7 })
  })

  test('发布快照保留不可变来源ID，展示只使用业务版本号', () => {
    const source = revisionTestSource({ id: '9007199254740995', sourceId: '9007199254740993', revisionNo: 1, state: 'VERSION' })
    expect(source).toEqual({ sourceType: 'VERSION', sourceId: '9007199254740993' })
    expect(executionSourceLabel(source, '发布版本 v1 · 第 2 次发布')).toBe('发布版本 v1 · 第 2 次发布（按冻结制品执行）')
    expect(executionSourceLabel(source, { versionNo: 1 })).toBe('发布版本 v1（按冻结制品执行）')
  })

  test('大ID和锁版本不冒充业务修订号，相邻ID结果仍被拒绝', () => {
    const source = { sourceType: 'REVISION', sourceId: '9007199254740993', lockVersion: 7 }
    const label = executionSourceLabel(source, '草稿 3 · 未发布')
    expect(label).toBe('草稿 3 · 未发布（按已保存配置和当前依赖执行）')
    expect(label).not.toContain('9007199254740993')
    expect(label).not.toContain('锁版本')
    expect(() => requireExecutionIdentity({ definitionId: '1', sourceType: 'REVISION', sourceId: '9007199254740992', lockVersion: 7 }, '1', source)).toThrow('不一致')
  })
})
