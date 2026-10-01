vi.unmock('@/api/variable')
vi.unmock('@/api/billing')

import request from '@/api/request'
import {
  changeFieldValidationStatusDraft,
  createFieldValidationDraft,
  deleteFieldValidationDraft,
  updateFieldValidationDraft
} from '@/api/variable'
import {
  changeBillingConfigStatusDraft,
  createBillingConfigDraft,
  deleteBillingConfigDraft,
  updateBillingConfigDraft
} from '@/api/billing'

describe('governed field validation and billing APIs', () => {
  beforeEach(() => vi.clearAllMocks())

  test('field validation lifecycle creates unified governance drafts', async () => {
    const rule = {
      id: 11,
      projectId: 7,
      scope: 'PROJECT',
      validationCode: 'Mobile_Check',
      validationName: '手机号校验',
      status: 1
    }

    await createFieldValidationDraft({ ...rule, id: null })
    await updateFieldValidationDraft(rule)
    await changeFieldValidationStatusDraft({ ...rule, status: 0 }, 0)
    await deleteFieldValidationDraft(rule)

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'FIELD_VALIDATION',
        action: 'CREATE',
        projectId: 7
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'FIELD_VALIDATION',
        resourceId: 11,
        action: 'UPDATE'
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'FIELD_VALIDATION',
        resourceId: 11,
        action: 'DISABLE',
        snapshotJson: expect.stringContaining('Mobile_Check')
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'FIELD_VALIDATION',
        resourceId: 11,
        action: 'DELETE',
        snapshotJson: null
      })
    )
  })

  test('billing configuration lifecycle creates unified governance drafts', async () => {
    const config = {
      id: 21,
      projectId: 7,
      scope: 'PROJECT',
      billingCode: 'Engine_Count',
      billingName: '规则计费',
      status: 1
    }

    await createBillingConfigDraft({ ...config, id: null })
    await updateBillingConfigDraft(config)
    await changeBillingConfigStatusDraft({ ...config, status: 0 }, 0)
    await deleteBillingConfigDraft(config)

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'BILLING_CONFIG',
        action: 'CREATE',
        projectId: 7
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'BILLING_CONFIG',
        resourceId: 21,
        action: 'UPDATE'
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'BILLING_CONFIG',
        resourceId: 21,
        action: 'DISABLE',
        snapshotJson: expect.stringContaining('Engine_Count')
      })
    )
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/rule/governance/drafts',
      expect.objectContaining({
        resourceType: 'BILLING_CONFIG',
        resourceId: 21,
        action: 'DELETE',
        snapshotJson: null
      })
    )
  })
})
