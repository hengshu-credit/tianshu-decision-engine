import { defineComponent, h, mergeProps, normalizeClass } from 'vue'
import { ElDialog } from 'element-plus'
import DialogResizeHandle from './DialogResizeHandle.vue'

// 保留 Element Plus 的焦点管理、关闭守卫、Teleport 和自定义插槽。
export default defineComponent({
  name: 'WorkspaceDialog',
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h(ElDialog, mergeProps(attrs, { class: 'workspace-dialog' }), {
      ...slots,
      default: () => [
        slots.default?.(),
        !attrs.fullscreen && !normalizeClass(attrs.class).split(' ').includes('resizable-config-dialog')
          ? h(DialogResizeHandle, { visible: !!attrs.modelValue, minWidth: 280, minHeight: 180 })
          : null,
      ],
    })
  },
})
