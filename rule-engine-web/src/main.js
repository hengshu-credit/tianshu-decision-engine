import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementTabFocusGuard from '@/plugins/elementTabFocusGuard'
import permissionDirective from '@/directives/permission'
import './styles/index.scss'

// 覆盖 Element Plus 主题色为主色 #2639E9
import './styles/element-override.scss'
import './styles/compact-workbench.scss'
import './styles/theme-tokens.scss'
import './styles/theme-components.scss'
import './styles/management-tables.scss'
import './styles/scrollbars.scss'
import { bootstrapLocalTheme } from '@/theme/themeRuntime'

bootstrapLocalTheme(window.localStorage, document.documentElement)

// Monaco Editor 通过 AMD loader 方式加载，Vite 在开发和生产阶段统一提供 vs/ 资源。
// 统一解析成当前页面下的绝对同源地址，避免相对路径在详情路由、反向代理或静态子目录下被解析到错误位置。
const base = import.meta.env.BASE_URL || './'
const monacoBaseUrl = new URL(base, document.baseURI)
const monacoVsUrl = new URL('vs/', monacoBaseUrl)
const monacoWorkerUrl = new URL('base/worker/workerMain.js', monacoVsUrl).href
window.MonacoEnvironment = {
  // AMD 版本的 monaco-editor 使用统一 workerMain 入口，不能指向 ESM worker 文件。
  getWorkerUrl: function () {
    return monacoWorkerUrl
  },
  // 显式创建同源 Worker，绕过 AMD 默认工厂对相对 URL 和跨目录部署的二次解析。
  getWorker: function (_moduleId, label) {
    return new Worker(monacoWorkerUrl, { name: label || 'monaco' })
  },
}

// 动态加载 monaco-editor 并挂载到 window.monaco，供组件使用
const loaderScript = document.createElement('script')
loaderScript.src = new URL('loader.js', monacoVsUrl).href
loaderScript.onload = () => {
  window.require.config({ paths: { vs: monacoVsUrl.href.replace(/\/$/, '') } })
  window.require(['vs/editor/editor.main'], () => {
    console.log('[MonacoEditor] Monaco Editor loaded successfully')
  })
}
document.head.appendChild(loaderScript)

// 全局注册 Monaco Editor 组件
import MonacoEditor from '@/components/MonacoEditor.vue'
import AppIcon from '@/components/common/AppIcon.vue'
const app = createApp(App)

app.component('MonacoEditor', MonacoEditor)
app.component('AppIcon', AppIcon)
app.directive('permission', permissionDirective)
app.use(store)
app.use(router)
app.use(ElementPlus, { size: 'small', locale: zhCn })
app.use(ElementTabFocusGuard)
app.mount('#app')
