import { clampDesignerPanelWidth, designerPanelMaxWidth } from '@/utils/designerPanelWidth'

describe('designerPanelWidth', () => {
  test('最大宽度为页面宽度的 80%', () => {
    expect(designerPanelMaxWidth(1600)).toBe(1280)
    expect(clampDesignerPanelWidth(1500, 1600)).toBe(1280)
    expect(clampDesignerPanelWidth(200, 1600)).toBe(320)
    expect(clampDesignerPanelWidth(900, 1600)).toBe(900)
  })
})
