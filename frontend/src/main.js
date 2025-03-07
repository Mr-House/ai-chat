import { createApp } from 'vue'
import ArcoVue from '@arco-design/web-vue'
import '@arco-design/web-vue/dist/arco.css'
import './style.css'
import App from './App.vue'

// 导入图标组件
import { 
  IconMessage, 
  IconMenu, 
  IconPlus, 
  IconRobot,
  IconSend
} from '@arco-design/web-vue/es/icon'

const app = createApp(App)
app.use(ArcoVue)

// 注册图标组件
app.component('IconMessage', IconMessage)
app.component('IconMenu', IconMenu)
app.component('IconPlus', IconPlus)
app.component('IconRobot', IconRobot)
app.component('IconSend', IconSend)

app.mount('#app')
