import ElementPlus from 'element-plus'
import AppIcon from '@/components/common/AppIcon.vue'

export default {
  install(app) {
    app.use(ElementPlus)
    app.component('AppIcon', AppIcon)
  },
}
