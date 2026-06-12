// monaco-editor mock — 提供所有常用 API 的最小空实现
// 避免安装巨大的 monaco-editor 也能运行测试
module.exports = {
  // 编辑器
  editor: {
    create: jest.fn(() => ({
      getValue: jest.fn(() => ''),
      setValue: jest.fn(),
      dispose: jest.fn(),
      layout: jest.fn(),
      onDidChangeModelContent: jest.fn(() => ({ dispose: jest.fn() })),
      onDidFocusEditorText: jest.fn(() => ({ dispose: jest.fn() })),
      onDidBlurEditorText: jest.fn(() => ({ dispose: jest.fn() })),
      addCommand: jest.fn(),
      addAction: jest.fn(),
      getModel: jest.fn(() => ({ getLanguageId: jest.fn(() => 'ql') })),
      setModel: jest.fn(),
      setPosition: jest.fn(),
      getPosition: jest.fn(() => ({ lineNumber: 1, column: 1 })),
      revealLine: jest.fn(),
      revealLineInCenter: jest.fn(),
      getScrollTop: jest.fn(() => 0),
      setScrollTop: jest.fn(),
      getScrollLeft: jest.fn(() => 0),
      setScrollLeft: jest.fn(),
      hasTextFocus: jest.fn(() => false),
      updateOptions: jest.fn(),
      focus: jest.fn()
    })),
    createDiffEditor: jest.fn(() => ({
      getOriginalEditor: jest.fn(),
      getModifiedEditor: jest.fn(),
      getDiffModel: jest.fn(),
      dispose: jest.fn()
    })),
    setModelLanguage: jest.fn(),
    setTheme: jest.fn(),
    defineTheme: jest.fn(),
    setLanguages: jest.fn(),
    getModel: jest.fn(),
    getModelMarkers: jest.fn(() => []),
    setModelMarkers: jest.fn()
  },
  // 语言服务
  languages: {
    getLanguages: jest.fn(() => []),
    register: jest.fn(),
    registerCompletionItemProvider: jest.fn(),
    setLanguageConfiguration: jest.fn()
  },
  // Token 类型
  languageserver: {},
  // 主题
  theme: {
    createSerializableData: jest.fn()
  },
  // 快捷键注册
  KeyMod: {},
  KeyCode: {},
  // Range/Position/Selection
  Range: jest.fn(),
  Position: jest.fn(),
  Selection: jest.fn(),
  // 常见命令
  Commands: {},
  // hover 提供
  languages.HoverProvider = {},
  // 标记范围
  MarkerSeverity: {},
  // IDisposable
  Disposable: jest.fn(() => ({ dispose: jest.fn() })),
  // 初始化完成回调
  onDidCreateEditor: jest.fn(),
  // Worker
  getWorker: jest.fn(),
  // 内部
  _allEditors: [],
  // 导出的静态方法（兼容不同版本 API）
  create: jest.fn(),
  define: jest.fn()
}