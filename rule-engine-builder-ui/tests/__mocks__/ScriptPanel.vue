// ScriptPanel mock
export default {
  name: 'ScriptPanel',
  template: '<div class="script-panel" />',
  props: {
    value: { type: String, default: '' },
    readonly: { type: Boolean, default: false },
    height: { type: [String, Number], default: 300 }
  }
}