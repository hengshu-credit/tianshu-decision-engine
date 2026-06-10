import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import App from './App.vue'
import router from './router'
import store from './store'
import './styles/index.scss'

// 覆盖 Element UI 主题色为主色 #2639E9
import './styles/element-override.scss'

// Monaco Editor 通过 AMD loader 方式加载（vs/ 目录由 copy-webpack-plugin 复制到输出目录）
const base = process.env.NODE_ENV === 'production' ? './' : '/'
window.MonacoEnvironment = {
  getWorkerUrl: function (moduleId, label) {
    if (label === 'json') {
      return base + 'vs/language/json/json.worker.js'
    }
    if (label === 'javascript' || label === 'typescript') {
      return base + 'vs/language/typescript/ts.worker.js'
    }
    if (label === 'python') {
      return base + 'vs/language/python/python.worker.js'
    }
    if (label === 'java') {
      return base + 'vs/language/java/java.worker.js'
    }
    if (label === 'yaml') {
      return base + 'vs/language/yaml/yaml.worker.js'
    }
    return base + 'vs/editor/editor.worker.js'
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
Vue.component('MonacoEditor', MonacoEditor)

Vue.use(ElementUI, { size: 'small' })
Vue.config.productionTip = false

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')