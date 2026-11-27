import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  checkRuleRegressionReports, executeApiScenarioValidated, getRuleRevision, listApiScenarios, submitRuleRevision,
} from '@/api/definition'
import { approveGovernanceRequest, getGovernanceRequest } from '@/api/governance'
import { restorePublishedStep } from '@/utils/restoreProjectSet'

vi.mock('@/api/definition', () => ({
  checkRuleRegressionReports: vi.fn(), executeApiScenarioValidated: vi.fn(), getRuleRevision: vi.fn(),
  listApiScenarios: vi.fn(), submitRuleRevision: vi.fn(), publishRuleRevision: vi.fn(),
}))
vi.mock('@/api/governance', () => ({
  approveGovernanceRequest: vi.fn(), getGovernanceRequest: vi.fn(), submitGovernanceRequest: vi.fn(),
}))

const step = { type: 'RULE', targetId: '12', actionId: '20', digest: 'digest', lockVersion: 3 }
const draft = { state: 'DRAFT', contentDigest: 'digest', lockVersion: 3 }
const cases = [{ id: 45, status: 1, scenarioName: '正常进件' }, { id: 46, status: 0, scenarioName: '手工联调' }]

describe('项目合集恢复源发布状态', () => {
  beforeEach(() => { vi.resetAllMocks() })
  it('仅对绑定修订执行所有启用用例，回归门禁通过后提交和审批', async () => {
    getRuleRevision.mockResolvedValueOnce({ data: draft })
      .mockResolvedValueOnce({ data: { state: 'REVIEW', governanceRequestId: '31' } })
      .mockResolvedValueOnce({ data: { state: 'PUBLISHED' } })
    listApiScenarios.mockResolvedValue({ data: cases })
    executeApiScenarioValidated.mockResolvedValue({ data: { execution: { success: true }, validation: { status: 'PASSED' } } })
    checkRuleRegressionReports.mockResolvedValue({ data: { allowed: true } })
    getGovernanceRequest.mockResolvedValue({ data: { request: { resourceType: 'RULE', resourceId: 12, status: 'PENDING' } } })
    approveGovernanceRequest.mockResolvedValue({ data: { status: 'APPROVED' } })
    await restorePublishedStep(step, vi.fn())
    expect(executeApiScenarioValidated).toHaveBeenCalledTimes(1)
    expect(executeApiScenarioValidated).toHaveBeenCalledWith('12', {
      scenarioId: 45, executionSource: { sourceType: 'REVISION', sourceId: '20', lockVersion: '3' },
    }, 1800000)
    expect(submitRuleRevision).toHaveBeenCalledWith('12', '20', expect.objectContaining({ expectedContentDigest: 'digest', expectedLockVersion: 3 }))
    expect(approveGovernanceRequest).toHaveBeenCalledWith('31', expect.any(String))
  })
  it('失败的用例和导入后被编辑的修订都不能触发提交', async () => {
    getRuleRevision.mockResolvedValueOnce({ data: { ...draft, lockVersion: 4 } }).mockResolvedValueOnce({ data: draft })
    await expect(restorePublishedStep(step, vi.fn())).rejects.toThrow('草稿已被编辑')
    expect(listApiScenarios).not.toHaveBeenCalled()
    listApiScenarios.mockResolvedValue({ data: cases })
    executeApiScenarioValidated.mockResolvedValue({ data: { execution: { success: true }, validation: { status: 'FAILED' } } })
    await expect(restorePublishedStep(step, vi.fn())).rejects.toThrow('正常进件')
    expect(submitRuleRevision).not.toHaveBeenCalled()
    expect(approveGovernanceRequest).not.toHaveBeenCalled()
  })
})
