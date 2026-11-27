import { parseExactJson, stringifyExactJson, isExactNumber } from '@/utils/exactJson'
import { parseFunctionParameters } from '@/utils/functionParameters'
import { sampleValueForVarType } from '@/utils/testParamTemplate'

function parameters(row) {
  const raw = row.paramsJson
  return parseFunctionParameters(typeof raw === 'string' && raw.trim() ? parseExactJson(raw) : raw)
}

export function functionSampleText(row) {
  const values = parameters(row).filter(param => param.name).map(param => [
    param.name, Object.hasOwn(param, 'example') ? param.example : sampleValueForVarType(param.type)
  ])
  return stringifyExactJson(Object.fromEntries(values), 2)
}

export function withFunctionSample(row, text) {
  const values = parseExactJson(text)
  const params = parameters(row)
  if (!values || typeof values !== 'object' || Array.isArray(values) || isExactNumber(values)
    || Object.keys(values).length !== params.length || params.some(param => !Object.hasOwn(values, param.name))) {
    throw new Error('测试样例须包含函数声明的每个参数，参数名称应与声明一致')
  }
  return { ...row, paramsJson: stringifyExactJson(params.map(param => ({ ...param, example: values[param.name] }))) }
}
