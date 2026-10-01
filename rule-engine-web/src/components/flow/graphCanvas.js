import { markRaw } from 'vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/es/index.css'
import '@/styles/logicflow-extension.css'
import '@/styles/lineage-node.css'
import { registerCustomNodes } from './nodes'

const PRIMARY = 'var(--el-color-primary)'

// 决策树、决策流、血缘图共享网格、连线主题与节点注册；业务交互由调用方配置。
export function createGraphCanvas(options) {
  // @logicflow/core 在 CJS/ESM 互操作场景下可能把构造函数挂在 default.default；
  // 统一取出构造函数，避免生产构建中画布被误判为不可用。
  const LogicFlowConstructor = typeof LogicFlow === 'function'
    ? LogicFlow
    : LogicFlow && typeof LogicFlow.default === 'function'
    ? LogicFlow.default
    : LogicFlow && LogicFlow.default && typeof LogicFlow.default.default === 'function'
    ? LogicFlow.default.default
    : LogicFlow && LogicFlow.default && typeof LogicFlow.default.LogicFlow === 'function'
    ? LogicFlow.default.LogicFlow
    : LogicFlow && typeof LogicFlow.LogicFlow === 'function'
    ? LogicFlow.LogicFlow
    : null
  if (!LogicFlowConstructor) return null
  const edgeStyle = () => ({
    stroke: PRIMARY,
    hoverStroke: PRIMARY,
    selectedStroke: PRIMARY,
    strokeWidth: 1.5,
  })
  const defaults = {
    background: {
      backgroundColor: 'var(--tianshu-bg-workspace)',
      opacity: 1,
    },
    grid: {
      size: 20,
      visible: true,
      type: 'dot',
      config: { color: 'var(--tianshu-border-strong)', thickness: 1 },
    },
    snapGrid: true,
    style: {
      nodeText: { overflowMode: 'ellipsis', fontSize: 12 },
      edgeText: { fontSize: 12, background: { fill: 'var(--tianshu-bg-surface)' } },
      polyline: edgeStyle(),
      line: edgeStyle(),
      bezier: edgeStyle(),
      edgeAnimation: {
        stroke: PRIMARY,
        strokeDasharray: '12,4,6,4',
        strokeDashoffset: '100%',
        animationName: 'lf_animate_dash',
        animationDuration: '20s',
        animationIterationCount: 'infinite',
        animationTimingFunction: 'linear',
        animationDirection: 'normal',
      },
      arrow: { stroke: PRIMARY, fill: PRIMARY },
      anchor: { stroke: PRIMARY, fill: 'var(--tianshu-bg-surface)', r: 4 },
      anchorHover: { stroke: PRIMARY, fill: PRIMARY, r: 5 },
      anchorLine: { stroke: PRIMARY },
    },
  }
  const { style: customStyle = {}, ...customOptions } = options || {}
  const style = {
    ...defaults.style,
    ...customStyle,
    polyline: { ...defaults.style.polyline, ...(customStyle.polyline || {}) },
    line: { ...defaults.style.line, ...(customStyle.line || {}) },
    bezier: { ...defaults.style.bezier, ...(customStyle.bezier || {}) },
    arrow: { ...defaults.style.arrow, ...(customStyle.arrow || {}) },
  }
  const lf = markRaw(new LogicFlowConstructor({ ...defaults, ...customOptions, style }))
  registerCustomNodes(lf)
  return lf
}
