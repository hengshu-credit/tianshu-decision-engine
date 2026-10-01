import DecisionTable from '@/views/designer/DecisionTable.vue'
import RuleSet from '@/views/designer/RuleSet.vue'
import CrossTable from '@/views/designer/CrossTable.vue'
import Scorecard from '@/views/designer/Scorecard.vue'
import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'
import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'
import ScriptEditor from '@/views/designer/ScriptEditor.vue'

test.each([DecisionTable, RuleSet, CrossTable, Scorecard, AdvancedCrossTable, AdvancedScorecard])('$name 切换空白来源不保留上个版本内容', async view => {
  const context = { viewRevision: { modelJson: '{}' }, model: { stale: 'deleted content' }, cellData: [['deleted']],
    normalizeModel: vi.fn(), _trySyncModelVarRefs: vi.fn(), _syncModelVarRefs: vi.fn(), syncCellData: vi.fn(),
    $nextTick: vi.fn(), $message: { error: vi.fn() } }
  await view.methods.loadContent.call(context)
  expect(context.$message.error).not.toHaveBeenCalled()
  expect(context.model).not.toHaveProperty('stale')
  if (view === AdvancedCrossTable) expect(context.cellData).toEqual([])
})

test.each(['{}', '{"script":""}'])('脚本切换到 %s 清空脚本与旧引用', async modelJson => {
  const context = { viewRevision: { modelJson }, script: 'return deleted;', scriptVarRefs: [{ varId: 1 }],
    $nextTick: vi.fn(), $message: { error: vi.fn() } }
  await ScriptEditor.methods.loadContent.call(context)
  expect(context.script).toBe('')
  expect(context.scriptVarRefs).toEqual([])
})
