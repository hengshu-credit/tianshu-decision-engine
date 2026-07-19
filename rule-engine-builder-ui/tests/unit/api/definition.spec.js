jest.unmock('@/api/definition')

import request from '@/api/request'
import {
  executeRule,
  migrateReferences,
  refreshFields,
  scanAllReferenceIntegrity,
  scanReferenceIntegrity
} from '@/api/definition'

describe('definition API', () => {
  beforeEach(() => {
    jest.clearAllMocks()
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
})
