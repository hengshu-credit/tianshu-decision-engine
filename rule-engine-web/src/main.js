import { createApp, defineAsyncComponent } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { initializeSystemBrand } from '@/system/systemBrand'
import store from './store'
import ElementTabFocusGuard from '@/plugins/elementTabFocusGuard'
import permissionDirective from '@/directives/permission'
import { recordTabVisit } from '@/security/operationActivity'
import './styles/index.scss'

// 覆盖 Element Plus 主题色为主色 #2639E9
import './styles/element-override.scss'
import './styles/compact-workbench.scss'
import './styles/theme-tokens.scss'
import './styles/theme-components.scss'
import './styles/theme-buttons.scss'
import './styles/layout-menus.scss'
import './styles/management-tables.scss'
import './styles/scrollbars.scss'
import './styles/content-reference.scss'
import { bootstrapLocalTheme } from '@/theme/themeRuntime'

bootstrapLocalTheme(window.localStorage, document.documentElement)

// 全局注册 Monaco Editor 组件
import AppIcon from '@/components/common/AppIcon.vue'
const app = createApp(App)

app.component('MonacoEditor', defineAsyncComponent(() => import('@/components/MonacoEditor.vue')))
app.component('AppIcon', AppIcon)
app.directive('permission', permissionDirective)
document.addEventListener('click', event => recordTabVisit(event, router.currentRoute.value))
app.use(store)
app.use(router)
app.use(ElementPlus, { size: 'small', locale: zhCn })
app.use(ElementTabFocusGuard)
app.mount('#app')
app.onUnmount(initializeSystemBrand(router))
