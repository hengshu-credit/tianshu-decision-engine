module.exports = {
  presets: [
    ['@vue/babel-preset-app', {
      targets: { ie: '11' },
      bugfixes: true,
      polyfills: []
    }]
  ],
  plugins: [
    // 支持 ES2021 数字分隔符 (numeric separators: 20_000)
    '@babel/plugin-proposal-numeric-separator'
  ]
}
