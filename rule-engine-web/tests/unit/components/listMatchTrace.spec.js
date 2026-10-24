import { mount } from '@test-utils'
import { flushPromises } from '@vue/test-utils'
import ListMatchTrace from '@/components/common/ListMatchTrace.vue'
import { getLibrary } from '@/api/ruleList'

describe('名单匹配过程', () => {
  beforeEach(() => {
    getLibrary.mockReset()
    getLibrary.mockImplementation(async id => ({ data: { id, listName: `风险名单${id}`, listCode: `risk_list_${id}` } }))
  })

  test.each([
    ['ANY_FIELD_ANY_LIST', '任一内容命中任一名单', '只要有一项', '或', '或'],
    ['ALL_FIELDS_ANY_LIST', '全部内容各自命中任一名单', '可以命中不同名单', '且', '或'],
    ['ANY_FIELD_ALL_LISTS', '任一内容同时命中全部名单', '至少有一项', '或', '且'],
    ['ALL_FIELDS_ALL_LISTS', '全部内容同时命中全部名单', '每项待匹配内容', '且', '且'],
  ])('%s 使用与执行矩阵一致的两层条件树', async (combinationMode, label, description, fields, lists) => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [1, 2], queryValues: ['a', 'b'], combinationMode, matchMode: 'IN_LIST', itemTypes: [] },
      responseBody: { hit: true }, success: 1,
    } })
    await flushPromises()
    expect(wrapper.text()).toContain(label)
    expect(wrapper.text()).toContain(description)
    expect(wrapper.text()).toContain('全部内容类型')
    expect(wrapper.find('.match-final').text()).toContain('命中')
    expect(wrapper.find('.match-tree-head .match-logic').text()).toBe(fields)
    expect(wrapper.findAll('.match-branch > .match-group-head .match-logic').map(node => node.text())).toEqual([lists, lists])
    expect(wrapper.findAll('.match-condition')).toHaveLength(4)
    expect(wrapper.findAll('.list-match-value').map(node => node.text())).toEqual(['a', 'a', 'b', 'b'])
    expect(wrapper.findAll('.match-library').map(node => node.find('.content-ref__name').text() + node.find('.content-ref__code').text())).toEqual([
      '风险名单1risk_list_1', '风险名单2risk_list_2', '风险名单1risk_list_1', '风险名单2risk_list_2',
    ])
    expect(wrapper.findAll('.match-condition-result').every(node => node.text() === '未记录逐项结果')).toBe(true)
    expect(wrapper.text()).not.toContain('名单 ID')
    wrapper.unmount()
  })

  test.each(['NOT_IN_LIST', 'NOT_CONTAINED_IN_LIST'])('排除模式 %s 不将 true 解释成在名单内', async matchMode => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [7], queryValues: ['a'], matchMode, combinationMode: 'ALL_FIELDS_ALL_LISTS' },
      responseBody: { hit: true }, success: 1,
    } })
    expect(wrapper.find('.match-final').text()).toContain('满足排除条件')
    expect(wrapper.text()).toContain('每项内容都要在全部所选名单中满足排除条件')
    expect(wrapper.text()).not.toContain('命中')
    await wrapper.setProps({ responseBody: { hit: false } })
    expect(wrapper.find('.match-final').text()).toContain('不满足排除条件')
    wrapper.unmount()
  })

  test('历史单值保留零值，未知配置不猜测为默认组合', () => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listId: 9, queryValue: 0, matchMode: 'FUTURE_MODE', itemTypes: ['FUTURE_TYPE'] },
      responseBody: { hit: 'false' }, success: 1,
    } })
    expect(wrapper.find('.list-match-value').text()).toBe('0')
    expect(wrapper.find('.match-library').attributes('title')).toBe('名单 ID：9')
    expect(wrapper.find('.match-tree-head .match-logic').text()).toBe('未知')
    expect(wrapper.text()).toContain('未记录组合方式')
    expect(wrapper.text()).toContain('FUTURE_MODE')
    expect(wrapper.text()).toContain('FUTURE_TYPE')
    expect(wrapper.find('.match-final').text()).toContain('未记录匹配结果')
    wrapper.unmount()
  })

  test('空值、空字符串和结构化输入均完整展示，响应错误优先于 hit', () => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { queryValues: [null, '', { value: '<script>unsafe</script>' }] },
      responseBody: { hit: true, error: '名单已失效' }, success: 1,
    } })
    expect(wrapper.findAll('.list-match-value').map(item => item.text())).toEqual([
      '空值（null）', '空字符串', JSON.stringify({ value: '<script>unsafe</script>' }),
    ])
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('.match-final').text()).toContain('匹配失败')
    expect(wrapper.text()).toContain('名单已失效')
    wrapper.unmount()
  })

  test('名单名称按字符串 ID 对应，重复 ID 仅查询一次，缺失和错误不会串名', async () => {
    getLibrary.mockImplementation(async id => {
      if (id === '2') throw new Error('无权查看')
      if (id === '3') return { data: null }
      if (id === '4') return { data: { id: 999, listName: '错误关联' } }
      return { data: { id, listName: '超长ID名单', listCode: 'large_id_list' } }
    })
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: ['9007199254740993', '9007199254740993', 2, 3, 4], queryValues: ['a'] },
    } })
    await flushPromises()
    expect(getLibrary).toHaveBeenCalledTimes(4)
    expect(getLibrary).toHaveBeenCalledWith('9007199254740993')
    expect(wrapper.findAll('.match-library .content-ref__name, .match-library > strong').map(node => node.text())).toEqual([
      '超长ID名单', '超长ID名单', '名单名称不可用', '名单名称不可用', '名单名称不可用',
    ])
    expect(wrapper.text()).not.toContain('错误关联')
    expect(wrapper.text()).toContain('已删除、无权查看或加载失败')
    wrapper.unmount()
  })

  test('切换日志时旧请求不能覆盖当前名单，最终结果不等待名称加载', async () => {
    let resolveOld
    getLibrary.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [1], queryValues: ['a'] }, responseBody: { hit: false }, success: 1,
    } })
    expect(wrapper.find('.match-final').text()).toContain('未命中')
    await wrapper.setProps({ requestBody: { listIds: [2], queryValues: ['b'] } })
    await flushPromises()
    resolveOld({ data: { id: 1, listName: '过期名称', listCode: 'stale' } })
    await flushPromises()
    expect(wrapper.find('.match-library .content-ref__code').text()).toBe('risk_list_2')
    expect(wrapper.text()).not.toContain('过期名称')
    wrapper.unmount()
  })

  test('每项实际值与对应来源保持顺序，多名单重复展示同一来源，阈值不带字段信息', async () => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [1, 2], queryValues: ['test-mobile', 0], queryOperands: [
        { kind: 'REFERENCE', refType: 'VARIABLE', label: '手机号', code: 'mobile', valueType: 'STRING', versionMode: 'FIXED', version: 3 },
        { kind: 'LITERAL' },
      ], combinationMode: 'ALL_FIELDS_ANY_LIST', matchMode: 'IN_LIST' }, responseBody: { hit: true }, success: 1,
    } })
    await flushPromises()
    const conditions = wrapper.findAll('.match-condition')
    expect(conditions).toHaveLength(4)
    expect(conditions.map(node => node.find('.content-ref__source').text())).toEqual(['变量', '变量', '阈值', '阈值'])
    expect(conditions.slice(0, 2).map(node => (node.find('.match-content .list-match-value').exists() ? node.find('.match-content .list-match-value') : node.find('.match-content .content-ref__value')).text())).toEqual(['test-mobile', 'test-mobile'])
    expect(conditions.slice(2).map(node => node.find('.match-content .content-ref__value').text())).toEqual(['0', '0'])
    expect(conditions[0].find('.content-ref__name').text()).toBe('手机号')
    expect(conditions[1].find('.content-ref__version').text()).toBe('固定 v3')
    expect(conditions[2].find('.match-content .content-ref__version').exists()).toBe(false)
    wrapper.unmount()
  })

  test('逐项结果来自执行矩阵，仅命中条件使用主题色标记', async () => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [1, 2], queryValues: ['a', 'b'], combinationMode: 'ALL_FIELDS_ANY_LIST', matchMode: 'IN_LIST' },
      responseBody: { hit: true, matchMatrix: [[true, false], [false, true]] }, success: 1,
    } })
    await flushPromises()
    expect(wrapper.findAll('.match-condition-result').map(node => node.text())).toEqual(['命中', '未命中', '未命中', '命中'])
    expect(wrapper.findAll('.match-condition').map(node => node.classes().includes('is-hit'))).toEqual([true, false, false, true])
    expect(wrapper.findAll('.match-group-result').map(node => node.text())).toEqual(['条件组满足', '条件组满足'])
    expect(wrapper.find('.match-final').classes()).toContain('is-success')
    expect(wrapper.text()).not.toContain('日志未记录逐项匹配明细')
    await wrapper.setProps({ responseBody: { hit: true, matchMatrix: [[true]] } })
    expect(wrapper.findAll('.match-condition.is-hit')).toHaveLength(0)
    expect(wrapper.findAll('.match-condition-result').every(node => node.text() === '未记录逐项结果')).toBe(true)
    wrapper.unmount()
  })

  test('排除条件的逐项 true 显示满足排除条件，失败日志不使用残留矩阵高亮', async () => {
    const wrapper = mount(ListMatchTrace, { props: {
      requestBody: { listIds: [1], queryValues: ['a'], combinationMode: 'ALL_FIELDS_ALL_LISTS', matchMode: 'NOT_IN_LIST' },
      responseBody: { hit: true, matchMatrix: [[true]] }, success: 1,
    } })
    expect(wrapper.find('.match-condition-result').text()).toBe('满足排除条件')
    expect(wrapper.text()).not.toContain('命中')
    await wrapper.setProps({ success: 0, errorMessage: '匹配异常' })
    expect(wrapper.findAll('.is-hit')).toHaveLength(0)
    expect(wrapper.find('.match-condition-result').text()).toBe('未记录逐项结果')
    expect(wrapper.find('.match-final').text()).toContain('匹配失败')
    wrapper.unmount()
  })
})
