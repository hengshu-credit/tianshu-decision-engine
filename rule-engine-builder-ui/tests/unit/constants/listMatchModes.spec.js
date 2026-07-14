import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, POSITIVE_LIST_MATCH_MODES, listCombinationMode } from '@/constants/listMatchModes'

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

  test('条件名单查询只暴露正向匹配，负向由外层操作符表达', () => {
    expect(POSITIVE_LIST_MATCH_MODES.map(item => item.value)).toEqual(['IN_LIST', 'CONTAINED_IN_LIST'])
    expect(LIST_ITEM_TYPES.map(item => item.value)).toContain('MOBILE')
  })
})
