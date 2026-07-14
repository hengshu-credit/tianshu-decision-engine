import { LIST_COMBINATION_MODES, listCombinationMode } from '@/constants/listMatchModes'

describe('名单组合模式', () => {
  test('覆盖字段与名单的四种任一/全部组合且都有提示', () => {
    expect(LIST_COMBINATION_MODES.map(item => item.value)).toEqual([
      'ANY_FIELD_ANY_LIST',
      'ALL_FIELDS_ANY_LIST',
      'ANY_FIELD_ALL_LISTS',
      'ALL_FIELDS_ALL_LISTS'
    ])
    expect(LIST_COMBINATION_MODES.every(item => item.label && item.description)).toBe(true)
    expect(listCombinationMode('ALL_FIELDS_ALL_LISTS').description).toContain('最严格')
  })
})
