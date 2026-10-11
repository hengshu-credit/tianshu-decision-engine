import { operandFromReferenceFields } from './operand'

export function levelVarFromOperand(operand) {
  return {
    operand: operand || null,
    varCode: (operand && (operand.code || operand.value)) || '',
    varLabel: (operand && (operand.label || operand.code || operand.value)) || '',
    varType: (operand && operand.valueType) || '',
    _varId: operand && operand.refId != null ? operand.refId : null,
    _refType: (operand && operand.refType) || null,
  }
}

export function normalizeScorecardLevelVar(model) {
  let target = model.levelVar
  if (!Object.hasOwn(model, 'levelVar')) {
    const legacy = model.thresholds && model.thresholds[0]
    if (legacy && legacy.resultVar) {
      target = { varCode: legacy.resultVar, _varId: legacy._varId, _refType: legacy._refType }
    }
  }
  if (!target) return levelVarFromOperand(null)
  return { ...target, operand: target.operand || operandFromReferenceFields(target) }
}
