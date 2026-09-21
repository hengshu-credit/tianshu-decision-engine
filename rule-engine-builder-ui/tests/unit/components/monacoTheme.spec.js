import { mount } from '@test-utils'
import * as monaco from 'monaco-editor'
import MonacoEditor from '@/components/MonacoEditor.vue'
import MonacoDiffEditor from '@/components/rule/versionDiff/MonacoDiffEditor.vue'

describe('Monaco 全局日夜主题同步', () => {
  beforeEach(() => {
    window.monaco = monaco
    document.documentElement.dataset.theme = 'light'
    document.documentElement.style.setProperty('--tianshu-bg-soft', '#f8fafc')
    document.documentElement.style.setProperty('--tianshu-bg-muted', '#f1f5f9')
    document.documentElement.style.setProperty('--tianshu-text-primary', '#172033')
    document.documentElement.style.setProperty('--tianshu-text-secondary', '#475569')
    document.documentElement.style.setProperty('--tianshu-text-tertiary', '#5b6678')
    document.documentElement.style.setProperty('--tianshu-border-subtle', '#e2e8f0')
    document.documentElement.style.setProperty('--el-color-primary', '#2639e9')
    document.documentElement.style.setProperty('--el-color-primary-dark-2', '#1e2eba')
    document.documentElement.style.setProperty('--el-color-primary-light-3', '#6775f0')
    document.documentElement.style.setProperty('--tianshu-color-secondary', '#f76e6c')
    document.documentElement.style.setProperty('--tianshu-success-text', '#793f46')
    monaco.editor.setTheme.mockClear()
    monaco.editor.defineTheme.mockClear()
  })

  afterEach(() => {
    delete window.monaco
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('style')
    vi.clearAllMocks()
  })

  test('普通编辑器使用应用背景和主题色生成 Monaco 主题并响应切换', async () => {
    document.documentElement.dataset.theme = 'dark'
    document.documentElement.style.setProperty('--tianshu-bg-soft', '#182238')
    document.documentElement.style.setProperty('--tianshu-bg-muted', '#202b45')
    document.documentElement.style.setProperty('--tianshu-text-primary', '#edf2ff')
    document.documentElement.style.setProperty('--tianshu-text-secondary', '#c2cce0')
    document.documentElement.style.setProperty('--tianshu-text-tertiary', '#93a2bd')
    document.documentElement.style.setProperty('--tianshu-border-subtle', '#28344d')
    const wrapper = mount(MonacoEditor, { props: { value: 'result = true' } })
    await wrapper.vm.$nextTick()

    expect(monaco.editor.defineTheme).toHaveBeenCalledWith(
      'tianshu-dark',
      expect.objectContaining({
        base: 'vs-dark',
        colors: expect.objectContaining({
          'editor.background': '#182238',
          'editor.foreground': '#EDF2FF',
          'editorCursor.foreground': '#6775F0',
          'editor.selectionBackground': '#2639E940',
        }),
      })
    )
    expect(monaco.editor.create).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ theme: 'tianshu-dark' })
    )

    document.documentElement.dataset.theme = 'light'
    document.documentElement.style.setProperty('--tianshu-bg-soft', '#f8fafc')
    document.documentElement.style.setProperty('--tianshu-bg-muted', '#f1f5f9')
    document.documentElement.style.setProperty('--tianshu-text-primary', '#172033')
    document.documentElement.style.setProperty('--tianshu-text-secondary', '#475569')
    document.documentElement.style.setProperty('--tianshu-text-tertiary', '#5b6678')
    document.documentElement.style.setProperty('--tianshu-border-subtle', '#e2e8f0')
    document.documentElement.style.setProperty('--el-color-primary', '#873ff2')
    document.documentElement.style.setProperty('--el-color-primary-dark-2', '#6c32c2')
    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { colorScheme: 'LIGHT', accentPreset: 'LIQUID_PURPLE' },
    }))
    expect(monaco.editor.defineTheme).toHaveBeenLastCalledWith(
      'tianshu-light',
      expect.objectContaining({
        base: 'vs',
        colors: expect.objectContaining({
          'editor.background': '#F8FAFC',
          'editorCursor.foreground': '#6C32C2',
          'editor.selectionBackground': '#873FF240',
        }),
      })
    )
    expect(monaco.editor.setTheme).toHaveBeenLastCalledWith('tianshu-light')
    wrapper.unmount()
  })

  test('差异编辑器和普通编辑器使用同一全局主题事件', async () => {
    const wrapper = mount(MonacoDiffEditor, {
      props: { original: 'a = 1', modified: 'a = 2', language: 'json' },
    })
    await wrapper.vm.$nextTick()

    window.dispatchEvent(new CustomEvent('tianshu-theme-change', {
      detail: { colorScheme: 'DARK' },
    }))

    expect(monaco.editor.setTheme).toHaveBeenCalledWith('tianshu-dark')
    wrapper.unmount()
  })

  test('夜间代码高亮使用可读的主题文字色，而非深色原始主色', async () => {
    document.documentElement.dataset.theme = 'dark'
    document.documentElement.style.setProperty('--el-color-primary', '#873ff2')
    document.documentElement.style.setProperty('--el-color-primary-light-3', '#ab79f6')
    document.documentElement.style.setProperty('--tianshu-success-text', '#aef3e0')
    const wrapper = mount(MonacoEditor, { props: { value: '{"result":"ok"}' } })
    await wrapper.vm.$nextTick()

    const theme = monaco.editor.defineTheme.mock.calls.at(-1)[1]
    expect(theme.rules).toEqual(expect.arrayContaining([
      expect.objectContaining({ token: 'string', foreground: 'AB79F6' }),
      expect.objectContaining({ token: 'function', foreground: 'AEF3E0' }),
    ]))
    wrapper.unmount()
  })

  test('组件卸载时清理主题事件监听', async () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener')
    const wrapper = mount(MonacoEditor)
    await wrapper.vm.$nextTick()

    wrapper.unmount()

    expect(removeSpy).toHaveBeenCalledWith(
      'tianshu-theme-change',
      wrapper.vm.handleGlobalThemeChange
    )
    removeSpy.mockRestore()
  })
})
