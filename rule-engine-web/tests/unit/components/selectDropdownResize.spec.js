import fs from 'node:fs'
import path from 'node:path'
import { computed, ref } from 'vue'

const source = fs.readFileSync(path.resolve('node_modules/element-plus/es/components/select/src/select-dropdown.vue_vue_type_script_lang.mjs'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/^export .*;\r?\n/gm, '')
const load = new Function('dependencies', `const { useNamespace, selectKey, useResizeObserver, computed, defineComponent, inject, onMounted, onBeforeUnmount, ref } = dependencies;\n${source}\nreturn select_dropdown_vue_vue_type_script_lang_default`)
let frames, mounted, dispose, resize, select, view
beforeEach(() => {
  frames = new Map(); let token = 0; mounted = []; dispose = []
  vi.stubGlobal('requestAnimationFrame', callback => { const id = ++token; frames.set(id, callback); return id })
  vi.stubGlobal('cancelAnimationFrame', id => frames.delete(id))
  select = { props: {}, selectRef: { offsetWidth: 200 } }
  view = load({ computed, ref, defineComponent: value => value, inject: () => select,
    useNamespace: () => ({}), selectKey: Symbol('select'),
    onMounted: callback => mounted.push(callback), onBeforeUnmount: callback => dispose.push(callback),
    useResizeObserver: (_element, callback) => { resize = callback; return { stop: vi.fn() } },
  }).setup()
  mounted.forEach(callback => callback())
})
afterEach(() => { dispose.forEach(callback => callback()); vi.unstubAllGlobals() })
const flushFrame = () => { const pending = [...frames.values()]; frames.clear(); pending.forEach(callback => callback()) }

test('首次展示仍准确，观察期间不改变弹层宽度，下一帧使用最后一次真实宽度', () => {
  expect(view.minWidth.value).toBe('198px')
  select.selectRef.offsetWidth = 300; resize([])
  select.selectRef.offsetWidth = 420; resize([])
  expect(view.minWidth.value).toBe('198px')
  expect(frames.size).toBe(1)
  flushFrame()
  expect(view.minWidth.value).toBe('418px')
})

test('卸载后不再把待执行尺寸写回下拉框', () => {
  select.selectRef.offsetWidth = 300; resize([])
  dispose.forEach(callback => callback()); flushFrame()
  expect(view.minWidth.value).toBe('198px')
  expect(frames.size).toBe(0)
})
