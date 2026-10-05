import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { startDevAutoReload } from './utils/devAutoReload'
// 命令式组件（ElMessage/ElMessageBox/ElNotification/v-loading）不在模板中，组件按需自动引入样式不会包含它们。
// 若不引入，弹出提示无任何样式（页面看似正常、toast 却不可见）。这里手动补齐其样式。
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-notification.css'
import 'element-plus/theme-chalk/el-loading.css'
// 设计系统令牌（后台为紧凑密度：index.html 的 <html data-density="compact">）
import './assets/styles/tokens.css'
import './assets/styles/global.scss'

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 开发模式下监听后端重启并自动刷新页面（生产构建不生效）
startDevAutoReload()

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
