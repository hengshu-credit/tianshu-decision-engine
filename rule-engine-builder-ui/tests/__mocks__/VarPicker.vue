// VarPicker mock
export default {
  name: 'VarPicker',
  template: '<div class="var-picker" />',
  props: {
    vars: { type: Array, default: () => [] },
    value: { type: [String, Number, Object], default: null },
    disabled: { type: Boolean, default: false },
    placeholder: { type: String, default: '' },
    size: { type: String, default: 'small' }
  }
}