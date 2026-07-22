const fs = require('fs')
const path = require('path')

const projectRoot = path.resolve(__dirname, '../..')
const srcRoot = path.join(projectRoot, 'src')

function readSourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) return readSourceFiles(fullPath)
    if (!/\.(js|vue)$/.test(entry.name)) return []
    return [{ file: path.relative(projectRoot, fullPath), source: fs.readFileSync(fullPath, 'utf8') }]
  })
}

describe('Vue 3 migration contract', () => {
  test('uses the native Vue 3 application and test stack', () => {
    const pkg = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8'))

    expect(pkg.dependencies.vue).toMatch(/^\^?3\./)
    expect(pkg.dependencies['vue-router']).toMatch(/^\^?4\./)
    expect(pkg.dependencies.vuex).toMatch(/^\^?4\./)
    expect(pkg.dependencies['element-plus']).toBeDefined()
    expect(pkg.dependencies['element-ui']).toBeUndefined()
    expect(pkg.devDependencies['@vue/test-utils']).toMatch(/^\^?2\./)
    expect(pkg.devDependencies['@vue/vue3-jest']).toBeDefined()
    expect(pkg.devDependencies['vue-jest']).toBeUndefined()
    expect(pkg.devDependencies['vue-template-compiler']).toBeUndefined()
    expect({ ...pkg.dependencies, ...pkg.devDependencies }).not.toHaveProperty('@vue/compat')
  })

  test('contains no Vue 2-only runtime or template APIs', () => {
    const forbidden = [
      ['slot-scope', /\bslot-scope\s*=/],
      ['sync modifier', /\.sync(?:\s|=|>)/],
      ['native event modifier', /\.native(?:\s|=|>)/],
      ['beforeDestroy lifecycle', /\bbeforeDestroy\s*\(/],
      ['destroyed lifecycle', /\bdestroyed\s*\(/],
      ['$set', /\bthis\.\$set\s*\(/],
      ['$delete', /\bthis\.\$delete\s*\(/],
      ['$listeners', /\$listeners\b/],
      ['Vue 2 functional option', /\bfunctional\s*:\s*true/],
      ['Vue.use', /\bVue\.use\s*\(/],
      ['new Vue', /\bnew\s+Vue\s*\(/],
      [
        'Element Plus deprecated text button',
        /<el-button\b(?:[^"'<>]|"[^"]*"|'[^']*')*\btype\s*=\s*["']text["']/
      ],
      [
        'Element Plus deprecated radio value alias',
        /<el-radio(?:-button)?\b[^>]*\b:?label\s*=/
      ],
      [
        'custom CSS class passed as Element Plus tag type',
        /<el-tag\b[^>]*:type\s*=\s*["'][^"']*scope-/
      ],
      ['reactive Element Plus icon in component data', /^ {6}ElIcon\w+,\s*$/m],
      [
        'router-view nested directly in keep-alive',
        /<keep-alive\b[^>]*>\s*<router-view\b/
      ],
      [
        'Element Plus deprecated small pagination prop',
        /<el-pagination\b[^>]*(?:^|\s)small(?:\s|\/?>)/
      ],
      ['element-ui import', /(?:from\s+|require\()['"]element-ui(?:['"/)])?/],
      [
        'Element UI icon font class',
        /(?:(?:class|icon)\s*=\s*["'][^"']*\bel-icon-(?!-)|["']el-icon-(?!-))/
      ]
    ]

    const violations = []
    readSourceFiles(srcRoot).forEach(({ file, source }) => {
      forbidden.forEach(([name, pattern]) => {
        if (pattern.test(source)) violations.push(`${file}: ${name}`)
      })
    })

    expect(violations).toEqual([])
  })
})
