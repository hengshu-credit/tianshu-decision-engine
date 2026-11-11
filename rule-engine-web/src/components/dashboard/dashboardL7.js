export { Scene } from '@antv/l7-scene'
export { Map as BaseMap } from '@antv/l7-maps/simple'
export { HeatmapLayer, LineLayer, PolygonLayer } from '@antv/l7-layers'

let supported = false
export function supportsWebGL() {
  if (!supported) {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl2') || canvas.getContext('webgl')
    supported = Boolean(gl)
    gl?.getExtension('WEBGL_lose_context')?.loseContext()
  }
  return supported
}
