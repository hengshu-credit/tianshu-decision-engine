import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import FieldPickerPanel from '@/components/common/FieldPickerPanel.vue'
import { buildFieldTree, filterFieldTree, fieldIdentity, fieldCategoryGroups, visibleFieldRows, fieldTypeIcon } from '@/utils/fieldPicker'
import { recentFieldKeys, rememberField, selectedFieldKeys } from '@/utils/fieldPickerHistory'
import { FIELD_PICKER_COLUMNS, fitFieldColumnWidths, resizeFieldColumnWidths, preferredFieldCategoryWidth, setPreferredFieldCategoryWidth, preferredFieldColumnWidths, setPreferredFieldColumnWidths } from '@/utils/fieldPickerPreferences'

const variable = (id, extra = {}) => ({ id, _varId: id, _refType: 'VARIABLE', varCode: `field${id}`, varLabel: `字段${id}`, varType: 'NUMBER', ...extra })
const objectField = (id, code, type = 'NUMBER', parentFieldId) => variable(id, { _refType: 'DATA_OBJECT', varCode: code, varType: type, parentFieldId, _ref: { category: 'object', objectId: '9223372036854775800', objectCode: 'report', objectLabel: '征信报告' } })
function panel(props = {}) {
  return mount(FieldPickerPanel, { props: { visible: true, ...props }, global: { stubs: { ElInput: { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' } } } })
}

describe('统一字段选择面板', () => {
  test('四列表头调宽联动其他列，保持总宽与最小宽度，偏好可重置', () => {
    const previous = preferredFieldColumnWidths.value
    const wrapper = panel({ embedded: true, items: [variable(1)] })
    try {
      wrapper.vm.resetColumnWidths()
      wrapper.vm.adjustColumnWidth('code', 48)
      expect(preferredFieldColumnWidths.value).toEqual({ type: 64, name: 80, code: 168, version: 80 })
      expect(JSON.parse(window.localStorage.getItem('tianshu-field-picker-column-widths')).code).toBe(168)
      wrapper.vm.adjustColumnWidth('code', -1000)
      expect(preferredFieldColumnWidths.value.code).toBe(96)
      expect(Object.values(preferredFieldColumnWidths.value).reduce((sum, width) => sum + width, 0)).toBeCloseTo(392, 2)
      wrapper.vm.resetColumnWidths()
      expect(preferredFieldColumnWidths.value).toBeNull()
    } finally { wrapper.unmount(); setPreferredFieldColumnWidths(previous) }
  })
  test('旧像素列宽在宽窄容器中始终填满可用空间，调整后保持新的比例', () => {
    const saved = { type: 64, name: 104, code: 192, version: 88 }
    for (const size of [320, 392, 600, 1200]) {
      const fitted = fitFieldColumnWidths(saved, size)
      expect(Object.values(fitted).reduce((sum, width) => sum + width, 0)).toBeCloseTo(size, 5)
      for (const column of FIELD_PICKER_COLUMNS) expect(fitted[column.key]).toBeGreaterThanOrEqual(column.min)
    }
    expect(fitFieldColumnWidths(saved, 200)).toEqual({ type: 64, name: 80, code: 96, version: 80 })
    const before = fitFieldColumnWidths(saved, 600)
    const after = resizeFieldColumnWidths(before, 'code', 56)
    expect(after.code).toBeCloseTo(before.code + 56, 5)
    expect(after.name).toBeLessThan(before.name)
    expect(Object.values(after).reduce((sum, width) => sum + width, 0)).toBeCloseTo(600, 5)
    expect(fitFieldColumnWidths(after, 900).code / fitFieldColumnWidths(after, 900).name).toBeCloseTo(after.code / after.name, 4)
  })
  test('分类栏支持键盘和拖拽调宽，并保留宽度偏好', async () => {
    const previous = preferredFieldCategoryWidth.value
    setPreferredFieldCategoryWidth(144)
    const wrapper = panel({ embedded: true, items: [variable(1)] })
    try {
      await wrapper.get('[aria-label="调整分类栏宽度"]').trigger('keydown', { key: 'ArrowRight' })
      expect(wrapper.vm.categoryWidth).toBe(160)
      wrapper.vm.startMotion({ button: 0, clientX: 100, clientY: 0, preventDefault() {} }, 'categories')
      wrapper.vm.onMotion({ clientX: 180, clientY: 0 })
      wrapper.vm.stopMotion()
      expect(wrapper.vm.categoryWidth).toBe(240)
      expect(window.localStorage.getItem('tianshu-field-picker-category-width')).toBe('240')
      wrapper.vm.setCategoryWidth(10000)
      expect(wrapper.vm.categoryWidth).toBe(320)
    } finally { wrapper.unmount(); setPreferredFieldCategoryWidth(previous) }
  })
  test('没有子字段的对象仍以自身名称归类，方法按真实实现方式分类', () => {
    const items = [{ _varId: 9, _refType: 'DATA_OBJECT_ROOT', rootReference: true, varCode: 'Empty', varLabel: '空对象', varType: 'OBJECT', _ref: { category: 'object', objectId: 9, objectCode: 'Empty', objectLabel: '空对象' } },
      { _function: true, function: { id: 4, implType: 'JAVA' }, _refType: 'FUNCTION', _varId: 4, varCode: 'calculate()', varLabel: '计算', varType: 'NUMBER' }]
    const groups = fieldCategoryGroups(buildFieldTree(items))
    expect(groups.map(group => group.label)).toEqual(['空对象', 'Java 方法'])
    expect(groups.map(group => group.count)).toEqual([1, 1])
  })
  test('二级对象分类按稳定身份过滤，查看字段不清除分类', async () => {
    const first = objectField(11, 'report.score')
    const second = objectField(12, 'other.score')
    second._ref = { ...second._ref, objectId: '2', objectCode: 'other', objectLabel: '另一个报告' }
    const wrapper = panel({ items: [first, second], category: 'object', embedded: true })
    expect(wrapper.attributes('role')).toBe('complementary')
    expect(wrapper.findAll('.fp-subcategory')).toHaveLength(2)
    const group = wrapper.vm.categoryGroups.find(group => group.label === '征信报告')
    wrapper.vm.selectGroup(group)
    await nextTick()
    expect(wrapper.vm.rows.some(row => row.node.code === 'report.score')).toBe(true)
    expect(wrapper.vm.rows.some(row => row.node.code === 'other.score')).toBe(false)
    wrapper.vm.inspect(wrapper.vm.tree.nodes.get('DATA_OBJECT:11'), { detail: 1 })
    expect(wrapper.vm.selectedGroup).toBe(group.key)
    wrapper.vm.search('score')
    await wrapper.setProps({ query: 'score', visible: false })
    await wrapper.setProps({ visible: true })
    expect(wrapper.vm.selectedGroup).toBe(group.key)
    expect(wrapper.vm.rows.some(row => row.node.code === 'other.score')).toBe(false)
    expect(wrapper.findAll('[role="columnheader"]').map(cell => cell.text())).toEqual(['数据类型', '名称', '编码', '版本'])
    wrapper.unmount()
  })
  test('外部调整每页条数会保留当前位置并重算页码', async () => {
    const wrapper = panel({ items: Array.from({ length: 120 }, (_, i) => variable(i)), initialPageSize: 25 })
    wrapper.vm.setPage(3)
    await wrapper.setProps({ initialPageSize: 100 })
    expect(wrapper.vm.pageSize).toBe(100)
    expect(wrapper.vm.page).toBe(1)
    expect(wrapper.vm.pageRows).toHaveLength(100)
    wrapper.unmount()
  })
  test('已选内容包含嵌套参数引用；最近使用去重且只显示当前有权选择的字段', async () => {
    recentFieldKeys.value = []
    const one = variable('9007199254740993'), two = variable(2)
    rememberField(one); rememberField(two); rememberField(one)
    expect(recentFieldKeys.value).toEqual([fieldIdentity(one), fieldIdentity(two)])
    const selected = selectedFieldKeys({ kind: 'FUNCTION', functionId: 4, args: [{ kind: 'REFERENCE', refId: one.id, refType: 'VARIABLE' }] }, [one, two])
    expect(selected).toEqual([fieldIdentity(one)])
    const wrapper = panel({ items: [one], selectedKeys: selected, recentKeys: recentFieldKeys.value, category: 'recent' })
    expect(wrapper.find('.fp-categories').text()).toContain('已选内容1')
    expect(wrapper.find('.fp-categories').text()).toContain('最近使用1')
    expect(wrapper.findAll('.fp-row')).toHaveLength(1)
    await wrapper.find('.fp-row').trigger('dblclick')
    expect(wrapper.emitted('select')[0][0].id).toBe(one.id)
    wrapper.unmount()
    recentFieldKeys.value = []
  })
  test('没有字段的分类不展示，最近使用和已选内容保持常驻', () => {
    const wrapper = panel({ items: [variable(1)], category: 'all' })
    const categoryKeys = wrapper.vm.categories.map(item => item.key)
    expect(categoryKeys).toEqual(expect.arrayContaining(['recent', 'selected', 'all', 'standalone']))
    expect(categoryKeys).not.toContain('object')
    expect(categoryKeys).not.toContain('model')
    expect(categoryKeys).not.toContain('function')
    wrapper.unmount()
  })
  test('同名引用按 ID 区分，数字和复合类型有明确图标', () => {
    const a = variable('9007199254740993', { varCode: 'same' }), b = variable('9007199254740994', { varCode: 'same' })
    expect(buildFieldTree([a, b]).roots.map(node => node.key)).toEqual([fieldIdentity(a), fieldIdentity(b)])
    expect(['NUMBER', 'INTEGER', 'STRING', 'LIST', 'OBJECT'].map(fieldTypeIcon)).toEqual(['N', '123', 'Abc', '[]', '{}'])
  })
  test('类型过滤保留必要的父级，父级不能冒充数字字段选择', () => {
    const tree = filterFieldTree(buildFieldTree([objectField(1, 'report.items', 'LIST'), objectField(2, 'report.items.score', 'INTEGER', 1), objectField(3, 'report.name', 'STRING')]), 'NUMBER')
    expect([...tree.nodes.keys()]).toEqual(expect.arrayContaining(['DATA_OBJECT:1', 'DATA_OBJECT:2']))
    expect(tree.nodes.has('DATA_OBJECT:3')).toBe(false)
    expect(tree.nodes.get('DATA_OBJECT:1').selectable).toBe(false)
    expect(tree.nodes.get('DATA_OBJECT:2').parent.key).toBe('DATA_OBJECT:1')
  })
  test('9000 字段定位、分页、改变条数时仅渲染窗口，分页保留吸顶祖先', async () => {
    const fields = Array.from({ length: 9000 }, (_, i) => objectField(i + 1, `report.item${i + 1}`))
    const wrapper = panel({ items: fields, selectedKey: 'DATA_OBJECT:8999', initialPageSize: 100 })
    await nextTick(); await nextTick(); await nextTick()
    expect(wrapper.vm.page).toBe(90)
    expect(wrapper.findAll('.fp-row').length).toBeLessThan(30)
    expect(wrapper.find('.fp-row--selected').text()).toContain('item8999')
    expect(wrapper.vm.stickyParents.map(node => node.name)).toEqual(['征信报告'])
    wrapper.vm.changePageSize(25)
    await nextTick()
    expect(wrapper.vm.page).toBe(357)
    expect(wrapper.vm.pageRows.length).toBe(25)
    expect(wrapper.emitted('page-size').at(-1)).toEqual([25])
    wrapper.unmount()
  })
  test('叶子字段双击提交，父级单击展开；不会提交展示用的合成分组', async () => {
    const wrapper = panel({ items: [objectField(1, 'report.items', 'LIST'), objectField(2, 'report.items.score', 'NUMBER', 1)] })
    await wrapper.find('.fp-row').trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
    await nextTick()
    const objectRow = wrapper.findAll('.fp-row').find(row => row.text().includes('items'))
    await objectRow.trigger('click')
    await nextTick()
    const leaf = wrapper.findAll('.fp-row').find(row => row.text().includes('score'))
    await leaf.trigger('dblclick')
    await nextTick()
    expect(wrapper.emitted('select')[0][0]._varId).toBe(2)
    expect(wrapper.vm.expanded.has('DATA_OBJECT:1')).toBe(true)
    wrapper.unmount()
  })
  test('数据对象根节点可以作为整体字段提交', async () => {
    const wrapper = panel({ items: [objectField(1, 'report.items', 'LIST')] })
    const selectButton = wrapper.find('.fp-select-parent')
    expect(selectButton.exists()).toBe(true)
    await selectButton.trigger('click')
    expect(wrapper.emitted('select')[0][0]).toMatchObject({ _varId: '9223372036854775800', _refType: 'DATA_OBJECT_ROOT', _versionGroup: true })
    wrapper.unmount()
  })
  test('切分类不清除搜索；已选字段不匹配搜索时明确展示', async () => {
    const wrapper = panel({ items: [variable(1), objectField(2, 'report.score')], query: '不存在', selectedKey: 'VARIABLE:1' })
    wrapper.vm.changeCategory('object')
    expect(wrapper.emitted('search')).toBeUndefined()
    expect(wrapper.find('.fp-located').text()).toContain('字段1')
    wrapper.unmount()
  })
  test('对象分类只计一级对象，子字段不重复计数也不在左侧目录列出', () => {
    const wrapper = panel({ items: [objectField(1, 'report.score'), objectField(2, 'report.name')], category: 'object', selectedKeys: ['DATA_OBJECT:1', 'DATA_OBJECT:2'], recentKeys: ['DATA_OBJECT:1', 'DATA_OBJECT:2'] })
    for (const category of ['object', 'all', 'selected', 'recent']) expect(wrapper.vm.categories.find(item => item.key === category).count).toBe(1)
    expect(wrapper.findAll('.fp-subcategory').map(item => item.text())).toEqual(['征信报告'])
    expect(wrapper.find('.fp-subcategory small').exists()).toBe(false)
    expect(wrapper.find('.fp-categories').text()).not.toContain('report.score')
    expect(wrapper.vm.tree.nodes.has('DATA_OBJECT:1')).toBe(true)
    wrapper.unmount()
  })
  test('完整字段预览使用可换行的受控提示，不依赖原生 title 滚动提示', async () => {
    const wrapper = panel({ items: [variable(1, { varLabel: '合同签署授权/证件反面图片', varCode: 'authorization_for_contracts_execution.idcard_back_image' })], selectedKey: 'VARIABLE:1' })
    await nextTick(); await nextTick()
    try {
      expect(wrapper.vm.previewText).toContain('合同签署授权/证件反面图片')
      expect(wrapper.get('.fp-preview').attributes('title')).toBeUndefined()
      expect(wrapper.find('.field-picker-preview-tooltip').exists()).toBe(false)
      expect(wrapper.html()).toContain('field-picker-preview-tooltip')
    } finally { wrapper.unmount() }
  })
  test('搜索子字段保留层级，收起对象后没有子字段行', () => {
    const tree = buildFieldTree([objectField(1, 'report.items', 'LIST'), objectField(2, 'report.items.score', 'NUMBER', 1)])
    expect(visibleFieldRows(tree, new Set(), 'all', '').length).toBe(1)
    expect(visibleFieldRows(tree, new Set(), 'all', 'score').map(row => row.depth)).toEqual([0, 1, 2])
  })
})
