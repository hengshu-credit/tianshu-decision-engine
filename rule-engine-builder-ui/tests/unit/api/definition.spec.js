vi.unmock('@/api/definition')

import request from '@/api/request'
import {
  executeRule,
  compileDesignerModel,
  getDesignerCompileTask,
  approveRuleRevision,
  createDraftRevision,
  createDraftFromSource,
  createProjectBinding,
  deleteProjectBinding,
  ensureDraftRevision,
  getVersionById,
  getRuleRevisionRepairPreview,
  preflightRuleRevision,
  publishRuleRevision,
  repairRuleRevision,
  migrateReferences,
  refreshFields,
  scanAllReferenceIntegrity,
  scanReferenceIntegrity
} from '@/api/definition'

describe('definition API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('生命周期接口使用规则和修订 ID，不使用名称匹配', async () => {
    await ensureDraftRevision(16)
    await preflightRuleRevision(16, 3)
    await approveRuleRevision(16, 3, { forcePublishReason: '兼容窗口已通知' })
    await publishRuleRevision(16, 3, { comment: '发布' })

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/definition/16/revisions/draft', method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/definition/16/revisions/3/preflight', method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/rule/definition/16/revisions/3/approve', method: 'post',
      data: { forcePublishReason: '兼容窗口已通知' }
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      url: '/rule/definition/16/revisions/3/publish', method: 'post',
      data: { comment: '发布' }
    })
  })

  test('显式创建草稿携带基线修订 ID', async () => {
    await createDraftRevision(16, 3)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/16/revisions/draft',
      method: 'post',
      data: { baseRevisionId: 3 },
    })
  })

  test('历史规则修订只通过预览和显式修复接口治理', async () => {
    const repair = {
      sourceRevisionId: 3,
      previewDigest: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    }

    await getRuleRevisionRepairPreview(16)
    await repairRuleRevision(16, repair)

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/definition/16/revisions/repair-preview',
      method: 'get',
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/definition/16/revisions/repair',
      method: 'post',
      data: repair,
    })
  })

  test('规则执行允许 ONNX 长链路完成推理', async () => {
    const data = { definitionId: 16, params: { face_image: 'base64' } }

    await executeRule(data, 90000)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/execute',
      method: 'post',
      data,
      timeout: 90000
    })
  })

  test('设计器编译提交后台任务并按任务 ID 查询状态', async () => {
    const payload = { modelJson: '{"nodes":[]}', sourceType: 'VERSION', sourceId: '6' }

    await compileDesignerModel(16, payload)
    await getDesignerCompileTask(16, 'task-1')

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/definition/16/designer/compile', method: 'post', data: payload
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/definition/16/designer/compile/task-1', method: 'get'
    })
  })

  test('刷新规则字段以纯文本发送原始模型 JSON', async () => {
    const modelJson = '{"nodes":[],"edges":[]}'

    await refreshFields(16, modelJson)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/refreshFields/16',
      method: 'post',
      data: modelJson,
      headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
    })
  })

  test('引用完整性扫描与人工迁移使用独立接口', async () => {
    const migration = {
      definitionId: 16,
      patches: [{ path: '$.rules[0]', idField: '_varId', refTypeField: '_refType', refId: 9, refType: 'VARIABLE' }]
    }

    await scanReferenceIntegrity(16)
    await scanAllReferenceIntegrity()
    await migrateReferences(migration)

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/definition/reference-integrity/scan/16',
      method: 'get'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/definition/reference-integrity/scan-all',
      method: 'get'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/rule/definition/reference-integrity/migrate',
      method: 'post',
      data: migration
    })
  })

  test('项目规则关联通过独立治理资源按 ID 创建和删除', async () => {
    await createProjectBinding(30, 9)
    await deleteProjectBinding(15, 9)

    expect(request).toHaveBeenNthCalledWith(1,
      '/rule/governance/drafts',
      {
        resourceType: 'RULE_PROJECT_BINDING',
        resourceId: null,
        projectId: 9,
        action: 'CREATE',
        snapshotJson: '{"definitionId":30,"projectId":9}',
        changeSummary: '将全局规则加入项目',
      }
    )
    expect(request).toHaveBeenNthCalledWith(2,
      '/rule/governance/drafts',
      {
        resourceType: 'RULE_PROJECT_BINDING',
        resourceId: 15,
        projectId: 9,
        action: 'DELETE',
        snapshotJson: null,
        changeSummary: '将全局规则移出项目',
      }
    )
  })
})

describe('definition source API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('loads a version by immutable snapshot ID', async () => {
    await getVersionById(16, 9)

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/16/versions/9',
      method: 'get',
    })
  })

  test('creates a draft only from the supplied stable source reference', async () => {
    await createDraftFromSource(16, { sourceType: 'VERSION', sourceId: 9 })

    expect(request).toHaveBeenCalledWith({
      url: '/rule/definition/16/revisions/draft/from-source',
      method: 'post',
      data: { sourceType: 'VERSION', sourceId: 9 },
    })
  })

  test('preserves source IDs beyond Number safe precision in URL and request body', async () => {
    const sourceId = '9007199254740993'

    await getVersionById(16, sourceId)
    await createDraftFromSource(16, { sourceType: 'VERSION', sourceId })

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/rule/definition/16/versions/9007199254740993',
      method: 'get',
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/rule/definition/16/revisions/draft/from-source',
      method: 'post',
      data: { sourceType: 'VERSION', sourceId: '9007199254740993' },
    })
  })
})
