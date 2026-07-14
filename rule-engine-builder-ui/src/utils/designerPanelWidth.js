const MIN_PANEL_WIDTH = 320

export function designerPanelMaxWidth(viewportWidth) {
  const width = Number(viewportWidth) || 0
  return Math.max(MIN_PANEL_WIDTH, Math.floor(width * 0.8))
}

export function clampDesignerPanelWidth(width, viewportWidth) {
  const value = Number(width) || MIN_PANEL_WIDTH
  return Math.min(Math.max(value, MIN_PANEL_WIDTH), designerPanelMaxWidth(viewportWidth))
}
