import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import FieldPickerPanel from '@/components/common/FieldPickerPanel.vue'
import { buildFieldTree, filterFieldTree, fieldIdentity, visibleFieldRows, fieldTypeIcon } from '@/utils/fieldPicker'
import { recentFieldKeys, rememberField, selectedFieldKeys } from '@/utils/fieldPickerHistory'

const variable = (id, extra = {}) => ({ id, _varId: id, _refType: 'VARIABLE', varCode: `field${id}`, varLabel: `字段${id}`, varType: 'NUMBER', ...extra })
const objectField = (id, code, type = 'NUMBER', parentFieldId) => variable(id, { _refType: 'DATA_OBJECT', varCode: code, varType: type, parentFieldId, _ref: { category: 'object', objectId: '9223372036854775800', objectCode: 'report', objectLabel: '征信报告' } })
function panel(props = {}) {
  return mount(FieldPickerPanel, { props: { visible: true, ...props }, global: { stubs: { ElInput: { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' } } } })
}

describe('统一字段选择面板', () => {
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
  test('单击查看及折叠，双击才提交；不会提交展示用的合成分组', async () => {
    const wrapper = panel({ items: [objectField(1, 'report.items', 'LIST'), objectField(2, 'report.items.score', 'NUMBER', 1)] })
    await wrapper.find('.fp-row').trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
    await wrapper.find('.fp-row').trigger('dblclick')
    expect(wrapper.emitted('select')).toBeUndefined()
    const rows = wrapper.findAll('.fp-row')
    await rows[1].trigger('click')
    rows[1].element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 2 }))
    await nextTick()
    await rows[1].trigger('dblclick')
    expect(wrapper.emitted('select')[0][0]._varId).toBe(1)
    expect(wrapper.vm.expanded.has('DATA_OBJECT:1')).toBe(true)
    wrapper.unmount()
  })
  test('切分类不清除搜索；已选字段不匹配搜索时明确展示', async () => {
    const wrapper = panel({ items: [variable(1), objectField(2, 'report.score')], query: '不存在', selectedKey: 'VARIABLE:1' })
    wrapper.vm.changeCategory('object')
    expect(wrapper.emitted('search')).toBeUndefined()
    expect(wrapper.find('.fp-located').text()).toContain('字段1')
    wrapper.unmount()
  })
  test('搜索子字段保留层级，收起对象后没有子字段行', () => {
    const tree = buildFieldTree([objectField(1, 'report.items', 'LIST'), objectField(2, 'report.items.score', 'NUMBER', 1)])
    expect(visibleFieldRows(tree, new Set(), 'all', '').length).toBe(1)
    expect(visibleFieldRows(tree, new Set(), 'all', 'score').map(row => row.depth)).toEqual([0, 1, 2])
  })
})
