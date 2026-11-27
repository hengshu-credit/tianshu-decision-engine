import ElementPlus from 'element-plus'
import WorkspaceDialog from '@/components/common/WorkspaceDialog'
import AppIcon from '@/components/common/AppIcon.vue'

export default {
  install(app) {
    app.use(ElementPlus)
    app.component('el-dialog', WorkspaceDialog)
    app.component('AppIcon', AppIcon)
  },
}
