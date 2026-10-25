import { waitForMonaco } from '@/utils/monacoLoader'

afterEach(() => {
  vi.useRealTimers(); vi.unstubAllGlobals(); delete window.monaco; delete window.require
  document.querySelectorAll('script[data-tianshu-monaco-loader]').forEach(script => script.remove())
})

test('实际使用编辑器才按需加载，多编辑器共享一次同源 AMD 下载', async () => {
  vi.useFakeTimers()
  expect(document.querySelector('script[data-tianshu-monaco-loader]')).toBeNull()
  const first = waitForMonaco(new AbortController().signal)
  const second = waitForMonaco(new AbortController().signal)
  const scripts = document.querySelectorAll('script[data-tianshu-monaco-loader]')
  expect(scripts).toHaveLength(1)
  expect(new URL(scripts[0].src).origin).toBe(location.origin)
  const monaco = { editor: {} }
  window.require = Object.assign(vi.fn((_modules, ready) => ready(monaco)), { config: vi.fn() })
  scripts[0].dispatchEvent(new Event('load'))
  await vi.advanceTimersByTimeAsync(100)
  expect(await first).toBe(monaco)
  expect(await second).toBe(monaco)
  expect(window.require).toHaveBeenCalledTimes(1)
  expect(window.require.config).toHaveBeenCalledWith({ paths: { vs: expect.stringMatching(/\/vs$/) } })
})

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

test('已经取消的编辑器不会启动任何下载，脚本失败后下一次打开可以重试', async () => {
  vi.useFakeTimers()
  const controller = new AbortController()
  controller.abort()
  await expect(waitForMonaco(controller.signal)).resolves.toBeNull()
  expect(document.querySelector('script[data-tianshu-monaco-loader]')).toBeNull()
  const first = new AbortController()
  const waiting = waitForMonaco(first.signal)
  document.querySelector('script[data-tianshu-monaco-loader]').dispatchEvent(new Event('error'))
  first.abort()
  await expect(waiting).resolves.toBeNull()
  const second = new AbortController()
  const retry = waitForMonaco(second.signal)
  expect(document.querySelector('script[data-tianshu-monaco-loader]')).not.toBeNull()
  second.abort()
  await expect(retry).resolves.toBeNull()
  expect(vi.getTimerCount()).toBe(0)
})
