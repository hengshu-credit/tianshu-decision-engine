// monaco-editor mock — 提供所有常用 API 的最小空实现
// 避免安装巨大的 monaco-editor 也能运行测试
module.exports = {
  // 编辑器
  editor: {
    create: vi.fn(() => ({
      getValue: vi.fn(() => ''),
      setValue: vi.fn(),
      dispose: vi.fn(),
      layout: vi.fn(),
      onDidChangeModelContent: vi.fn(() => ({ dispose: vi.fn() })),
      onDidFocusEditorText: vi.fn(() => ({ dispose: vi.fn() })),
      onDidBlurEditorText: vi.fn(() => ({ dispose: vi.fn() })),
      addCommand: vi.fn(),
      addAction: vi.fn(),
      getModel: vi.fn(() => ({ getLanguageId: vi.fn(() => 'ql') })),
      setModel: vi.fn(),
      setPosition: vi.fn(),
      getPosition: vi.fn(() => ({ lineNumber: 1, column: 1 })),
      revealLine: vi.fn(),
      revealLineInCenter: vi.fn(),
      getScrollTop: vi.fn(() => 0),
      setScrollTop: vi.fn(),
      getScrollLeft: vi.fn(() => 0),
      setScrollLeft: vi.fn(),
      hasTextFocus: vi.fn(() => false),
      updateOptions: vi.fn(),
      focus: vi.fn()
    })),
    createDiffEditor: vi.fn(() => ({
      getOriginalEditor: vi.fn(),
      getModifiedEditor: vi.fn(),
      getDiffModel: vi.fn(),
      setModel: vi.fn(),
      layout: vi.fn(),
      dispose: vi.fn()
    })),
    createModel: vi.fn(() => ({
      getValue: vi.fn(() => ''),
      setValue: vi.fn(),
      dispose: vi.fn()
    })),
    setModelLanguage: vi.fn(),
    setTheme: vi.fn(),
    defineTheme: vi.fn(),
    setLanguages: vi.fn(),
    getModel: vi.fn(),
    getModelMarkers: vi.fn(() => []),
    setModelMarkers: vi.fn()
  },
  // 语言服务
  languages: {
    getLanguages: vi.fn(() => []),
    register: vi.fn(),
    registerCompletionItemProvider: vi.fn(),
    setLanguageConfiguration: vi.fn(),
    HoverProvider: {}
  },
  // Token 类型
  languageserver: {},
  // 主题
  theme: {
    createSerializableData: vi.fn()
  },
  // 快捷键注册
  KeyMod: {},
  KeyCode: {},
  // Range/Position/Selection
  Range: vi.fn(),
  Position: vi.fn(),
  Selection: vi.fn(),
  // 常见命令
  Commands: {},
  // 标记范围
  MarkerSeverity: {},
  // IDisposable
  Disposable: vi.fn(() => ({ dispose: vi.fn() })),
  // 初始化完成回调
  onDidCreateEditor: vi.fn(),
  // Worker
  getWorker: vi.fn(),
  // 内部
  _allEditors: [],
  // 导出的静态方法（兼容不同版本 API）
  create: vi.fn(),
  define: vi.fn()
}
