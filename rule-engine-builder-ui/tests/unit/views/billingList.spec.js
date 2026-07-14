import BillingList from '@/views/billing/BillingList.vue'
import * as billingApi from '@/api/billing'
import { listDefinitions } from '@/api/definition'
import { listApiConfigs } from '@/api/datasource'
import { listDbDatasources } from '@/api/database'

function createContext(overrides = {}) {
  const ctx = {
    targetOptions: [],
    targetLoading: false,
    configForm: {
      scope: 'PROJECT',
      projectId: 7,
      billingTarget: 'ENGINE',
      targetRefId: 9,
      effectiveTime: '',
      expireTime: ''
    },
    ...overrides
  }
  Object.keys(BillingList.methods).forEach(name => {
    ctx[name] = BillingList.methods[name].bind(ctx)
  })
  return ctx
}

describe('BillingList target selector', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('loadTargetOptions loads rule definitions for ENGINE target', async () => {
    listDefinitions.mockResolvedValue({ data: { records: [{ id: 11, ruleName: 'Rule A', ruleCode: 'rule_a' }] } })
    const ctx = createContext()

    await ctx.loadTargetOptions()

    expect(listDefinitions).toHaveBeenCalledWith({ pageNum: 1, pageSize: 500, status: 1, projectId: 7 })
    expect(ctx.targetOptions).toEqual([{ id: 11, ruleName: 'Rule A', ruleCode: 'rule_a' }])
    expect(ctx.targetLoading).toBe(false)
  })

  test('loadTargetOptions loads API configs for API target', async () => {
    listApiConfigs.mockResolvedValue({ data: { records: [{ id: 21, apiName: 'Credit API', apiCode: 'credit_api' }] } })
    const ctx = createContext({ configForm: { scope: 'PROJECT', projectId: 7, billingTarget: 'API', targetRefId: null } })

    await ctx.loadTargetOptions()

    expect(listApiConfigs).toHaveBeenCalledWith({ pageNum: 1, pageSize: 500, status: 1 })
    expect(ctx.targetOptions[0].id).toBe(21)
  })

  test('loadTargetOptions loads DB datasources for DB target', async () => {
    listDbDatasources.mockResolvedValue({ data: { records: [{ id: 31, datasourceName: 'Risk DB', datasourceCode: 'risk_db' }] } })
    const ctx = createContext({ configForm: { scope: 'PROJECT', projectId: 7, billingTarget: 'DB', targetRefId: null } })

    await ctx.loadTargetOptions()

    expect(listDbDatasources).toHaveBeenCalledWith({ pageNum: 1, pageSize: 500, status: 1, projectId: 7 })
    expect(ctx.targetOptions[0].id).toBe(31)
  })

  test('target change clears selected reference before reloading options', () => {
    const ctx = createContext()
    ctx.loadTargetOptions = jest.fn()

    ctx.onBillingTargetChange()

    expect(ctx.configForm.targetRefId).toBeNull()
    expect(ctx.loadTargetOptions).toHaveBeenCalled()
  })

  test('normalizeConfig clears global project and blank optional fields', () => {
    const ctx = createContext()
    const data = ctx.normalizeConfig({
      scope: 'GLOBAL',
      projectId: 7,
      targetRefId: null,
      effectiveTime: '',
      expireTime: ''
    })

    expect(data.projectId).toBe(0)
    expect(data.targetRefId).toBeNull()
    expect(data.effectiveTime).toBeNull()
    expect(data.expireTime).toBeNull()
  })

  test('targetOptionLabel renders a readable name and code', () => {
    const ctx = createContext()

    expect(ctx.targetOptionLabel({ id: 1, apiName: 'Credit API', apiCode: 'credit_api' })).toBe('Credit API / credit_api')
  })

  test('billing detail keeps authentication and token filters', async () => {
    billingApi.listBillingRecords.mockResolvedValue({ data: { records: [], total: 0 } })
    const ctx = createContext({
      recordLoading: false,
      recordList: [],
      recordTotal: 0,
      recordQuery: {
        pageNum: 1, pageSize: 10, authType: 'BASIC', authCode: 'BASIC_MAIN', tokenCode: 'TOKEN_A'
      }
    })

    await ctx.loadRecords()

    expect(billingApi.listBillingRecords).toHaveBeenCalledWith(expect.objectContaining({
      authType: 'BASIC', authCode: 'BASIC_MAIN', tokenCode: 'TOKEN_A'
    }))
  })

  test('billing summary groups and filters by authentication but not token', async () => {
    billingApi.listBillingSummaries.mockResolvedValue({ data: { records: [], total: 0 } })
    const ctx = createContext({
      summaryLoading: false,
      summaryList: [],
      summaryTotal: 0,
      summaryQuery: { pageNum: 1, pageSize: 10, authType: 'API_KEY', authCode: 'PARTNER_API' }
    })

    await ctx.loadSummaries()

    expect(billingApi.listBillingSummaries).toHaveBeenCalledWith(expect.objectContaining({
      authType: 'API_KEY', authCode: 'PARTNER_API'
    }))
    expect(billingApi.listBillingSummaries.mock.calls[0][0].tokenCode).toBeUndefined()
  })
})
