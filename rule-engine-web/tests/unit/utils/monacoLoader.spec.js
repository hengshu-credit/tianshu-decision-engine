import { waitForMonaco } from '@/utils/monacoLoader'

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); delete window.monaco })

test('加载完成时返回对应实例并清理计时器', async () => {
  vi.useFakeTimers()
  const waiting = waitForMonaco(new AbortController().signal)
  const monaco = { editor: {} }
  window.monaco = monaco
  await vi.advanceTimersByTimeAsync(100)
  expect(await waiting).toBe(monaco)
  expect(vi.getTimerCount()).toBe(0)
})

test('取消加载立即结束，浏览器环境移除后也不会继续访问 window', async () => {
  vi.useFakeTimers()
  const controller = new AbortController()
  const waiting = waitForMonaco(controller.signal)
  controller.abort()
  vi.stubGlobal('window', undefined)
  await expect(waiting).resolves.toBeNull()
  await vi.runAllTimersAsync()
  expect(vi.getTimerCount()).toBe(0)
})

test('加载超时结束，不无限保留组件', async () => {
  vi.useFakeTimers()
  const waiting = waitForMonaco(new AbortController().signal)
  await vi.advanceTimersByTimeAsync(10000)
  await expect(waiting).resolves.toBeNull()
  expect(vi.getTimerCount()).toBe(0)
})
