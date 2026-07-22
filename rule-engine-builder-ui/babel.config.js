module.exports = {
  presets: [
    ['@vue/babel-preset-app', {
      bugfixes: true,
      polyfills: []
    }]
  ],
  plugins: [
    // 支持 ES2021 数字分隔符 (numeric separators: 20_000)
    '@babel/plugin-proposal-numeric-separator'
  ]
}
