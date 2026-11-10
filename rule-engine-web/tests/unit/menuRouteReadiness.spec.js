vi.unmock('@/router')

import router from '@/router'
import { SIDEBAR_MENUS } from '@/layout/layoutState'

beforeAll(async () => {
  // 即使首次直达详情，布局就绪时菜单组件也必须全部就绪。
  await router.resolve('/rule/101').matched[0].components.default()
})

test.each(SIDEBAR_MENUS)('$label 在工作台显示前已具备页面组件，不依赖首次点击下载', ({ index }) => {
  const component = router.resolve(index).matched.at(-1).components.default
  expect(component).toBeTypeOf('object')
  expect(component.render).toBeTypeOf('function')
  expect(component.__asyncLoader).toBeUndefined()
})

test('详情和设计器保留按需下载与重试能力', () => {
  for (const path of ['/rule/101', '/designer/table/101', '/designer/script/101']) {
    const component = router.resolve(path).matched.at(-1).components.default
    expect(component).toBeTypeOf('function')
    expect(component.preload).toBeTypeOf('function')
  }
})
