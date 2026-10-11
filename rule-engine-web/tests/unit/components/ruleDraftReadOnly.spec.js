import { flushPromises, mount } from '@test-utils'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'

describe('RuleDraftReadOnly', () => {
  test('加载期间以 info 状态提示播报，不渲染模态弹窗', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: true },
    })

    const notice = wrapper.get('[data-testid="draft-read-only"]')
    expect(notice.attributes('role')).toBe('status')
    expect(notice.attributes('aria-live')).toBe('polite')
    expect(notice.attributes('aria-modal')).toBeUndefined()
    expect(notice.text()).toContain('正在加载规则版本')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="go-rule-lifecycle"]').exists()).toBe(
      false
    )
  })

  test('提示区只保留带边框的迁移和重试，不重复显示返回', () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loadError: true, migrationRequired: true },
    })
    expect(wrapper.get('[data-testid="designer-reference-migration"]').attributes('link')).toBeUndefined()
    expect(wrapper.get('[data-testid="designer-source-retry"]').attributes('link')).toBeUndefined()
    expect(wrapper.find('[data-testid="draft-read-only-back"]').exists()).toBe(false)
    wrapper.unmount()
  })

  test('只读时以非模态提示说明权限，不提供创建草稿入口', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: { visible: true, loading: false },
    })

    expect(wrapper.text()).toContain('当前账号没有规则编辑权限')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="fork-view-revision"]').exists()).toBe(false)
  })

  test('允许编辑时保留同一个版本选择器并隐藏只读提示', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: {
        visible: false,
        loading: false,
        sourceOptions: [
          { value: 'REVISION:6', label: '草稿 · 2', group: 'REVISION', state: 'DRAFT' },
        ],
        selectedSource: 'REVISION:6',
      },
    })

    expect(wrapper.find('[data-testid="draft-read-only"]').exists()).toBe(false)
    const selector = wrapper.get('[data-testid="designer-version-control"]')
    expect(selector.exists()).toBe(true)
    await selector.get('.delete-draft').trigger('click')
    expect(wrapper.emitted('delete-source')[0][0].value).toBe('REVISION:6')
    await wrapper.setProps({ visible: true })
    expect(wrapper.get('[data-testid="designer-version-control"]').element).toBe(selector.element)
    expect(wrapper.find('.delete-draft').exists()).toBe(false)
    wrapper.unmount()
  })

  test('查看模式可以直接切换指定规则版本', async () => {
    const wrapper = mount(RuleDraftReadOnly, {
      props: {
        visible: true,
        loading: false,
        sourceOptions: [
          { value: 'REVISION:6', label: '待修改修订 v8', group: 'REVISION' },
          { value: 'VERSION:9', label: '发布版本 v7', group: 'VERSION' },
        ],
        selectedSource: 'VERSION:9',
      },
    })

    const selector = wrapper.findComponent({ name: 'RuleDesignerVersionSelect' })
    selector.vm.$emit('change', 'REVISION:6')
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('change-source')).toEqual([['REVISION:6']])
  })
})

describe('RuleDraftReadOnly source actions', () => {
  test.each(['VERSION', 'REVIEW', 'LEGACY'])('%s 不提供创建草稿入口，只说明查看权限', (revisionState) => {
    const wrapper = mount(RuleDraftReadOnly, { props: { visible: true, loading: false, revisionLabel: '版本 7', revisionState, canFork: true } })
    expect(wrapper.text()).toContain('版本 7')
    expect(wrapper.text()).toContain('当前账号没有规则编辑权限')
    expect(wrapper.find('[data-testid="fork-view-revision"]').exists()).toBe(false)
    wrapper.unmount()
  })
  test('加载失败保留页内重试，重试期间不能重复触发', async () => {
    const wrapper = mount(RuleDraftReadOnly, { props: { visible: true, loadError: true } })
    expect(wrapper.text()).toContain('当前版本加载失败')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await wrapper.get('[data-testid="designer-source-retry"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    await wrapper.setProps({ loading: true })
    expect(wrapper.find('[data-testid="designer-source-retry"]').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('RuleDraftReadOnly interaction isolation', () => {
  test('嵌入工具栏后仍保护编辑区与其他操作，版本与重试可用，已有 inert 不被清除', async () => {
    const wrapper = mount({
      components: { RuleDraftReadOnly },
      data: () => ({ visible: true }),
      template: `
        <div class="uiue-compact-designer">
          <header><div><button data-designer-back>返回</button><span>标题</span></div><nav>
            <rule-draft-read-only :visible="visible" :load-error="true" />
            <button data-testid="save">保存</button>
          </nav></header>
          <section data-testid="content"><input /></section>
          <section data-testid="preexisting" inert />
        </div>
      `,
    })
    await flushPromises()
    expect(wrapper.get('[data-testid="designer-source-retry"]').element.closest('[inert]')).toBeNull()
    expect(wrapper.get('[data-designer-back]').element.closest('[inert]')).toBeNull()
    expect(wrapper.get('[data-testid="save"]').attributes('inert')).toBe('')
    expect(wrapper.get('[data-testid="content"]').attributes('inert')).toBe('')
    wrapper.vm.visible = false
    await flushPromises()
    expect(wrapper.get('[data-testid="save"]').element.closest('[inert]')).toBeNull()
    expect(wrapper.get('[data-testid="content"]').attributes('inert')).toBeUndefined()
    expect(wrapper.get('[data-testid="preexisting"]').attributes('inert')).toBe('')
    wrapper.unmount()
  })
  test('信息提示不遮挡页面，但仍隔离编辑区域且恢复后解除保护', async () => {
    const wrapper = mount({
      components: { RuleDraftReadOnly },
      data() {
        return { visible: true }
      },
      template: `
        <div>
          <rule-draft-read-only :visible="visible" />
          <section data-testid="designer-content">
            <button data-testid="designer-save">Save</button>
          </section>
        </div>
      `,
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="designer-content"]').attributes('inert')).toBe('')

    wrapper.vm.visible = false
    await flushPromises()

    expect(wrapper.get('[data-testid="designer-content"]').attributes('inert')).toBeUndefined()
  })
})
