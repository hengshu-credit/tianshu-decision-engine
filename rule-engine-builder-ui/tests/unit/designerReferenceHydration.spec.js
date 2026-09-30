import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import varPickerMixin from '@/mixins/varPickerMixin'

function host() {
  const vm = {
    ...ruleDraftMixin.data(),
    canEditDraft: true,
    contentLoaded: true,
    projectRefs: [{ refType: 'VARIABLE', varObj: { id: 130 } }],
    model: { initialScore: 100, resultVar: { _varId: 130, varCode: 'score', varLabel: '评分' } },
    serializeDesignerDraft() { return JSON.stringify(this.model) },
    _syncModelVarRefs() { this.model.resultVar.varLabel = '评分 score' },
  }
  Object.entries(ruleDraftMixin.methods).forEach(([key, method]) => { vm[key] = method.bind(vm) })
  Object.defineProperty(vm, 'designerHasUnsavedChanges', { get: () => ruleDraftMixin.computed.designerHasUnsavedChanges.call(vm) })
  vm.initializeDesignerDraftTracking(vm.serializeDesignerDraft())
  return vm
}

describe('设计器引用元信息同步', () => {
  test('字段晚于模型加载时，同步名称不产生虚假的未保存修改', () => {
    const vm = host()
    varPickerMixin.methods._trySyncModelVarRefs.call(vm)
    vm.captureDesignerDraftState()
    expect(vm.model.resultVar.varLabel).toBe('评分 score')
    expect(vm.designerHasUnsavedChanges).toBe(false)
    expect(vm.designerActionState).toBe('CLEAN')
  })

  test('字段加载期间已有真实编辑，自动同步不会把编辑标记为已保存', () => {
    const vm = host()
    vm.model.initialScore = 200
    const baseline = vm.designerBaselineFingerprint
    varPickerMixin.methods._trySyncModelVarRefs.call(vm)
    vm.captureDesignerDraftState()
    expect(vm.designerBaselineFingerprint).toBe(baseline)
    expect(vm.model.initialScore).toBe(200)
    expect(vm.designerHasUnsavedChanges).toBe(true)
  })

  test('正在保存时的同步不得替换保存基线', () => {
    const vm = host()
    const baseline = vm.designerBaselineFingerprint
    vm.designerActionState = 'SAVING'
    varPickerMixin.methods._trySyncModelVarRefs.call(vm)
    expect(vm.designerBaselineFingerprint).toBe(baseline)
  })

  test('无元信息变化时保留已经通过的编译状态', () => {
    const vm = host()
    vm._syncModelVarRefs = () => {}
    vm.designerActionState = 'READY_TO_TEST'
    vm.designerCheckedFingerprint = vm.designerCurrentFingerprint
    varPickerMixin.methods._trySyncModelVarRefs.call(vm)
    expect(vm.designerActionState).toBe('READY_TO_TEST')
    expect(vm.designerCheckedFingerprint).toBe(vm.designerCurrentFingerprint)
  })
})
