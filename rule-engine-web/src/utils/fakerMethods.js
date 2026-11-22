import groups from '@/constants/faker-methods.json'

export const fakerGroups = groups
export const fakerMethods = new Map(groups.flatMap(group => group.methods.map(method => {
  const key = group.module + '.' + method.name
  return [key, { ...method, key, module: group.module, category: group.label }]
})))

export function fakerMethod(rule) {
  if (rule.generator !== 'FAKER') return undefined
  try { return fakerMethods.get(JSON.parse(rule.configJson)) }
  catch { return undefined }
}
