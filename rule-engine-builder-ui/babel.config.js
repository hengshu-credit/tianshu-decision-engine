module.exports = {
  presets: [
    ['@vue/cli-plugin-babel/preset', {
      targets: { ie: '11' },
      bugfixes: true
    }]
  ],
  plugins: [
    // 支持 ES2021 数字分隔符 (numeric separators: 20_000)
    '@babel/plugin-proposal-numeric-separator'
  ]
}
