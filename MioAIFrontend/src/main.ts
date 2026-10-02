import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import pinia from './store'
import { useUserStore } from './store/user'
import './styles/global.scss'

const app = createApp(App)

app.use(Antd)
app.use(router)
app.use(pinia)

// 挂载前向服务端校验本地 token（服务端重启会使 token 全部失效），
// 失效则降级游客，避免页面带失效 token 发请求导致各处 40100 报错
async function bootstrap(): Promise<void> {
  const userStore = useUserStore()
  if (userStore.token) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      // fetchUserInfo 内部已清理登录态
    }
  }
  app.mount('#app')
}

bootstrap()
