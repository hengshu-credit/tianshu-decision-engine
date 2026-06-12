const path = require('path')
const rootDir = path.resolve(__dirname)

module.exports = {
  testEnvironment: 'jsdom',
  setupFiles: ['<rootDir>/tests/setup.js'],
  moduleNameMapper: {
    // @ 别名（用函数形式避免 $1 捕获组在部分 Jest 版本 Windows 上不展开）
    '^@/router$': path.join(rootDir, 'src/router/index.js'),
    '^@/api/request$': path.join(rootDir, 'src/api/request.js'),
    '^@/layout/index\\.vue$': path.join(rootDir, 'src/layout/index.vue'),
    '^@/styles/variables\\.scss$': path.join(rootDir, 'src/styles/variables.scss'),
    '^@/styles/element-override\\.scss$': path.join(rootDir, 'src/styles/element-override.scss'),
    '^@/components/common/TraceTree\\.vue$': path.join(rootDir, 'src/components/common/TraceTree.vue'),
    '^@/components/flow/ActionBlockEditor\\.vue$': path.join(rootDir, 'src/components/flow/ActionBlockEditor.vue'),
    '^@/components/common/VarPicker\\.vue$': path.join(rootDir, 'src/components/common/VarPicker.vue'),
    '^@/components/common/ScriptPanel\\.vue$': path.join(rootDir, 'src/components/common/ScriptPanel.vue'),
    // 通用 @ 映射（兜底）
    '^@/(.*)$': path.join(rootDir, 'src', '$1'),
    // SCSS/CSS 空 mock
    '\\.scss$': path.join(rootDir, 'tests/__mocks__/styleMock.js'),
    '\\.css$': path.join(rootDir, 'tests/__mocks__/styleMock.js'),
    // 大型第三方模块
    '^monaco-editor$': path.join(rootDir, 'tests/__mocks__/monaco-editor.js'),
    '^@logicflow/core$': path.join(rootDir, 'tests/__mocks__/empty.js'),
    '^@logicflow/extension$': path.join(rootDir, 'tests/__mocks__/empty.js')
  },
  moduleFileExtensions: ['js', 'jsx', 'json', 'vue'],
  transform: {
    '^.+\\.vue$': '<rootDir>/node_modules/vue-jest',
    '^.+\\.js$': '<rootDir>/node_modules/babel-jest'
  },
  transformIgnorePatterns: [
    '/node_modules/(?!(@vue|vue-router|vuex|element-ui)/)'
  ],
  collectCoverageFrom: [
    'src/utils/**/*.js',
    'src/constants/**/*.js',
    '!src/**/*.spec.js'
  ]
}