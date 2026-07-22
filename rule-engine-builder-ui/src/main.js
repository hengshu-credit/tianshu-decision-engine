import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementTabFocusGuard from '@/plugins/elementTabFocusGuard'
import './styles/index.scss'

// 覆盖 Element Plus 主题色为主色 #2639E9
import './styles/element-override.scss'
import './styles/compact-workbench.scss'

// Monaco Editor 通过 AMD loader 方式加载，Vite 在开发和生产阶段统一提供 vs/ 资源。
const base = import.meta.env.BASE_URL || './'
window.MonacoEnvironment = {
  getWorkerUrl: function () {
    // AMD 版本的 monaco-editor 使用统一 workerMain 入口，不能指向 ESM worker 文件。
    return base + 'vs/base/worker/workerMain.js'
  }
}

// 动态加载 monaco-editor 并挂载到 window.monaco，供组件使用
const loaderScript = document.createElement('script')
loaderScript.src = base + 'vs/loader.js'
loaderScript.onload = () => {
  window.require.config({ paths: { vs: base + 'vs' } })
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
app.use(store)
app.use(router)
app.use(ElementPlus, { size: 'small', locale: zhCn })
app.use(ElementTabFocusGuard)
app.mount('#app')
