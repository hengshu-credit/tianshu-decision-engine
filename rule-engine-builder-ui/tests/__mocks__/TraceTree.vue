// TraceTree mock
export default {
  name: 'TraceTree',
  template: '<div class="trace-tree" />',
  props: {
    data: { type: Object, default: () => ({}) },
    traceNodes: { type: Array, default: () => [] }
  }
}