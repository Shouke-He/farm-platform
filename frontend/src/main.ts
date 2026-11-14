// 前端启动入口：Vite 打包时以 index.html 里的 <div id="app"> 为挂载点，
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'

// 创建 Vue 应用实例，根组件是 App.vue
const app = createApp(App)
app.use(createPinia())
app.use(router)
// locale: zhCn 表示 Element Plus 组件内部文案用简体中文
app.use(ElementPlus, { locale: zhCn })
// 遍历所有图标组件，逐个注册为全局组件（这样模板里直接写图标名即可，无需 import）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// 挂载到 index.html 里 id="app" 的节点上，页面开始渲染
app.mount('#app')
