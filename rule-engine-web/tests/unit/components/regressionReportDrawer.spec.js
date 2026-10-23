import { shallowMount } from '@vue/test-utils'
import * as api from '@/api/definition'
import RegressionReportDrawer from '@/components/rule/RegressionReportDrawer.vue'

const flush = () => new Promise(resolve => setTimeout(resolve, 0))
function mountReport(props = {}) {
  return shallowMount(RegressionReportDrawer, {
    props: { definitionId: '7', revisionId: '20', ...props },
    global: { directives: { permission: {}, loading: {} } },
  })
}

describe('回归报告', () => {
  afterEach(() => vi.clearAllMocks())

  test('历史通过不自动显示当前有效，复核后准确标记过期', async () => {
    const row = { id: '9007199254740993', eligible: 1, state: 'COMPLETED', validationStatus: 'PASSED' }
    api.listRuleRegressionReports.mockResolvedValue({ data: { records: [row], total: 1 } })
    api.checkRuleRegressionReports.mockResolvedValue({ data: { allowed: false, cases: [{ reportId: row.id, status: 'STALE' }], staleCount: 1 } })
    api.getRuleRegressionReport.mockResolvedValue({ data: { ...row, validationJson: '{"status":"PASSED"}' } })
    const wrapper = mountReport()
    try {
      await flush()
      expect(wrapper.vm.evidenceLabel(row)).toBe('历史报告，待复核')
      await wrapper.vm.check()
      expect(api.checkRuleRegressionReports).toHaveBeenCalledWith('7', '20')
      expect(wrapper.vm.evidenceLabel(row)).toBe('已过期')
      await wrapper.vm.openDetail(row)
      expect(api.getRuleRegressionReport).toHaveBeenCalledWith('7', row.id)
      expect(wrapper.vm.validation.status).toBe('PASSED')
    } finally { wrapper.unmount() }
  })

  test('切换规则时旧历史响应不能覆盖新规则', async () => {
    let finishOld
    api.listRuleRegressionReports.mockImplementation(id => id === '7'
      ? new Promise(resolve => { finishOld = resolve })
      : Promise.resolve({ data: { records: [{ id: 'new' }], total: 1 } }))
    const wrapper = mountReport()
    try {
      await wrapper.setProps({ definitionId: '8' }); await flush()
      finishOld({ data: { records: [{ id: 'old' }], total: 99 } }); await flush()
      expect(wrapper.vm.rows).toEqual([{ id: 'new' }]); expect(wrapper.vm.total).toBe(1)
      expect(wrapper.vm.loading).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('旧修订复核失败不覆盖新修订结果，诊断报告不能充当发布证据', async () => {
    api.listRuleRegressionReports.mockResolvedValue({ data: { records: [], total: 0 } })
    let rejectOld
    api.checkRuleRegressionReports.mockReturnValueOnce(new Promise((_resolve, reject) => { rejectOld = reject }))
      .mockResolvedValueOnce({ data: { allowed: true, requiredCount: 1, cases: [] } })
    const wrapper = mountReport()
    try {
      await flush(); const pending = wrapper.vm.check()
      await wrapper.setProps({ revisionId: '21' }); await wrapper.vm.check()
      rejectOld(new Error('旧请求失败')); await pending
      expect(wrapper.vm.gate.allowed).toBe(true); expect(wrapper.vm.checkError).toBe('')
      expect(wrapper.vm.evidenceLabel({ eligible: 0, state: 'COMPLETED', validationStatus: 'PASSED' })).toContain('不作为发布证据')
    } finally { wrapper.unmount() }
  })
})
