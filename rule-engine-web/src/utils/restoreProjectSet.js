import {
  checkRuleRegressionReports, executeApiScenarioValidated, getRuleRevision, listApiScenarios,
  publishRuleRevision, submitRuleRevision,
} from '@/api/definition'
import { approveGovernanceRequest, getGovernanceRequest, submitGovernanceRequest } from '@/api/governance'

const comment = '离线迁移：在目标环境回归并发布'

async function approve(id, type, owner, digest, progress) {
  progress('核对审批单和目标资源')
  let request = (await getGovernanceRequest(id)).data?.request
  if (!request || request.resourceType !== type || String(request.resourceId) !== String(owner)
      || (digest && request.snapshotDigest !== digest)) throw new Error('待发布配置在导入后已变更，请在审批管理中复核')
  if (request.status === 'EDITING') {
    progress('提交审批单')
    await submitGovernanceRequest(id, comment, request.snapshotDigest)
    request = (await getGovernanceRequest(id)).data?.request
  }
  if (request?.status === 'PENDING') {
    progress('审批并使配置生效')
    const approved = await approveGovernanceRequest(id, comment)
    if (approved.data?.status !== 'APPROVED') throw new Error('审批未生效，请查看审批意见')
  } else if (request?.status !== 'APPROVED') throw new Error('审批单已变更，请在审批管理中复核')
}

export async function restorePublishedStep(step, progress) {
  if (step.type === 'EXPERIMENT') {
    await approve(step.actionId, step.type, step.targetId, step.digest, progress)
    return
  }
  let revision = (await getRuleRevision(step.targetId, step.actionId)).data
  if (revision?.state === 'PUBLISHED') return
  if (!revision || revision.contentDigest !== step.digest) throw new Error('导入的规则修订已变更，请重新复核')
  if (revision.state === 'DRAFT') {
    if (Number(revision.lockVersion) !== step.lockVersion) throw new Error('导入的草稿已被编辑，请在规则工作台复核')
    const source = { sourceType: 'REVISION', sourceId: String(step.actionId), lockVersion: String(step.lockVersion) }
    const cases = ((await listApiScenarios(step.targetId)).data || []).filter(row => Number(row.status) === 1)
    for (const item of cases) {
      progress(`执行固定用例 · ${item.scenarioName}`)
      const response = await executeApiScenarioValidated(step.targetId, { scenarioId: item.id, executionSource: source }, 1800000)
      if (!response.data?.execution?.success || response.data?.validation?.status !== 'PASSED') {
        throw new Error(`固定用例「${item.scenarioName}」未通过：${response.data?.validation?.message || response.data?.execution?.errorMessage || '请查看规则测试记录'}`)
      }
    }
    progress('核对固定用例回归结果')
    const gate = (await checkRuleRegressionReports(step.targetId, step.actionId)).data
    if (!gate?.allowed) throw new Error(gate?.message || '当前修订的固定用例回归未通过')
    progress('提交审批')
    await submitRuleRevision(step.targetId, step.actionId, { comment, expectedLockVersion: step.lockVersion, expectedContentDigest: step.digest })
    revision = (await getRuleRevision(step.targetId, step.actionId)).data
  }
  if (revision?.state === 'REVIEW' && revision.governanceRequestId) {
    await approve(revision.governanceRequestId, 'RULE', step.targetId, null, progress)
    revision = (await getRuleRevision(step.targetId, step.actionId)).data
  }
  if (revision?.state === 'APPROVED') {
    progress('发布已审批的规则修订')
    await publishRuleRevision(step.targetId, step.actionId, { comment })
    revision = (await getRuleRevision(step.targetId, step.actionId)).data
  }
  if (revision?.state !== 'PUBLISHED') throw new Error('规则尚未发布，请在规则工作台复核')
}
