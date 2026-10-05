import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      // 与生产 nginx 一致：转发时剥掉 /api 前缀（后端控制器映射不带 /api）
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        // SSE 长连接：转发层不做超时（后端 15s 心跳保活；连接生死交给前端看门狗判定）
        timeout: 0,
        proxyTimeout: 0
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
        silenceDeprecations: ['import']
      }
    }
  }
})