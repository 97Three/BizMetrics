import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/tokens.scss'
import './styles/element-override.scss'
import './styles/base.scss'

if (localStorage.getItem('bizmetrics-theme') === 'dark' || new URLSearchParams(location.search).get('theme') === 'dark') {
  document.documentElement.classList.add('dark')
}
createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
