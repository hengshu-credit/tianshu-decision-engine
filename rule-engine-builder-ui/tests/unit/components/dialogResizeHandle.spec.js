import { mount } from '@test-utils'
import DialogResizeHandle from '@/components/common/DialogResizeHandle.vue'

function mountHandle() {
  const dialog = document.createElement('div')
  dialog.className = 'el-dialog'
  document.body.appendChild(dialog)
  dialog.getBoundingClientRect = () => ({ left: 200, top: 100, width: 600, height: 500 })
  const wrapper = mount(DialogResizeHandle, { attachTo: dialog, props: { visible: true, minWidth: 520, minHeight: 360 } })
  return { wrapper, dialog, cleanup() { wrapper.unmount(); dialog.remove() } }
}

describe('弹窗尺寸拖拽', () => {
  test('居中弹窗拖拽右下角同时调整宽高，保持原页面光标和选择状态', () => {
    const { wrapper, dialog, cleanup } = mountHandle()
    document.body.style.cursor = 'crosshair'
    document.body.style.userSelect = 'text'
    try {
      wrapper.vm.start({ clientX: 800, clientY: 600, button: 0 })
      window.dispatchEvent(new MouseEvent('mousemove', { clientX: 840, clientY: 630 }))
      expect(dialog.style.width).toBe('680px')
      expect(dialog.style.height).toBe('560px')
      window.dispatchEvent(new MouseEvent('mouseup'))
      expect(document.body.style.cursor).toBe('crosshair')
      expect(document.body.style.userSelect).toBe('text')
      window.dispatchEvent(new MouseEvent('mousemove', { clientX: 999, clientY: 999 }))
      expect(dialog.style.width).toBe('680px')
    } finally {
      cleanup()
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  })

  test('尺寸上下限不超出视口，小屏幕优先可见而非强制最小宽度', () => {
    const { wrapper, dialog, cleanup } = mountHandle()
    try {
      wrapper.vm.start({ clientX: 800, clientY: 600, button: 0 })
      wrapper.vm.resize(9999, 9999)
      expect(parseFloat(dialog.style.width)).toBe(window.innerWidth - 32)
      expect(parseFloat(dialog.style.height)).toBe(window.innerHeight - 32)
      wrapper.vm.resize(-9999, -9999)
      expect(dialog.style.width).toBe('520px')
      expect(dialog.style.height).toBe('360px')
      vi.stubGlobal('innerWidth', 375)
      vi.stubGlobal('innerHeight', 300)
      wrapper.vm.resize(9999, 9999)
      expect(dialog.style.width).toBe('343px')
      expect(dialog.style.height).toBe('268px')
    } finally { vi.unstubAllGlobals(); cleanup() }
  })

  test('关闭或销毁正在拖拽的弹窗时取消监听并还原页面', async () => {
    const { wrapper, dialog, cleanup } = mountHandle()
    try {
      wrapper.vm.start({ clientX: 800, clientY: 600, button: 0 })
      await wrapper.setProps({ visible: false })
      expect(wrapper.vm.resizing).toBe(false)
      expect(document.body.style.userSelect).toBe('')
      window.dispatchEvent(new MouseEvent('mousemove', { clientX: 900, clientY: 700 }))
      expect(dialog.style.width).toBe('')
      await wrapper.setProps({ visible: true })
      wrapper.vm.startTouch({ touches: [{ clientX: 800, clientY: 600 }] })
      wrapper.vm.moveTouch({ touches: [{ clientX: 830, clientY: 620 }], preventDefault: vi.fn() })
      expect(dialog.style.width).toBe('660px')
      expect(dialog.style.height).toBe('540px')
    } finally { cleanup() }
    expect(document.body.style.userSelect).toBe('')
  })
})
