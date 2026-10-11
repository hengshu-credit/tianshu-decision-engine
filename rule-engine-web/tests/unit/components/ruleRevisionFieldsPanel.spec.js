import { mount, flushPromises } from '@test-utils'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import * as api from '@/api/definition'
import * as variables from '@/api/variable'
import RuleRevisionFieldsPanel from '@/components/rule/RuleRevisionFieldsPanel.vue'

const revisionId = '9007199254740993'
const revisions = [{ id: revisionId, revisionNo: 2, state: 'PUBLISHED', lockVersion: 3 }]
const view = (overrides = {}) => ({
  definitionId: '1', revisionId, revisionNo: 2, revisionState: 'PUBLISHED', readOnly: true,
  status: 'AVAILABLE', source: 'FROZEN_ARTIFACT', referenceNamespace: 'ARTIFACT_SOURCE', validationSource: 'FROZEN_REFERENCE_IDS',
  artifactId: '32', artifactDigest: 'frozen-digest', diagnostics: [], localOutputNames: [],
  inputFields: [{ id: '7', varId: '9007199254740995', refType: 'VARIABLE', fieldLabel: '冻结金额', scriptName: 'Amount_Original', fieldType: 'NUMBER', defaultValue: 0, validationRuleIds: '[9007199254740997]' }],
  outputFields: [{ id: null, varId: null, fieldName: 'localScore', scriptName: 'localScore', fieldLabel: '本地分数', fieldType: 'NUMBER' }],
  ...overrides,
})
const props = { definitionId: 1, modelValue: revisionId, revisions }
async function render(extra = {}) {
  const wrapper = mount(RuleRevisionFieldsPanel, { props: { ...props, ...extra }, stubs: { 'el-table': ElTable, 'el-table-column': ElTableColumn, 'el-alert': ElAlert } })
  await flushPromises()
  return wrapper
}

describe('修订字段只读视图', () => {
  beforeEach(() => { vi.clearAllMocks(); api.getRuleRevisionFieldView.mockReset(); api.getRuleRevisionFieldView.mockResolvedValue({ data: view() }) })

  test('真实表格保留冻结字段原名、大ID、零默认值和校验原文，不查询当前目录', async () => {
    const snapshot = view(), original = JSON.stringify(snapshot)
    api.getRuleRevisionFieldView.mockResolvedValue({ data: snapshot })
    const wrapper = await render()
    try {
      expect(api.getRuleRevisionFieldView).toHaveBeenCalledWith(1, revisionId)
      const rows = wrapper.findAll('.el-table__body tbody tr')
      expect(rows).toHaveLength(1)
      expect(rows[0].text()).toContain('冻结金额')
      expect(rows[0].text()).toContain('Amount_Original')
      expect(rows[0].text()).toContain('9007199254740995')
      expect(rows[0].text()).toContain('[9007199254740997]')
      expect(rows[0].findAll('td')[4].text()).toBe('0')
      expect(wrapper.text()).toContain('制品冻结字段')
      expect(wrapper.text()).toContain('不链接当前环境中的同号资源')
      expect(variables.listVariablesByProject).not.toHaveBeenCalled()
      expect(JSON.stringify(snapshot)).toBe(original)
    } finally { wrapper.unmount() }
  })

  test.each(['UNAVAILABLE', 'INVALID'])('%s明确显示诊断，不伪装为合法零字段', async status => {
    api.getRuleRevisionFieldView.mockResolvedValue({ data: view({ status, inputFields: [], diagnostics: [{ message: '制品不存在或摘要不一致', path: '$.artifact' }] }) })
    const wrapper = await render()
    try {
      expect(wrapper.text()).toContain('制品不存在或摘要不一致')
      expect(wrapper.find('[data-testid="revision-fields-table"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="revision-fields-empty"]').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('合法空输入与脚本本地输出分别展示，不要求本地字段伪造varId', async () => {
    api.getRuleRevisionFieldView.mockResolvedValue({ data: view({ inputFields: [], localOutputNames: ['localScore'] }) })
    const wrapper = await render()
    try {
      expect(wrapper.text()).toContain('已确认没有输入字段')
      await wrapper.setProps({ direction: 'output' })
      await flushPromises()
      expect(wrapper.find('.el-table__body tbody tr').text()).toContain('本地输出字段')
      expect(wrapper.text()).toContain('localScore')
    } finally { wrapper.unmount() }
  })

  test('切换修订时清掉旧数据，迟到响应不能覆盖新修订', async () => {
    let resolveOld
    api.getRuleRevisionFieldView.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    const wrapper = await render()
    const nextId = '9007199254740994'
    try {
      api.getRuleRevisionFieldView.mockResolvedValueOnce({ data: view({ revisionId: nextId, source: 'DRAFT_ANALYSIS', inputFields: [{ fieldLabel: '另一份草稿字段', scriptName: 'newField' }] }) })
      await wrapper.setProps({ modelValue: nextId, revisions: [...revisions, { id: nextId, state: 'DRAFT', revisionNo: 3, lockVersion: 0 }] })
      await flushPromises()
      resolveOld({ data: view() }); await flushPromises()
      expect(wrapper.text()).toContain('另一份草稿字段')
      expect(wrapper.text()).not.toContain('冻结金额')
      expect(api.getRuleRevisionFieldView).toHaveBeenLastCalledWith(1, nextId)
    } finally { wrapper.unmount() }
  })

  test('错误归属响应被拒绝，刷新可重试；卸载后响应不回写', async () => {
    api.getRuleRevisionFieldView.mockResolvedValueOnce({ data: view({ definitionId: '2' }) })
    const wrapper = await render()
    expect(wrapper.get('[data-testid="revision-fields-error"]').text()).toContain('与所选修订不一致')
    expect(wrapper.find('[data-testid="revision-fields-table"]').exists()).toBe(false)
    await wrapper.vm.load(); await flushPromises()
    expect(wrapper.text()).toContain('冻结金额')
    let resolveLate
    api.getRuleRevisionFieldView.mockReturnValueOnce(new Promise(resolve => { resolveLate = resolve }))
    const request = wrapper.vm.load(), vm = wrapper.vm
    wrapper.unmount(); resolveLate({ data: view() }); await request
    expect(vm.view).toBeNull()
  })
})
