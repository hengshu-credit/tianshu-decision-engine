import { mount } from '@test-utils'
import { nextTick } from 'vue'
import DesignerInputTooltip from '@/components/common/DesignerInputTooltip.vue'

describe('DesignerInputTooltip', () => {
  let wrapper
  let root
  let input

  beforeEach(() => {
    vi.useFakeTimers()
    root = document.createElement('div')
    root.className = 'uiue-compact-designer'
    input = document.createElement('input')
    input.value = 'dateDiffDays(currentDate(), 申请信息.身份证到期日)'
    Object.defineProperties(input, {
      clientWidth: { configurable: true, value: 120 },
      scrollWidth: { configurable: true, value: 460 },
    })
    root.append(input)
    document.body.append(root)
    wrapper = mount(DesignerInputTooltip, {
      props: { enabled: true },
      stubs: {
        'el-tooltip': {
          props: ['visible', 'virtualRef'],
          template: '<div v-if="visible" role="tooltip"><slot name="content" /></div>',
        },
      },
    })
  })

  afterEach(() => {
    wrapper.unmount()
    root.remove()
    vi.useRealTimers()
  })

  async function hover() {
    input.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
    await vi.advanceTimersByTimeAsync(300)
    await nextTick()
  }

  test('长字段、函数和阈值显示完整配置，点击或编辑后立即隐藏', async () => {
    await hover()
    expect(wrapper.get('[role="tooltip"]').text()).toBe(input.value)
    expect(wrapper.vm.hoverTarget).toBe(input)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false)
  })

  test.each(['short', 'empty', 'password', 'monaco', 'outside', 'open-picker'])('%s 不显示无关或敏感提示', async mode => {
    if (mode === 'short') Object.defineProperty(input, 'scrollWidth', { value: 100 })
    if (mode === 'empty') input.value = ''
    if (mode === 'password') input.type = 'password'
    if (mode === 'monaco') root.classList.add('monaco-editor')
    if (mode === 'outside') root.className = 'management-page'
    if (mode === 'open-picker') input.setAttribute('aria-expanded', 'true')
    await hover()
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false)
  })

  test('离开设计器和销毁组件时取消延迟提示及监听', async () => {
    await hover()
    await wrapper.setProps({ enabled: false })
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false)
    const remove = vi.spyOn(document, 'removeEventListener')
    wrapper.unmount()
    expect(remove).toHaveBeenCalledWith('mouseover', wrapper.vm.onMouseOver)
    expect(remove).toHaveBeenCalledWith('mouseout', wrapper.vm.onMouseOut)
    remove.mockRestore()
  })

  test('配置中的 HTML 只以文本显示', async () => {
    input.value = '<img src=x onerror=alert(1)>'
    await hover()
    expect(wrapper.get('[role="tooltip"]').text()).toBe(input.value)
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
