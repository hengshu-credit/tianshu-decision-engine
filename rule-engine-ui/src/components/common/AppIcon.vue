<template>
  <el-icon><component :is="iconComponent" /></el-icon>
</template>

<script>
import * as ElementPlusIcons from '@element-plus/icons-vue'

export default {
  name: 'AppIcon',
  props: {
    name: {
      type: [String, Object, Function],
      default: '',
    },
  },
  computed: {
    iconComponent() {
      if (typeof this.name !== 'string') return this.name
      return ElementPlusIcons[this.name] || ElementPlusIcons.QuestionFilled
    },
  },
}
</script>
