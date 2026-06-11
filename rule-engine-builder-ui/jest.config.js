module.exports = {
  testEnvironment: 'jsdom',
  setupFiles: ['<rootDir>/tests/setup.js'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    // SCSS/CSS 空 mock
    '\\.scss$': '<rootDir>/tests/__mocks__/styleMock.js',
    '\\.css$': '<rootDir>/tests/__mocks__/styleMock.js'
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