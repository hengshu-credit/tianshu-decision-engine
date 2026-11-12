import { createApp, defineAsyncComponent } from 'vue'
import { provideGlobalConfig } from 'element-plus'
import { PUBLIC_AUTH_PATHS } from '@/router/authGuard'
import { loadWorkspaceUi } from '@/router/navigationState'
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
const app = createApp(App)

app.component('MonacoEditor', defineAsyncComponent(() => import('@/components/MonacoEditor.vue')))
app.directive('permission', permissionDirective)
document.addEventListener('click', event => recordTabVisit(event, router.currentRoute.value))
app.use(store)
provideGlobalConfig({ size: 'small', locale: zhCn }, app, true)
let workspaceUi
router.beforeEach(to => {
  if (PUBLIC_AUTH_PATHS.has(to.path)) return
  if (!workspaceUi) {
    // 此守卫位于身份校验之后，与首个工作台页面并行下载完整组件库。
    to.matched.forEach(record => record.components?.default?.preload?.())
    workspaceUi = loadWorkspaceUi().then(({ default: plugin }) => {
      app.use(plugin)
    }).catch(error => { workspaceUi = null; throw error })
  }
  return workspaceUi
})
app.use(router)
app.use(ElementTabFocusGuard)
app.mount('#app')
// 先绘制可操作的首屏，再启用完整品牌字体。慢网下载期间沿用系统字体。
router.isReady().then(() => {
  requestAnimationFrame(() => requestAnimationFrame(() => {
    const font = document.getElementById('engine-default-font')
    if (font) font.media = 'all'
  }))
}).catch(() => { /* 初次导航失败时仍可使用系统字体显示错误。 */ })
app.onUnmount(initializeSystemBrand(router))
