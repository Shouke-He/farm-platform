// 管理端前端入口：整个应用从这里启动（index.html 里的 <div id="app"> 就是它的挂载点）。
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
// 组件库统一用中文语言包（分页、弹窗按钮等文案随系统显示中文）
app.use(ElementPlus, { locale: zhCn })
// 把 Element Plus 的全部图标注册成全局组件，模板里可直接写 <Sunny /> <Bell /> 等
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.mount('#app')
