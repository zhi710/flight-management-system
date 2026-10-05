import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import i18n, { getElementPlusLocale } from './locales'
import { injectRouter } from './utils/request'
import { startDevAutoReload } from './utils/devAutoReload'
// 设计系统令牌：必须放在 element-plus 样式之后，才能覆盖 EP 的 :root 变量
import './assets/styles/tokens.css'
import './assets/styles/global.css'

const app = createApp(App)

// Register all Element Plus icons globally
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

injectRouter(router)
// 开发模式下监听后端重启并自动刷新页面（生产构建不生效）
startDevAutoReload()
app.use(createPinia())
app.use(router)
app.use(i18n)

// Element Plus 使用当前语言
app.use(ElementPlus, {
  locale: getElementPlusLocale(i18n.global.locale.value),
})

app.mount('#app')
