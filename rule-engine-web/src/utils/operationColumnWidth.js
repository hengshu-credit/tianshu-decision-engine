const CJK_CHARACTER_PATTERN = /[\u1100-\u11ff\u2e80-\u9fff\uac00-\ud7ff]/

const BUTTON_HORIZONTAL_PADDING = 12
const BUTTON_GAP = 8
const CELL_HORIZONTAL_PADDING = 24
const SAFETY_SPACE = 24

/**
 * 根据操作按钮文案计算固定操作列的最小安全宽度。
 * labels 应包含当前权限/条件下可能出现的全部按钮文案。
 */
export function operationColumnWidth(labels, options = {}) {
  const source = Array.isArray(labels) ? labels.filter(Boolean) : []
  const contentWidth = source.reduce((total, label) => {
    const textWidth = Array.from(String(label)).reduce(
      (width, character) => width + (CJK_CHARACTER_PATTERN.test(character) ? 14 : 7),
      0
    )
    return total + textWidth + BUTTON_HORIZONTAL_PADDING
  }, 0)
  const gapWidth = Math.max(0, source.length - 1) * BUTTON_GAP
  const calculated = Math.ceil(
    contentWidth + gapWidth + CELL_HORIZONTAL_PADDING + SAFETY_SPACE
  )
  const minWidth = Number.isFinite(options.minWidth) ? options.minWidth : 0
  return Math.max(minWidth, calculated)
}
