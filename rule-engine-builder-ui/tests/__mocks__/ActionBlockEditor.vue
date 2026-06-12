// ActionBlockEditor mock
export default {
  name: 'ActionBlockEditor',
  template: '<div class="action-block-editor" />',
  props: {
    value: { type: Object, default: () => ({}) },
    readonly: { type: Boolean, default: false }
  }
}