import HandlerDomProxy from 'zrender/lib/dom/HandlerProxy.js'

test('图表滚轮明确声明可取消，保留缩放阻止页面滚动的能力并清理监听', () => {
  const element = document.createElement('div')
  const addListener = vi.spyOn(element, 'addEventListener')
  const removeListener = vi.spyOn(element, 'removeEventListener')
  const proxy = new HandlerDomProxy(element, element)
  try {
    for (const type of ['wheel', 'mousewheel']) {
      const registration = addListener.mock.calls.find(args => args[0] === type)
      expect(registration[2]).toEqual({ passive: false })
    }
    proxy.on('mousewheel', event => event.preventDefault())
    const event = new WheelEvent('wheel', { deltaY: 100, cancelable: true })
    element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  } finally {
    proxy.dispose()
  }
  for (const type of ['wheel', 'mousewheel']) {
    expect(removeListener.mock.calls.some(args => args[0] === type)).toBe(true)
  }
})
